package app.jrny.browser.library

import app.jrny.browser.address.WebAddress
import app.jrny.browser.data.BookmarkDao
import app.jrny.browser.data.BookmarkEntity
import app.jrny.browser.data.HistoryDao
import app.jrny.browser.data.HistoryEntity
import app.jrny.browser.data.Placed
import app.jrny.browser.data.RecordedVisit
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.tab.TabId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A page visit in a Profile's history. */
data class Visit(val id: Long, val url: String, val title: String, val visitedAt: Long)

/** A bookmarked address in a Profile, at the top level or in the Folder [folderId]. */
data class Bookmark(val id: Long, val url: String, val title: String, val folderId: Long? = null)

/** A named group of a Profile's bookmarks, in their order. The Sidebar hides them while it is [collapsed]. */
data class Folder(val id: Long, val name: String, val collapsed: Boolean = false, val bookmarks: List<Bookmark> = emptyList())

/** An entry of a Profile's top level of bookmarks: a bookmark, or a Folder with its bookmarks. */
sealed interface PinnedItem {
    data class Page(val bookmark: Bookmark) : PinnedItem
    data class Group(val folder: Folder) : PinnedItem
}

/**
 * [profile]'s top level of bookmarks and its history, newest first, read together, so what
 * shows them never puts one Profile's pages under another's name.
 */
data class ProfilePages(val profile: ProfileId, val pinned: List<PinnedItem>, val visits: List<Visit>)

/** Rules for a Folder's name. */
object FolderName {
    const val MAX_LENGTH = 40

    /** The name to store, trimmed, or null when [input] can't be a name (blank, or too long). */
    fun clean(input: String): String? = input.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}

/**
 * Each Profile's history (SPEC, "Browsing features"). Only web pages are kept, and at most
 * [MAX_VISITS] per Profile: the oldest go first. [now] gives the time of a visit.
 */
class HistoryRepository(private val dao: HistoryDao, private val now: () -> Long = System::currentTimeMillis) {

    /** [profile]'s visits, newest first. */
    fun visits(profile: ProfileId): Flow<List<Visit>> =
        dao.observe(profile.toLong(), SHOWN_VISITS).map { rows -> rows.map { Visit(it.id, it.url, it.title, it.visitedAt) } }

    /**
     * Records that [profile] visited [url]. Addresses that aren't web pages are left out, and a
     * repeat of the Profile's latest visit only moves its time. Returns what was written, for
     * [takeBack], or null when nothing was.
     */
    suspend fun record(profile: ProfileId, url: String): RecordedVisit? {
        if (!WebAddress.isWebAddress(url)) return null
        return dao.record(HistoryEntity(profileId = profile.toLong(), url = url, visitedAt = now()), MAX_VISITS)
    }

    /**
     * Undoes [recorded], for a visit that turned out to be only a redirect's stop on the way to
     * the real page: a new row goes, and a repeat gets its earlier time back.
     */
    suspend fun takeBack(recorded: RecordedVisit) {
        val earlier = recorded.previousVisitedAt
        if (earlier == null) dao.delete(recorded.id) else dao.setVisitedAt(recorded.id, earlier)
    }

    /** Sets the title of [profile]'s latest visit of [url], once the page has one. */
    suspend fun setTitle(profile: ProfileId, url: String, title: String) {
        if (title.isNotBlank()) dao.setTitle(profile.toLong(), url, title)
    }

    suspend fun delete(visit: Visit) = dao.delete(visit.id)

    suspend fun clear(profile: ProfileId) = dao.clear(profile.toLong())

    companion object {
        const val MAX_VISITS = 5_000

        /** How many visits the history list shows. */
        const val SHOWN_VISITS = 500
    }
}

/**
 * Each Profile's bookmarks and the Folders they are in. Only web pages can be bookmarked. The
 * Sidebar lists them as the Profile's Pinned Tabs (SPEC, "Pinned Tabs").
 */
class BookmarkRepository(private val dao: BookmarkDao, private val now: () -> Long = System::currentTimeMillis) {

    /** All of [profile]'s bookmarks, in their order. */
    fun bookmarks(profile: ProfileId): Flow<List<Bookmark>> =
        dao.observe(profile.toLong()).map { rows -> rows.map(::toBookmark) }

