package app.jrny.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.jrny.browser.data.RecordedVisit
import app.jrny.browser.download.Download
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DataAddress
import app.jrny.browser.download.DownloadStatus
import app.jrny.browser.download.FetchedImage
import app.jrny.browser.download.ImageFetch
import app.jrny.browser.download.PageFiles
import app.jrny.browser.handoff.AppLink
import app.jrny.browser.handoff.HandoffRules
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Folder
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.library.ProfilePages
import app.jrny.browser.library.Visit
import app.jrny.browser.address.AddressInput
import app.jrny.browser.address.WebAddress
import app.jrny.browser.profile.Profile
import app.jrny.browser.settings.Settings
import app.jrny.browser.settings.ThemeChoice
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.settings.Suggestion
import app.jrny.browser.profile.ProfileLook
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.rules.SitePattern
import app.jrny.browser.rules.SiteRule
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import app.jrny.browser.site.SiteSettingsEntry
import app.jrny.browser.tab.PermissionAsk
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.PrivateSession
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The Active Profile with its Tabs, read together so they always belong to each other. */
data class ActiveProfileTabs(val profile: Profile, val tabs: List<SavedTab>) {
    /** The Tab the window shows. */
    val selected: SavedTab get() = tabs.first { it.selected }
}

/** What the window offers for a Tab that an Incoming Link opened (SPEC, "Incoming Links"). */
sealed interface LinkOffer {
    val tab: TabId

    /**
     * The link opened in this Tab: in the Profile [rule] names, or without one in the Active
     * Profile. The window says so and offers "Move to Profile…".
     */
    data class Opened(override val tab: TabId, val rule: SiteRule? = null) : LinkOffer

    /**
     * The person just moved the link to [profile]. The window offers a Site Rule, so links that
     * [pattern] covers open there from now on. When a rule sent this link elsewhere, that is
     * [rule], and the offer is to change it: [pattern] is its own. Without one, [pattern] is the
     * link's site, for a new rule.
     */
    data class Always(override val tab: TabId, val pattern: SitePattern, val profile: Profile, val rule: SiteRule? = null) : LinkOffer

