package app.jrny.browser.tab

import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A Profile's open Private Tabs. They share one temporary web data store, named by [session],
 * which is discarded when the last of them closes.
 */
data class PrivateSession(
    val profile: ProfileId,
    /** Counts the Profile's private sessions since the app started. Each gets a store of its own. */
    val session: Int,
    val tabs: List<SavedTab>,
)

/**
 * Every Profile's Private Tabs (SPEC, "Private Tabs"). They are kept only here, in memory, for as
 * long as the app's process lives: never in the database, so they are never restored after the
 * app is killed. One instance serves the whole app, so they survive the activity being recreated.
 *
 * A Profile's selected Private Tab, while it has one, is what the Profile shows instead of its
 * selected normal Tab.
 */
class PrivateTabs {
    private val sessions = MutableStateFlow<Map<ProfileId, PrivateSession>>(emptyMap())
    private var nextId = -1L
    private var nextSession = 1

    /** [profile]'s Private Tabs, or null while it has none. */
    fun session(profile: ProfileId): Flow<PrivateSession?> = sessions.map { it[profile] }.distinctUntilChanged()

    fun current(profile: ProfileId): PrivateSession? = sessions.value[profile]

    /** The open Private Tab with [id], in whichever Profile it is. */
    fun find(id: TabId): SavedTab? = sessions.value.values.firstNotNullOfOrNull { session -> session.tabs.firstOrNull { it.id == id } }

    /** Opens a Private Tab in [profile], showing [url] or nothing, and selects it. The first one starts a session. */
    @Synchronized
    fun open(profile: ProfileId, url: String? = null): TabId {
        val id = TabId(nextId--)
        val tab = SavedTab(id, profile, url, title = "", scrollY = 0, selected = true, isPrivate = true)
        sessions.update { all ->
            val session = all[profile] ?: PrivateSession(profile, nextSession++, emptyList())
            all + (profile to session.copy(tabs = session.tabs.map { it.copy(selected = false) } + tab))
        }
        return id
    }

    /** Makes [id] the Private Tab [profile] shows, or none of them with null: its normal Tab shows again. */
    fun select(profile: ProfileId, id: TabId?) = change(profile) { tabs -> tabs.map { it.copy(selected = it.id == id) } }

    fun savePage(profile: ProfileId, id: TabId, url: String?, title: String) =
        change(profile) { tabs -> tabs.map { if (it.id == id) it.copy(url = url, title = title) else it } }

    fun saveScroll(profile: ProfileId, id: TabId, scrollY: Int) =
        change(profile) { tabs -> tabs.map { if (it.id == id) it.copy(scrollY = scrollY) else it } }

    /**
     * Closes [id]. If it was the selected one, the Profile's normal Tab shows again. Returns the
     * session that ended because this was its last Private Tab, whose store is now to be
     * discarded, or null.
     */
    @Synchronized
    fun close(profile: ProfileId, id: TabId): PrivateSession? {
        val session = sessions.value[profile] ?: return null
        val left = session.tabs.filter { it.id != id }
        if (left.size == session.tabs.size) return null
        sessions.update { all -> if (left.isEmpty()) all - profile else all + (profile to session.copy(tabs = left)) }
        return session.takeIf { left.isEmpty() }
    }

    /** Closes all of [profile]'s Private Tabs. Returns the session that ended, or null if it had none. */
    @Synchronized
    fun closeAll(profile: ProfileId): PrivateSession? {
        val session = sessions.value[profile] ?: return null
        sessions.update { it - profile }
        return session
    }

    private fun change(profile: ProfileId, tabs: (List<SavedTab>) -> List<SavedTab>) = sessions.update { all ->
        val session = all[profile] ?: return@update all
        all + (profile to session.copy(tabs = tabs(session.tabs)))
    }
}