    /**
     * [profile]'s top level, in its order: bookmarks and Folders, each Folder with its bookmarks.
     * A bookmark whose Folder is gone counts as top level.
     */
    fun pinned(profile: ProfileId): Flow<List<PinnedItem>> =
        combine(dao.observe(profile.toLong()), dao.observeFolders(profile.toLong())) { bookmarks, folders ->
            val known = folders.map { it.id }.toSet()
            val inFolders = bookmarks.filter { it.folderId in known }.groupBy { it.folderId }
            val pages = bookmarks.filter { it.folderId !in known }.map { it.position to PinnedItem.Page(toBookmark(it).copy(folderId = null)) }
            val groups = folders.map { folder ->
                folder.position to PinnedItem.Group(Folder(folder.id, folder.name, folder.collapsed, inFolders[folder.id].orEmpty().map(::toBookmark)))
            }
            // Both lists are in order already, and a stable sort keeps that among equal places.
            (pages + groups).sortedBy { it.first }.map { it.second }
        }

    /** Bookmarks [url] in [profile]. Nothing changes if it's already bookmarked there. */
    suspend fun add(profile: ProfileId, url: String, title: String) {
        if (!WebAddress.isWebAddress(url)) return
        dao.add(BookmarkEntity(profileId = profile.toLong(), url = url, title = title, createdAt = now()))
    }

    suspend fun remove(profile: ProfileId, url: String) = dao.remove(profile.toLong(), url)

    /**
     * Bookmarks [url] in [profile], or removes the bookmark if that Profile has one. A newly
     * added bookmark takes [pinTab], a Tab of that Profile, as its Pinned Tab. Returns whether
     * the address is bookmarked afterwards. An address that isn't a web page never is.
     */
    suspend fun toggle(profile: ProfileId, url: String, title: String, pinTab: TabId? = null): Boolean {
        if (!WebAddress.isWebAddress(url)) return false
        return dao.toggle(BookmarkEntity(profileId = profile.toLong(), url = url, title = title, createdAt = now()), pinTab?.value)
    }

    /**
     * Pins the Tab [tab], which shows [url]: bookmarks the address in the Tab's Profile, unless it
     * is bookmarked already, and makes the Tab that bookmark's Pinned Tab. False, with nothing
     * changed, when the address isn't a web page or the Tab is gone.
     */
    suspend fun pin(tab: TabId, url: String, title: String): Boolean {
        if (!WebAddress.isWebAddress(url)) return false
        // The Tab's own Profile is filled in from its row.
        return dao.pin(tab.value, BookmarkEntity(profileId = 0, url = url, title = title, createdAt = now())) != null
    }

    /** Removes [bookmark]. A Tab that showed it as a Pinned Tab stays open as an ordinary Tab. */
    suspend fun delete(bookmark: Bookmark) = dao.delete(bookmark.id)

    /** Gives [bookmark] another name. A blank one is refused. */
    suspend fun rename(bookmark: Bookmark, title: String) {
        if (title.isNotBlank()) dao.rename(bookmark.id, title.trim())
    }

    /** Moves [bookmark] to the end of [folder], or of the top level with null. */
    suspend fun move(bookmark: Bookmark, folder: Folder?) = dao.move(bookmark.id, folder?.id)

    /** Adds an empty Folder to [profile]. Returns its id, or null when the name can't be one or the Profile is gone. */
    suspend fun addFolder(profile: ProfileId, name: String): Long? =
        FolderName.clean(name)?.let { dao.addFolder(profile.toLong(), it, now()) }

    suspend fun renameFolder(folder: Folder, name: String) {
        FolderName.clean(name)?.let { dao.renameFolder(folder.id, it) }
    }

    suspend fun setCollapsed(folder: Folder, collapsed: Boolean) = dao.setCollapsed(folder.id, collapsed)

    /** Deletes [folder]. Its bookmarks stay, at the top level. */
    suspend fun deleteFolder(folder: Folder) = dao.deleteFolder(folder.id)

    /** Puts [profile]'s top level in the order of [items]. */
    suspend fun reorder(profile: ProfileId, items: List<PinnedItem>) = dao.reorder(
        profile.toLong(),
        items.map {
            when (it) {
                is PinnedItem.Page -> Placed(it.bookmark.id, folder = false)
                is PinnedItem.Group -> Placed(it.folder.id, folder = true)
            }
        },
    )

    /** Puts the bookmarks of one Folder of [profile] in the order of [bookmarks]. */
    suspend fun reorderIn(profile: ProfileId, bookmarks: List<Bookmark>) =
        dao.reorder(profile.toLong(), bookmarks.map { Placed(it.id, folder = false) })

    private fun toBookmark(row: BookmarkEntity) = Bookmark(row.id, row.url, row.title, row.folderId)
}

private fun ProfileId.toLong(): Long = value.toLong()
