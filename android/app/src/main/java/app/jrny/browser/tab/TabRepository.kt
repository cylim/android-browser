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

    /** Opens [url] as a new Tab in [profile] and selects it. Returns null, and opens nothing, when the Profile is gone. */
    suspend fun openUrl(profile: ProfileId, url: String): TabId? = dao.openInLive(profile.toLong(), url)?.let(::TabId)

    /**
     * Open in Profile: opens [url] as a new Tab in [profile] and makes it the Active Profile.
     * Returns null when the Profile is gone.
     */
    suspend fun openIn(profile: ProfileId, url: String): TabId? = dao.openAndActivate(profile.toLong(), url)?.let(::TabId)

    /**
     * Move: closes [id] and opens [url], its current address, as a new Tab in [profile], which
     * becomes the Active Profile. Only the address is carried (ADR 0003). Returns null, and does
     * nothing, when the Tab or Profile is gone or the Tab is already in [profile].
     */
    suspend fun move(id: TabId, profile: ProfileId, url: String): TabId? = dao.move(id.value, profile.toLong(), url)?.let(::TabId)

    /**
     * Shows the bookmark [bookmarkId] as a Pinned Tab: its open Tab is selected, and a closed one
     * gets a new Tab that loads the bookmark's address. Returns null when the bookmark is gone.
     */
    suspend fun openPinned(bookmarkId: Long): TabId? = dao.openPinned(bookmarkId)?.let(::TabId)

    /**
     * Shows the bookmark [bookmarkId] as a Pinned Tab, chosen on the Start Page of [tab]: a closed
     * one makes [tab] its Pinned Tab, with the bookmark's address, and an open one is selected
     * while [tab] closes. Returns the Tab that shows the bookmark, or null when the bookmark is
     * gone or belongs to another Profile.
     */
    suspend fun openPinnedIn(tab: TabId, bookmarkId: Long): TabId? = dao.openPinnedIn(tab.value, bookmarkId)?.let(::TabId)

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
        bookmarkId = bookmarkId,
    )

    private fun ProfileId.toLong(): Long = value.toLong()
}