    /** That rule was just made. The window says so, with a way to the Site Rules. */
    data class RuleSaved(override val tab: TabId, val pattern: SitePattern, val profile: Profile) : LinkOffer
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
    services: BrowserServices,
    private val scope: CoroutineScope,
) {
    private val repositories = services.repositories
    private val downloader = services.downloader
    private val privateTabs = services.privateTabs
    private val settingsRepository = services.settings
    private val searchSuggestions = services.searchSuggestions
    private val profileRepository = repositories.profiles
    private val tabRepository = repositories.tabs
    private val history = repositories.history
    private val bookmarkRepository = repositories.bookmarks
    private val downloadRepository = repositories.downloads
    private val siteRuleRepository = repositories.siteRules
    private val siteSettingsRepository = repositories.siteSettings

    /** The icons of the sites each Profile has shown, for the Tab list. */
    val siteIcons = services.siteIcons

    val profiles: StateFlow<List<Profile>> = profileRepository.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val activeProfile: StateFlow<Profile?> = profileRepository.activeProfile.stateIn(scope, SharingStarted.Eagerly, null)
    val pendingDeletionCount: StateFlow<Int> = profileRepository.pendingDeletionCount.stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * The Active Profile and its Tabs: its saved Tabs, then its Private Tabs. A Profile without
     * Tabs gets an empty one first. While one of its Private Tabs is selected, that is the Tab
     * the Profile shows, and none of the saved ones is.
     */
    val active: StateFlow<ActiveProfileTabs?> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { profile ->
            val saved = tabRepository.tabs(profile.id)
                .onEach { if (it.isEmpty()) tabRepository.ensureOne(profile.id) }
                .filter { it.isNotEmpty() }
            combine(saved, privateTabs.session(profile.id)) { normal, session ->
                val private = session?.tabs.orEmpty()
                val privateShown = private.any { it.selected }
                ActiveProfileTabs(profile, normal.map { if (privateShown) it.copy(selected = false) else it } + private)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The Active Profile's history, newest first. */
    val visits: StateFlow<List<Visit>> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { history.visits(it.id) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** All of the Active Profile's bookmarks, in their order. */
    val bookmarks: StateFlow<List<Bookmark>> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { bookmarkRepository.bookmarks(it.id) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * The Active Profile's bookmarks and Folders as the Sidebar lists them under "Pinned" (SPEC,
     * "Pinned Tabs"): its top level in order, each Folder with its bookmarks.
     */
    val pinned: StateFlow<List<PinnedItem>> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { bookmarkRepository.pinned(it.id) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * The Active Profile's Pinned Tabs and history for the Start Page, tagged with the Profile
     * they belong to and loaded together. Null until the first Profile's have loaded; after a
     * switch it still holds the previous Profile's until the new ones arrive.
     */
    val startPagePages: StateFlow<ProfilePages?> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { profile ->
            combine(bookmarkRepository.pinned(profile.id), history.visits(profile.id)) { pinned, visits ->
                ProfilePages(profile.id, pinned, visits)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The Active Profile's downloads, newest first. */
    val downloads: StateFlow<List<Download>> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { downloadRepository.downloads(it.id) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The Site Rules, oldest first. They belong to the app, not to one Profile. */
    val siteRules: StateFlow<List<SiteRule>> = siteRuleRepository.rules.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The Active Profile's Site settings: its sites with a permission or a Desktop site choice. */
    val siteSettings: StateFlow<List<SiteSettingsEntry>> = profileRepository.activeProfile
        .filterNotNull()
        .flatMapLatest { siteSettingsRepository.sites(it.id) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The app's settings, shared by all Profiles. */
    val settings: StateFlow<Settings> = settingsRepository.settings

    fun updateSettings(change: (Settings) -> Settings) = settingsRepository.update(change)

    /** Live Tabs, least recently shown first. */
    private val live = LinkedHashMap<TabId, LiveTab>(MAX_LIVE_TABS + 1, 0.75f, true)

    private class LiveTab(val tab: Tab, val saving: Job?, val saved: SavedTab)

    /** Every Profile's desktop sites, kept here so a Tab can ask before each page loads. */
    private var desktopSites: Map<ProfileId, Set<SiteOrigin>> = emptyMap()

    /** True while the device is in dark mode; the app's theme "the device's" follows it. */
    private var deviceDark: Boolean = isDeviceDark()

    private fun isDeviceDark(): Boolean =
        context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Whether pages are shown dark now: "Darken pages" is on and the app's theme is dark (SPEC, "Dark pages"). */
    private fun pagesDark(current: Settings = settings.value): Boolean =
        current.darkenPages && when (current.theme) {
            ThemeChoice.Dark -> true
            ThemeChoice.Light -> false
            ThemeChoice.System -> deviceDark
        }

    /** The device switched between light and dark: with the app's theme following it, so do dark pages. */
    fun deviceThemeChanged() {
        deviceDark = isDeviceDark()
        val dark = pagesDark()
        live.values.forEach { it.tab.darkPages = dark }
    }

    init {
        // Turning Safe Browsing on or off reaches the pages that are already open.
        scope.launch {
            settings.map { it.safeBrowsing }.distinctUntilChanged().drop(1).collect { on ->
                live.values.forEach { it.tab.safeBrowsing = on }
            }
        }
        // So do text size and dark pages.
        scope.launch {
            settings.map { it.textZoom }.distinctUntilChanged().drop(1).collect { zoom ->
                live.values.forEach { it.tab.textZoom = zoom }
            }
        }
        scope.launch {
            settings.map { pagesDark(it) }.distinctUntilChanged().drop(1).collect { dark ->
                live.values.forEach { it.tab.darkPages = dark }
            }
        }
        scope.launch { siteSettingsRepository.desktopSites().collect { desktopSites = it } }
    }

    /**
     * Desktop site on or off for the site [tab] shows (SPEC, "Desktop site"). A Profile remembers
     * it for the site; a Private Tab only until it closes. The page loads again.
     */
    fun setDesktopSite(tab: SavedTab, live: Tab, on: Boolean) {
        val origin = SiteOrigin.of(live.url) ?: return
        live.setDesktopSite(origin, on)
        if (tab.isPrivate) return
        scope.launch {
            if (on) siteSettingsRepository.set(tab.profileId, origin, SiteSetting.DesktopSite, true) else siteSettingsRepository.reset(tab.profileId, origin, SiteSetting.DesktopSite)
        }
    }

    /** Private stores whose session has ended, emptied once their last WebView is gone. */
    private val endedStores = mutableSetOf<String>()


    /** The Tab on screen. Its WebView can't be destroyed until another Tab replaces it. */
    private var shownId: TabId? = null

    /** Closed Tabs still on screen, destroyed once another Tab is shown. */
    private val retired = mutableSetOf<TabId>()

    /**
     * What the window offers for the Tab an Incoming Link just opened (SPEC, "Incoming Links"),
     * until it's dismissed or taken up, or the person goes on to another Tab.
     */
    var linkOffer: LinkOffer? by mutableStateOf(null)
        private set

    /** The Tab an Incoming Link just opened, while it offers "Move to Profile…". */
    val incomingLinkTab: TabId? get() = (linkOffer as? LinkOffer.Opened)?.tab

    /**
     * True once the [linkOffer]'s Tab has been on screen. Only then does showing another Tab end
     * the offer: the Tab shown before the link can still report itself shown a moment after the
     * link arrived, most of all when the link is what started the app.
     */
    private var linkOfferSeen = false

    private fun offer(offer: LinkOffer) {
        // Its Tab can be on screen already, when the window was quicker than this.
        linkOfferSeen = offer.tab == shownId
        linkOffer = offer
    }

    /** How many Tabs have a WebView now. */
    val liveTabCount: Int get() = live.size

    fun isLive(id: TabId): Boolean = live.containsKey(id)

    /**
     * The live Tab for [saved], loading it with its saved address and scroll position if it was
     * unloaded. Call [shown] once it's on screen.
     */
    fun tabFor(saved: SavedTab): Tab {
        live[saved.id]?.let { return it.tab }
        if (saved.isPrivate) return privateTabFor(saved)
        // Keep the saved address and title current, so a kill loses at most the scroll position.
        // Saved one at a time, latest first: a burst of changes during a load writes only the last.
        val page = MutableStateFlow(saved.url to saved.title)
        // The Tab's newest visit until the Tab reports its page as seen: it got a title, or it
        // finished loading. If the next visit arrives first, it was only a redirect's stop on the
        // way, and leaves the history again.
        var unconfirmed: Pair<RecordedVisit, String>? = null
        // A visit is written before its title, and before the visit after it: one at a time, in
        // the order the Tab reported them. Without this, a title could arrive before its row.
        val historyOrder = Mutex()
        val tab = Tab(
            context,
            WebProfile.of(saved.profileId),
            safeBrowsing = settings.value.safeBrowsing,
            onPageChanged = { page.value = it.url to it.title },
            onVisited = { url ->
                scope.launch {
                    historyOrder.withLock {
                        unconfirmed?.let { (recorded, _) -> history.takeBack(recorded) }
                        unconfirmed = history.record(saved.profileId, url)?.let { it to url }
                    }
                }
            },
            onPageSeen = { url, title ->
                scope.launch {
                    historyOrder.withLock {
                        history.setTitle(saved.profileId, url, title)
                        if (unconfirmed?.second == url) unconfirmed = null
                    }
                }
            },
            // Kept with the Profile the page was shown in. A Private Tab's are never kept.
            onIcon = { url, icon -> scope.launch { siteIcons.save(saved.profileId, url, icon) } },
            onPermissionAsk = { tab, ask -> answerPermissionAsk(saved, tab, ask) },
            onHandoff = { tab, link, tapped -> handOff(saved, tab, link, tapped) },
            desktopFor = { origin -> origin in desktopSites[saved.profileId].orEmpty() },
            textZoom = settings.value.textZoom,
            darkPages = pagesDark(),
        )
        val saving = scope.launch {
            page.drop(1).collect { (url, title) -> tabRepository.savePage(saved.id, url, title) }
        }
        saved.url?.let { tab.restore(it, saved.title, saved.scrollY) }
        live[saved.id] = LiveTab(tab, saving, saved)
        return tab
    }

    /**
     * A Private Tab's page lives in its session's temporary store, apart from the Profile's own.
     * Its address and title are kept only in memory, and nothing it visits goes into the history.
     */
    private fun privateTabFor(saved: SavedTab): Tab {
        val session = checkNotNull(privateTabs.current(saved.profileId)) { "No private session" }
        val tab = Tab(
            context,
            WebProfile.privateOf(saved.profileId, session.session),
            safeBrowsing = settings.value.safeBrowsing,
            onPageChanged = { privateTabs.savePage(saved.profileId, saved.id, it.url, it.title) },
            onPermissionAsk = { tab, ask -> answerPermissionAsk(saved, tab, ask) },
            onHandoff = { tab, link, tapped -> handOff(saved, tab, link, tapped) },
            // A Private Tab starts with its Profile's desktop sites, and keeps its own choices to itself.
            desktopFor = { origin -> origin in desktopSites[saved.profileId].orEmpty() },
            textZoom = settings.value.textZoom,
            darkPages = pagesDark(),
        )
        saved.url?.let { tab.restore(it, saved.title, saved.scrollY) }
        live[saved.id] = LiveTab(tab, saving = null, saved)
        return tab
    }

    /**
     * Answers a site's ask for the camera, the microphone or location from what [saved]'s Profile
     * remembers, or shows it for the person to answer (SPEC, "Site permissions and Site
     * settings"). A Tab that isn't on screen is refused, and so is a site the Profile blocked. A
     * Private Tab remembers nothing, so only a grant for once counts there.
     */
    private fun answerPermissionAsk(saved: SavedTab, tab: Tab, ask: PermissionAsk) {
        if (saved.id != shownId) {
            ask.deny()
            return tab.closePermissionAsk()
        }
        fun allowed(kept: Map<SiteSetting, Boolean?>) = ask.wanted.all { tab.isAllowedOnce(ask.origin, it) || kept[it] == true }
        if (saved.isPrivate) return tab.showPermissionAsk(if (allowed(emptyMap())) ask.asDecided() else ask)
        scope.launch {
            val kept = ask.wanted.associateWith { siteSettingsRepository.get(saved.profileId, ask.origin, it) }
            when {
                // The person may have shown another Tab while the Profile's answer was read.
                saved.id != shownId || kept.values.any { it == false } -> {
                    ask.deny()
                    tab.closePermissionAsk()
                }
                allowed(kept) -> tab.showPermissionAsk(ask.asDecided())
                else -> tab.showPermissionAsk(ask)
            }
        }
    }

    /**
     * Hands a link the page opened to its app, or offers it (SPEC, "Links to other apps"). A
     * tapped phone, mail, message or map link in a normal Tab on screen goes at once; anything
     * else waits for "Open". An `intent:` link for a web page loads in the Tab, and one no app can
     * open loads its fallback page when it names one.
     */
    private fun handOff(saved: SavedTab, tab: Tab, link: AppLink, tapped: Boolean) {
        // Loaded after WebView has finished asking about this navigation. Not through the
        // WebView's own queue, which waits until it is on screen.
        fun loadInstead(address: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { tab.load(address) }
        }
        link.webAddress?.let { return loadInstead(it) }
        if (link.fallback != null && !link.canOpen(context)) return loadInstead(link.fallback)
        val onScreen = saved.id == shownId
        if (onScreen && HandoffRules.goesAtOnce(link.scheme, tapped, saved.isPrivate) && link.open(context)) return
        tab.offerHandoff(link)
    }

    /** The Tab an "Open in new Tab" just opened behind the shown one, for the window to offer "Show", or null. */
    var openedBehind: SavedTab? by mutableStateOf(null)
        private set

    fun dismissOpenedBehind() {
        openedBehind = null
    }

    /**
     * Opens [url] as a new Tab in [from]'s Profile, at the end of its list, and keeps [from]
     * shown (SPEC, "Link and image menu"). From a Private Tab it is a Private Tab.
     */
    fun openInNewTab(from: SavedTab, url: String) {
        if (from.isPrivate) {
            val id = privateTabs.open(from.profileId, url, select = false)
            openedBehind = privateTabs.find(id)
            return
        }
        scope.launch {
            val id = tabRepository.openBehind(from.profileId, url) ?: return@launch
            openedBehind = SavedTab(id, from.profileId, url, "", 0, selected = false)
        }
    }

    /** Opens [url] in a new Private Tab of [profile] and shows it. */
    fun openInPrivateTab(profile: Profile, url: String) {
        tabChoices++
        privateTabs.open(profile.id, url)
    }

    /** Opens [url] as a new Tab in [profile], which becomes the Active Profile: Open in Profile for a link. */
    fun openLinkInProfile(profile: Profile, url: String) {
        scope.launch {
            if (tabRepository.openIn(profile.id, url) != null) {
                privateTabs.select(profile.id, null)
                history.record(profile.id, url)
            }
        }
    }

    /**
     * The image at [url] that the person held in [tab], for "Copy image" and "Share image": fetched
     * with the cookies of the Tab's own store, decoded from a `data:` address, or read out of the
     * page for a `blob:` one. Null when it can't be had.
     */
    suspend fun fetchImage(tab: SavedTab, live: Tab, url: String): FetchedImage? {
        if (url.startsWith("data:", ignoreCase = true)) {
            return DataAddress.decode(url)?.takeIf { it.mimeType.startsWith("image/") }?.let { FetchedImage(it.bytes, it.mimeType) }
        }
        if (url.startsWith("blob:", ignoreCase = true)) {
            val name = PageFiles.newCopyName()
            live.evaluate(PageFiles.keep(url, name))
            return PageFiles.read(name, live::evaluate)?.let { FetchedImage(it, "image/png") }
        }
        if (!WebAddress.isWebAddress(url)) return null
        val cookies = cookieManagerFor(tab) ?: return null
        val userAgent = live.webView.settings.userAgentString.orEmpty()
        val referrer = live.url?.takeIf(WebAddress::isWebAddress)?.substringBefore('#')
        return withContext(Dispatchers.IO) { ImageFetch.fetch(url, userAgent, referrer, cookies::getCookie) }
    }

    /** The cookies of [tab]'s own store: its Profile's, or its private session's. */
    private fun cookieManagerFor(tab: SavedTab) = if (tab.isPrivate) {
        privateTabs.current(tab.profileId)?.let { WebProfile.privateOf(tab.profileId, it.session).cookieManager }
    } else {
        WebProfile.of(tab.profileId).cookieManager
    }

    /** What happened to a file a page made, once Okapi tried to save it: its name, and whether it was saved. */
    var pageFileSaved: Pair<String, Boolean>? by mutableStateOf(null)
        private set

    fun dismissPageFileSaved() {
        pageFileSaved = null
    }

    /**
     * Saves a file the page in [live] made itself, which the person agreed to keep (SPEC,
     * "Browsing features"): a `data:` address is decoded, a `blob:` one read out of the page's
     * copy. It goes to the Downloads folder, and on the Profile's list unless [tab] is private.
     */
    fun savePageFile(tab: SavedTab, live: Tab, request: DownloadRequest) {
        val page = live.url?.takeIf(WebAddress::isWebAddress)?.substringBefore('#')
        scope.launch {
            val data = if (request.url.startsWith("data:", ignoreCase = true)) {
                DataAddress.decode(request.url)?.bytes
            } else {
                request.pageCopy?.let { PageFiles.read(it, live::evaluate) }
            }
            val mimeType = request.mimeType.ifBlank { DataAddress.decode(request.url)?.mimeType.orEmpty() }
            val file = data?.let { withContext(Dispatchers.IO) { downloader.save(request.fileName, mimeType, it) } }
            if (file != null && !tab.isPrivate) downloadRepository.addSaved(tab.profileId, file, request.copy(mimeType = mimeType), page)
            pageFileSaved = request.fileName to (file != null)
        }
    }

    /**
     * Keeps the person's answer to a site's ask in [tab]'s Profile: "Always allow" or "Block".
     * Nothing is kept for a Private Tab.
     */
    fun rememberPermission(tab: SavedTab, origin: SiteOrigin, settings: Set<SiteSetting>, allowed: Boolean) {
        if (tab.isPrivate) return
        scope.launch { settings.forEach { siteSettingsRepository.set(tab.profileId, origin, it, allowed) } }
    }

    /** Changes [profile]'s [setting] for [origin], in Site settings. */
    fun setSiteSetting(profile: Profile, origin: SiteOrigin, setting: SiteSetting, enabled: Boolean) {
        scope.launch { siteSettingsRepository.set(profile.id, origin, setting, enabled) }
    }

    /** Unsets [profile]'s [setting] for [origin]: the site asks again. */
    fun resetSiteSetting(profile: Profile, origin: SiteOrigin, setting: SiteSetting) {
        scope.launch { siteSettingsRepository.reset(profile.id, origin, setting) }
    }

    /** Unsets every Site setting of [profile]. */
    fun resetAllSiteSettings(profile: Profile) {
        scope.launch { siteSettingsRepository.resetAll(profile.id) }
    }

    /** [id] is on screen: it becomes the most recently shown, and Tabs past the limit unload. */
    fun shown(id: TabId) {
        live[id] ?: return
        shownId = id
        if (id == linkOffer?.tab) {
            linkOfferSeen = true
        } else if (linkOfferSeen) {
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

    fun create(name: String, look: ProfileLook) {
        scope.launch { profileRepository.create(name, look.color, look.icon, look.emoji, look.tint) }
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
                live.entries.filter { it.value.saved.profileId == profile.id }.map { it.key }.forEach(::retire)
                // Its Private Tabs close with it.
                privateTabs.closeAll(profile.id)?.let(::discard)
                siteIcons.clear(profile.id)
            }
        }
    }

    /**
     * Counts the person's choices of which Tab to show. Choosing a saved Tab is written to the
     * database before the Private Tab selection is lifted. If another choice was made meanwhile,
     * the earlier one must not undo it.
     */
    private var tabChoices = 0

    /** Opens an empty Tab in the Active Profile and shows it. */
    fun openTab(profile: Profile) {
        val choice = ++tabChoices
        scope.launch {
            tabRepository.open(profile.id)
            if (choice == tabChoices) privateTabs.select(profile.id, null)
        }
    }

    /** Opens an empty Private Tab in [profile] and shows it (SPEC, "Private Tabs"). */
    fun openPrivateTab(profile: Profile) {
        tabChoices++
        privateTabs.open(profile.id)
    }

    /** Shows the Tab [id], one of [profile]'s. */
    fun selectTab(profile: Profile, id: TabId) {
        val choice = ++tabChoices
        if (id.isPrivate) {
            privateTabs.select(profile.id, id)
        } else {
            scope.launch {
                tabRepository.select(id)
                if (choice == tabChoices) privateTabs.select(profile.id, null)
            }
        }
    }

    fun closeTab(id: TabId) {
        retire(id)
        if (id.isPrivate) {
            // Its session ends with the last Private Tab, and the session's store is discarded.
            val tab = privateTabs.find(id) ?: return
            privateTabs.close(tab.profileId, id)?.let(::discard)
        } else {
            scope.launch { tabRepository.close(id) }
        }
    }

    private val TabId.isPrivate: Boolean get() = value < 0

    /**
     * Discards an ended private session's store: now, if none of its pages is still on screen,
     * or as soon as the last one is gone.
     */
    private fun discard(session: PrivateSession) {
        val store = WebProfile.privateOf(session.profile, session.session).name
        endedStores += store
        emptyEndedStore(store)
    }

    private fun emptyEndedStore(store: String) {
        if (store !in endedStores || live.values.any { it.tab.profile.name == store }) return
        endedStores -= store
        WebProfile.named(store).clear()
    }

    /**
     * Move: closes [tab] and opens its current address as a new Tab in [profile], which becomes
     * the Active Profile. Only the address goes along (ADR 0003). Nothing happens for a Tab
     * without an address.
     */
    fun moveTab(tab: SavedTab, profile: Profile) {
        // A Private Tab stays private: it can't become a saved Tab of another Profile.
        if (tab.isPrivate) return
        val url = currentUrl(tab) ?: return
        scope.launch {
            val moved = tabRepository.move(tab.id, profile.id, url) ?: return@launch
            val wasIncomingLink = incomingLinkTab == tab.id
            if (linkOffer?.tab == tab.id) dismissIncomingLink()
            retire(tab.id)
            privateTabs.select(profile.id, null)
            history.record(profile.id, url)
            // Moving a link by hand is when a rule for it is wanted, unless one already sends it
            // to that Profile. The rule that decides for this link now is the one to change:
            // a new rule for the whole site would lose to a narrower one that names another
            // Profile. Without a rule, the link's site gets one.
            val deciding = siteRuleRepository.ruleFor(url)
            val pattern = deciding?.pattern ?: SitePattern.forSiteOf(url)
            if (wasIncomingLink && pattern != null && deciding?.profileId != profile.id) {
                offer(LinkOffer.Always(moved, pattern, profile, deciding))
            }
        }
    }

    /** Takes up the offered Site Rule ([LinkOffer.Always]): links to that site open in that Profile from now on. */
    fun acceptRuleOffer() {
        val offered = linkOffer as? LinkOffer.Always ?: return
        scope.launch {
            // The deciding rule is changed in place. Made anew it would be the youngest, and an
            // equally narrow rule that also covers the link would decide instead.
            val saved = siteRuleRepository.save(existing = offered.rule, offered.pattern, offered.profile.id)
            // Still the same offer: the person may have gone on meanwhile.
            if (linkOffer == offered) linkOffer = if (saved) LinkOffer.RuleSaved(offered.tab, offered.pattern, offered.profile) else null
        }
    }

    /** Adds a Site Rule, or changes [existing]: links that [pattern] covers open in [profile]. */
    fun saveSiteRule(existing: SiteRule?, pattern: SitePattern, profile: Profile) {
        scope.launch { siteRuleRepository.save(existing, pattern, profile.id) }
    }

    fun deleteSiteRule(rule: SiteRule) {
        scope.launch { siteRuleRepository.delete(rule) }
    }

    /** Open in Profile: opens [tab]'s current address as a new Tab in [profile] and shows it. [tab] stays. */
    fun openInProfile(tab: SavedTab, profile: Profile) {
        if (tab.isPrivate) return
        val url = currentUrl(tab) ?: return
        scope.launch {
            if (tabRepository.openIn(profile.id, url) != null) {
                privateTabs.select(profile.id, null)
                history.record(profile.id, url)
            }
        }
    }

    /**
     * Opens an Incoming Link, an address another app handed over, as a new Tab, and offers to
     * move it. A Site Rule that covers the address names the Profile, which becomes the Active
     * Profile; without one the link opens in the Active Profile. The caller checks that [url] is
     * a web address.
     */
    fun openIncomingLink(url: String) {
        scope.launch {
            // The Profile can be deleted between choosing it and opening the Tab. Then nothing is
            // opened there, its rules are gone with it, and the next try chooses again.
            repeat(INCOMING_LINK_ATTEMPTS) {
                val rule = siteRuleRepository.ruleFor(url)
                val target = rule?.profileId ?: profileRepository.activeProfile.filterNotNull().first().id
                val opened = if (rule != null) tabRepository.openIn(target, url) else tabRepository.openUrl(target, url)
                if (opened != null) {
                    offer(LinkOffer.Opened(opened, rule))
                    // Never in a Private Tab, even if that is what the person left the app from.
                    privateTabs.select(target, null)
                    history.record(target, url)
                    return@launch
                }
            }
        }
    }

    /**
     * What to offer while [text] is typed in [tab]'s address field: pages from the Active
     * Profile's bookmarks and history that match, then searches the search engine suggests.
     *
     * The text goes to the search engine only if the setting is on and it would be searched for
     * anyway: an address, or text shaped like credentials, is never sent. A Private Tab offers
     * nothing: what is typed there stays there, and it has no history to draw on.
     */
    suspend fun suggest(tab: SavedTab, text: String): List<Suggestion> {
        val query = text.trim()
        if (query.isEmpty() || tab.isPrivate) return emptyList()
        fun matches(title: String, url: String) = title.contains(query, ignoreCase = true) || url.contains(query, ignoreCase = true)
        val pages = (
            bookmarks.value.filter { matches(it.title, it.url) }.map { Suggestion(it.title.ifBlank { it.url }, it.url, Suggestion.Kind.Bookmark) } +
                visits.value.filter { matches(it.title, it.url) }.map { Suggestion(it.title.ifBlank { it.url }, it.url, Suggestion.Kind.History) }
            )
            // One page can be on record under more than one address. It is offered once.
            .distinctBy { it.text to WebAddress.hostOf(it.url) }
            .take(MAX_PAGE_SUGGESTIONS)
        val current = settings.value
        val searches = if (current.suggestions && AddressInput.isSearch(query)) {
            searchSuggestions.fetch(current.searchEngine, query).take(MAX_SEARCH_SUGGESTIONS).map { Suggestion(it, null, Suggestion.Kind.Search) }
        } else {
            emptyList()
        }
        return pages + searches
    }

    /**
     * Bookmarks the page [tab] shows, [url], in its Profile, or removes the bookmark if that
     * Profile has one. A newly bookmarked page's Tab becomes its Pinned Tab. A Private Tab stays
     * what it is: its page is only bookmarked.
     */
    fun toggleBookmark(tab: SavedTab, url: String, title: String) {
        scope.launch { bookmarkRepository.toggle(tab.profileId, url, title, pinTab = tab.id.takeUnless { tab.isPrivate }) }
    }

    /**
     * Pins [tab]: bookmarks the page it shows and makes the Tab that bookmark's Pinned Tab, which
     * the Sidebar lists under "Pinned". Nothing happens for a Private Tab or a Tab without a web page.
     */
    fun pinTab(tab: SavedTab) {
        if (tab.isPrivate) return
        val live = live[tab.id]?.tab
        val url = live?.url ?: tab.url ?: return
        val title = live?.pageTitle ?: tab.pageTitle.orEmpty()
        scope.launch { bookmarkRepository.pin(tab.id, url, title) }
    }

    /**
     * Shows [bookmark] as a Pinned Tab: its open Tab if it has one, else a new Tab that loads its
     * address. Either way a Private Tab is no longer the one shown.
     */
    fun openPinned(profile: Profile, bookmark: Bookmark) {
        val choice = ++tabChoices
        scope.launch {
            if (tabRepository.openPinned(bookmark.id) != null && choice == tabChoices) privateTabs.select(profile.id, null)
        }
    }

    /**
     * Shows [bookmark] as a Pinned Tab, chosen on the Start Page of [tab] (SPEC, "Start Page").
     * A closed one opens in [tab], which becomes its Pinned Tab, so no empty Tab is left behind;
     * it loads as a Pinned Tab opened from the Tab list does. An open one is shown, and [tab]
     * closes. A Private Tab never becomes a Pinned Tab: from one, the bookmark opens as
     * [openPinned] opens it.
     */
    fun openPinnedHere(profile: Profile, tab: SavedTab, bookmark: Bookmark) {
        if (tab.isPrivate) return openPinned(profile, bookmark)
        val choice = ++tabChoices
        scope.launch {
            val shown = tabRepository.openPinnedIn(tab.id, bookmark.id) ?: return@launch
            if (shown == tab.id) {
                live[tab.id]?.tab?.restore(bookmark.url, bookmark.title, scrollY = 0)
            } else {
                retire(tab.id)
            }
            if (choice == tabChoices) privateTabs.select(profile.id, null)
        }
    }

    /** Removes [bookmark]: unpins it. A Tab that showed it stays open as an ordinary Tab. */
    fun deleteBookmark(bookmark: Bookmark) {
        scope.launch { bookmarkRepository.delete(bookmark) }
    }

    fun renameBookmark(bookmark: Bookmark, title: String) {
        scope.launch { bookmarkRepository.rename(bookmark, title) }
    }

    /** Moves [bookmark] to the end of [folder], or of the top level with null. */
    fun moveBookmark(bookmark: Bookmark, folder: Folder?) {
        scope.launch { bookmarkRepository.move(bookmark, folder) }
    }

    /** Adds a Folder to [profile] and, with [first], moves that bookmark into it. */
    fun addFolder(profile: Profile, name: String, first: Bookmark? = null) {
        scope.launch {
            val id = bookmarkRepository.addFolder(profile.id, name) ?: return@launch
            if (first != null) bookmarkRepository.move(first, Folder(id, name))
        }
    }

    fun renameFolder(folder: Folder, name: String) {
        scope.launch { bookmarkRepository.renameFolder(folder, name) }
    }

    fun setFolderCollapsed(folder: Folder, collapsed: Boolean) {
        scope.launch { bookmarkRepository.setCollapsed(folder, collapsed) }
    }

    /** Deletes [folder]. Its bookmarks stay, at the top level. */
    fun deleteFolder(folder: Folder) {
        scope.launch { bookmarkRepository.deleteFolder(folder) }
    }

    /** Puts [profile]'s top level of bookmarks and Folders in the order of [items]. */
    fun reorderPinned(profile: Profile, items: List<PinnedItem>) {
        scope.launch { bookmarkRepository.reorder(profile.id, items) }
    }

    /** Puts the bookmarks of one of [profile]'s Folders in the order of [bookmarks]. */
    fun reorderFolder(profile: Profile, bookmarks: List<Bookmark>) {
        scope.launch { bookmarkRepository.reorderIn(profile.id, bookmarks) }
    }

    fun deleteVisit(visit: Visit) {
        scope.launch { history.delete(visit) }
    }

    /** Clears [profile]'s history, and the icons of every site it has no bookmark or open Tab of. */
    fun clearHistory(profile: Profile) {
        scope.launch {
            history.clear(profile.id)
            // What its live Tabs show now counts too: a page just opened may not be in its Tab's
            // saved row yet. A closed Tab still on screen is not one of them.
            val showing = live.entries
                .filter { (id, tab) -> id !in retired && tab.saved.profileId == profile.id && !tab.saved.isPrivate }
                .mapNotNull { it.value.tab.url }
            val kept = bookmarkRepository.bookmarks(profile.id).first().map { it.url } + tabRepository.tabs(profile.id).first().mapNotNull { it.url } + showing
            siteIcons.keepOnly(profile.id, kept)
        }
    }

    /**
     * Starts the download the person agreed to, in [profile]: with that Profile's cookies, so a
     * file behind its sign-in arrives as the page would get it, and on that Profile's list.
     */
    fun startDownload(profile: Profile, request: DownloadRequest) {
        if (!request.fromWeb) return
        val cookieManager = WebProfile.of(profile.id).cookieManager
        scope.launch {
            val id = withContext(Dispatchers.IO) { startAtFinalAddress(request, cookieManager::getCookie) }
            // The Profile was deleted meanwhile: nothing of it is fetched or listed any more.
            if (!downloadRepository.add(profile.id, id, request)) withContext(Dispatchers.IO) { downloader.cancel(id) }
        }
    }

    /**
     * Starts [request] at the address its redirects end at, with only that address's cookies.
     * Followed here, each hop gets the cookies of its own site; left to the download service,
     * the first site's cookies would travel along to every site a redirect leads to.
     */
    private fun startAtFinalAddress(request: DownloadRequest, cookiesFor: (String) -> String?): Long {
        val address = downloader.resolve(request.url, request.userAgent, cookiesFor)
        return downloader.start(request.copy(url = address), cookiesFor(address))
    }

    /**
     * Starts a download from the Private Tab [tab], with its session's cookies. The file is the
     * person's to keep, but the download isn't put on the Profile's list: a Private Tab leaves
     * nothing behind in the app.
     */
    fun startPrivateDownload(tab: SavedTab, request: DownloadRequest) {
        if (!request.fromWeb) return
        val session = privateTabs.current(tab.profileId) ?: return
        val cookieManager = WebProfile.privateOf(tab.profileId, session.session).cookieManager
        scope.launch { withContext(Dispatchers.IO) { startAtFinalAddress(request, cookieManager::getCookie) } }
    }

    /** Where each of [downloads] stands, by its list id. */
    suspend fun downloadStatuses(downloads: List<Download>): Map<Long, DownloadStatus> = withContext(Dispatchers.IO) {
        val byDownloadId = downloader.statuses(downloads.filter { it.fileUri == null }.map { it.downloadId })
        downloads.associate { download ->
            val saved = download.fileUri
            download.id to (if (saved != null) downloader.savedStatus(saved) else byDownloadId[download.downloadId] ?: DownloadStatus.Gone)
        }
    }

    /** Opens a finished download. False when no app can show it, or the file is gone. */
    fun openDownload(download: Download): Boolean = downloader.open(download)

    /**
     * Takes [download] off the list. One that is still running is stopped first; a finished file
     * stays on the device. Whether it is running is asked now, not taken from what the list last
     * showed, so a download never keeps running without a row.
     */
    fun removeDownload(download: Download) {
        scope.launch {
            // A file Okapi saved itself is already done: there is nothing to stop.
            if (download.fileUri == null) {
                withContext(Dispatchers.IO) {
                    val status = downloader.statuses(listOf(download.downloadId))[download.downloadId]
                    if (status is DownloadStatus.Running) downloader.cancel(download.downloadId)
                }
            }
            downloadRepository.remove(download)
        }
    }

    /** Ends the [linkOffer], whichever it is. */
    fun dismissIncomingLink() {
        linkOffer = null
        linkOfferSeen = false
    }

    /** The address [tab] shows now: the live page's, which can be newer than the saved one. */
    private fun currentUrl(tab: SavedTab): String? = live[tab.id]?.tab?.url ?: tab.url

    /** Saves the live Tabs' scroll positions and writes every open Profile's cookies to disk. */
    fun flush() {
        live.values.forEach(::saveScroll)
        // Not the private stores: nothing of them needs to reach the disk.
        live.values.filter { !it.saved.isPrivate }.map { it.tab.profile.name }.distinct().forEach { WebProfile.named(it).flush() }
    }

    fun destroy() {
        live.keys.toList().forEach(::destroy)
    }

    /**
     * Closes every Profile's Private Tabs and discards their stores, when the person closes the
     * app's window. Android may keep the process for a while, and nothing private may be there
     * when the app opens again.
     */
    fun closeAllPrivateTabs() {
        privateTabs.closeAll().forEach { session ->
            // Destroyed at once, even the one on screen: the window is going away.
            session.tabs.forEach { destroy(it.id) }
            discard(session)
        }
    }

    /** Destroys a closed Tab, or, if it's on screen, once another Tab replaces it. */
    private fun retire(id: TabId) {
        if (id == shownId) retired += id else destroy(id)
    }

    /** Frees [id]'s WebView, keeping it saved so it loads again when shown. */
    private fun unload(id: TabId) {
        val entry = live[id] ?: return
        saveScroll(entry)
        destroy(id)
    }

    private fun destroy(id: TabId) {
        val entry = live.remove(id) ?: return
        entry.saving?.cancel()
        entry.tab.destroy()
        emptyEndedStore(entry.tab.profile.name)
    }

    private fun saveScroll(entry: LiveTab) {
        val saved = entry.saved
        val scrollY = entry.tab.scrollY
        if (saved.isPrivate) {
            privateTabs.saveScroll(saved.profileId, saved.id, scrollY)
        } else {
            scope.launch { tabRepository.saveScroll(saved.id, scrollY) }
        }
    }

    companion object {
        private const val INCOMING_LINK_ATTEMPTS = 3
        private const val MAX_PAGE_SUGGESTIONS = 3
        private const val MAX_SEARCH_SUGGESTIONS = 5

        /** At most this many Tabs are live at once, across all Profiles (SPEC, "Tabs"). */
        const val MAX_LIVE_TABS = 5
    }
}
