package app.jrny.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.tab.TabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Active Profile with its Tabs, read together so they always belong to each other. */
data class ActiveProfileTabs(val profile: Profile, val tabs: List<SavedTab>) {
    /** The Tab the window shows. */
    val selected: SavedTab get() = tabs.first { it.selected }
}

/**
 * What the browser window shows: the Profiles, the Active Profile and its Tabs.
 *
 * Every Tab is saved in the database, so Tabs come back after Android kills the app. Only a few
 * are live, with a WebView: the shown Tab and the [MAX_LIVE_TABS] - 1 most recently shown, across
 * all Profiles (SPEC, "Tabs"). The others are unloaded and load again when shown.
 *
 * Create and use it on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserState(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val tabRepository: TabRepository,
    private val scope: CoroutineScope,
) {
    val profiles: StateFlow<List<Profile>> = profileRepository.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val activeProfile: StateFlow<Profile?> = profileRepository.activeProfile.stateIn(scope, SharingStarted.Eagerly, null)
    val pendingDeletionCount: StateFlow<Int> = profileRepository.pendingDeletionCount.stateIn(scope, SharingStarted.Eagerly, 0)

    /** The Active Profile and its Tabs. A Profile without Tabs gets an empty one first. */
    val active: StateFlow<ActiveProfileTabs?> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { profile ->
            tabRepository.tabs(profile.id)
                .onEach { if (it.isEmpty()) tabRepository.ensureOne(profile.id) }
                .filter { it.isNotEmpty() }
                .map { ActiveProfileTabs(profile, it) }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Live Tabs, least recently shown first. */
    private val live = LinkedHashMap<TabId, LiveTab>(MAX_LIVE_TABS + 1, 0.75f, true)

    private class LiveTab(val tab: Tab, val saving: Job)

    /** The Tab on screen. Its WebView can't be destroyed until another Tab replaces it. */
    private var shownId: TabId? = null

    /** Closed Tabs still on screen, destroyed once another Tab is shown. */
    private val retired = mutableSetOf<TabId>()

    /**
     * The Tab an Incoming Link just opened, which offers "Move to Profile…" (SPEC, "Incoming
     * Links"), until it's dismissed, moved, or the person goes on to another Tab.
     */
    var incomingLinkTab: TabId? by mutableStateOf(null)
        private set

    /**
     * True once [incomingLinkTab] has been on screen. Only then does showing another Tab end the
     * offer: the Tab shown before the link can still report itself shown a moment after the link
     * arrived, most of all when the link is what started the app.
     */
    private var incomingLinkSeen = false

    /** How many Tabs have a WebView now. */
    val liveTabCount: Int get() = live.size

    fun isLive(id: TabId): Boolean = live.containsKey(id)

    /**
     * The live Tab for [saved], loading it with its saved address and scroll position if it was
     * unloaded. Call [shown] once it's on screen.
     */
    fun tabFor(saved: SavedTab): Tab {
        live[saved.id]?.let { return it.tab }
        // Keep the saved address and title current, so a kill loses at most the scroll position.
        // Saved one at a time, latest first: a burst of changes during a load writes only the last.
        val page = MutableStateFlow(saved.url to saved.title)
        val tab = Tab(context, WebProfile.of(saved.profileId)) { page.value = it.url to it.title }
        val saving = scope.launch {
            page.drop(1).collect { (url, title) -> tabRepository.savePage(saved.id, url, title) }
        }
        saved.url?.let { tab.restore(it, saved.title, saved.scrollY) }
        live[saved.id] = LiveTab(tab, saving)
        return tab
    }

    /** [id] is on screen: it becomes the most recently shown, and Tabs past the limit unload. */
    fun shown(id: TabId) {
        live[id] ?: return
        shownId = id
        if (id == incomingLinkTab) {
            incomingLinkSeen = true
        } else if (incomingLinkSeen) {
            dismissIncomingLink()
        }
        retired.filter { it != id }.forEach { destroy(it); retired.remove(it) }
        while (live.size > MAX_LIVE_TABS) {
            val (oldest, _) = live.entries.first()
            unload(oldest)
        }
    }

    fun switchTo(profile: Profile) {
        scope.launch { profileRepository.activate(profile.id) }
    }

    fun create(name: String, color: ProfileColor, icon: ProfileIcon) {
        scope.launch { profileRepository.create(name, color, icon) }
    }

    fun update(profile: Profile) {
        scope.launch { profileRepository.update(profile) }
    }

    /**
     * Deletes [profile]: it disappears, its Tabs close, and its web data is wiped at the next cold
     * start. Nothing happens for the last Profile.
     */
    fun delete(profile: Profile) {
        scope.launch {
            if (profileRepository.delete(profile.id)) {
                val store = WebProfile.of(profile.id).name
                live.entries.filter { it.value.tab.profile.name == store }.map { it.key }.forEach(::retire)
            }
        }
    }

    /** Opens an empty Tab in the Active Profile and shows it. */
    fun openTab(profile: Profile) {
        scope.launch { tabRepository.open(profile.id) }
    }

    fun selectTab(id: TabId) {
        scope.launch { tabRepository.select(id) }
    }

    fun closeTab(id: TabId) {
        retire(id)
        scope.launch { tabRepository.close(id) }
    }

    /**
     * Move: closes [tab] and opens its current address as a new Tab in [profile], which becomes
     * the Active Profile. Only the address goes along (ADR 0003). Nothing happens for a Tab
     * without an address.
     */
    fun moveTab(tab: SavedTab, profile: Profile) {
        val url = currentUrl(tab) ?: return
        scope.launch {
            if (tabRepository.move(tab.id, profile.id, url) != null) {
                if (incomingLinkTab == tab.id) dismissIncomingLink()
                retire(tab.id)
            }
        }
    }

    /** Open in Profile: opens [tab]'s current address as a new Tab in [profile] and shows it. [tab] stays. */
    fun openInProfile(tab: SavedTab, profile: Profile) {
        val url = currentUrl(tab) ?: return
        scope.launch { tabRepository.openIn(profile.id, url) }
    }

    /**
     * Opens an Incoming Link, an address another app handed over, as a new Tab in the Active
     * Profile, and offers to move it. The caller checks that [url] is a web address.
     */
    fun openIncomingLink(url: String) {
        scope.launch {
            // The Active Profile can be deleted between reading it and opening the Tab. Then
            // nothing is opened there, and the Profile that is active now takes the link.
            repeat(INCOMING_LINK_ATTEMPTS) {
                val profile = profileRepository.activeProfile.filterNotNull().first()
                val opened = tabRepository.openUrl(profile.id, url)
                if (opened != null) {
                    incomingLinkSeen = false
                    incomingLinkTab = opened
                    return@launch
                }
            }
        }
    }

    fun dismissIncomingLink() {
        incomingLinkTab = null
        incomingLinkSeen = false
    }

    /** The address [tab] shows now: the live page's, which can be newer than the saved one. */
    private fun currentUrl(tab: SavedTab): String? = live[tab.id]?.tab?.url ?: tab.url

    /** Saves the live Tabs' scroll positions and writes every open Profile's cookies to disk. */
    fun flush() {
        live.forEach { (id, it) -> saveScroll(id, it.tab) }
        live.values.map { it.tab.profile }.distinct().forEach { it.flush() }
    }

    fun destroy() {
        live.keys.toList().forEach(::destroy)
    }

    /** Destroys a closed Tab, or, if it's on screen, once another Tab replaces it. */
    private fun retire(id: TabId) {
        if (id == shownId) retired += id else destroy(id)
    }

    /** Frees [id]'s WebView, keeping it saved so it loads again when shown. */
    private fun unload(id: TabId) {
        val entry = live[id] ?: return
        saveScroll(id, entry.tab)
        destroy(id)
    }

    private fun destroy(id: TabId) {
        val entry = live.remove(id) ?: return
        entry.saving.cancel()
        entry.tab.destroy()
    }

    private fun saveScroll(id: TabId, tab: Tab) {
        val scrollY = tab.scrollY
        scope.launch { tabRepository.saveScroll(id, scrollY) }
    }

    companion object {
        private const val INCOMING_LINK_ATTEMPTS = 3

        /** At most this many Tabs are live at once, across all Profiles (SPEC, "Tabs"). */
        const val MAX_LIVE_TABS = 5
    }
}
