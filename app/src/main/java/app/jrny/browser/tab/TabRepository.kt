package app.jrny.browser.tab

import app.jrny.browser.data.TabDao
import app.jrny.browser.data.TabEntity
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Each Profile's Tabs and which one it shows. A Profile always has at least one Tab. */
class TabRepository(private val dao: TabDao) {

    /** [profile]'s Tabs in the order they were opened, with its selected Tab marked. */
    fun tabs(profile: ProfileId): Flow<List<SavedTab>> = dao.observe(profile.toLong()).map { rows ->
        val selected = rows.maxByOrNull { it.lastUsed }?.id
        rows.map { it.toSavedTab(selected = it.id == selected) }
    }

    /** Opens a Tab at the end of [profile]'s list, showing [url] or nothing, and selects it. */
    suspend fun open(profile: ProfileId, url: String? = null): TabId = TabId(dao.open(profile.toLong(), url))

    suspend fun select(id: TabId) = dao.select(id.value)

    /** Closes [id]. The Profile's most recently used remaining Tab takes over; the last leaves an empty one. */
    suspend fun close(id: TabId) = dao.close(id.value)

    /** Gives [profile] an empty Tab if it has none. */
    suspend fun ensureOne(profile: ProfileId) = dao.ensureOne(profile.toLong())

    suspend fun savePage(id: TabId, url: String?, title: String) = dao.savePage(id.value, url, title)

    suspend fun saveScroll(id: TabId, scrollY: Int) = dao.saveScroll(id.value, scrollY)

    private fun TabEntity.toSavedTab(selected: Boolean) = SavedTab(
        id = TabId(id),
        profileId = ProfileId(profileId.toString()),
        url = url,
        title = title,
        scrollY = scrollY,
        selected = selected,
    )

    private fun ProfileId.toLong(): Long = value.toLong()
}
