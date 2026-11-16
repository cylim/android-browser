package app.jrny.browser

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the browser window shows: the Profiles, the Active Profile, and one Tab per Profile until
 * Milestone 3 adds more. Each Tab is created the first time its Profile is shown, in that
 * Profile's web data store, and lives until its Profile is deleted or the activity ends.
 *
 * Create and use it on the main thread.
 */
class BrowserState(
    private val context: Context,
    private val repository: ProfileRepository,
    private val scope: CoroutineScope,
    /** Addresses to reload after the activity was recreated, by Profile id. */
    private val restored: Map<ProfileId, String> = emptyMap(),
) {
    val profiles: StateFlow<List<Profile>> = repository.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val activeProfile: StateFlow<Profile?> = repository.activeProfile.stateIn(scope, SharingStarted.Eagerly, null)
    val pendingDeletionCount: StateFlow<Int> = repository.pendingDeletionCount.stateIn(scope, SharingStarted.Eagerly, 0)

    private val tabs = mutableStateMapOf<ProfileId, Tab>()

    /** The Tab of [profile], created in its web data store the first time it's asked for. */
    fun tabFor(profile: Profile): Tab = tabs.getOrPut(profile.id) {
        Tab(context, WebProfile.of(profile.id)).also { tab -> restored[profile.id]?.let(tab::load) }
    }

    /** The address each open Tab shows, by Profile, to restore after the activity is recreated. */
    fun addresses(): Map<ProfileId, String> =
        tabs.mapNotNull { (id, tab) -> tab.url?.let { id to it } }.toMap()

    fun switchTo(profile: Profile) {
        scope.launch { repository.activate(profile.id) }
    }

    fun create(name: String, color: ProfileColor, icon: ProfileIcon) {
        scope.launch { repository.create(name, color, icon) }
    }

    fun update(profile: Profile) {
        scope.launch { repository.update(profile) }
    }

    /**
     * Deletes [profile]: it disappears, its Tab closes, and its web data is wiped at the next cold
     * start. Nothing happens for the last Profile.
     */
    fun delete(profile: Profile) {
        scope.launch {
            if (repository.delete(profile.id)) tabs.remove(profile.id)?.destroy()
        }
    }

    /** Writes every open Profile's cookies to disk. */
    fun flush() = tabs.values.forEach { it.profile.flush() }

    fun destroy() {
        tabs.values.forEach(Tab::destroy)
        tabs.clear()
    }
}
