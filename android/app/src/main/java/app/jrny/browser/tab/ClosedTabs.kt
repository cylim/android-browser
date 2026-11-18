package app.jrny.browser.tab

import app.jrny.browser.data.ClosedTabDao
import app.jrny.browser.data.ClosedTabEntity
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A Tab a Profile closed recently: what it showed, and the place it had (SPEC, "Reopen closed Tab"). */
data class ClosedTab(val id: Long, val profileId: ProfileId, val tabId: TabId, val url: String, val title: String)

/** Each Profile's recently closed Tabs, its latest ten, kept on disk with only their address and title. */
class ClosedTabRepository(private val dao: ClosedTabDao, private val now: () -> Long = System::currentTimeMillis) {

    /** [profile]'s recently closed Tabs, newest first. */
    fun closed(profile: ProfileId): Flow<List<ClosedTab>> = dao.observe(profile.value.toLong()).map { rows -> rows.map(::toClosed) }

    /** Remembers that [tab], showing [url] titled [title], closed. Returns it, or null when the Profile is gone. */
    suspend fun add(tab: TabId, profile: ProfileId, url: String, title: String): ClosedTab? {
        val row = ClosedTabEntity(profileId = profile.value.toLong(), tabId = tab.value, url = url, title = title, closedAt = now())
        return dao.add(row)?.let { toClosed(row.copy(id = it)) }
    }

    /** Takes [closed] off the list, as it opens again. */
    suspend fun remove(closed: ClosedTab) = dao.delete(closed.id)

    suspend fun clear(profile: ProfileId) = dao.clear(profile.value.toLong())

    private fun toClosed(row: ClosedTabEntity) = ClosedTab(row.id, ProfileId(row.profileId.toString()), TabId(row.tabId), row.url, row.title)
}
