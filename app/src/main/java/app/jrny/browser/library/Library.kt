package app.jrny.browser.library

import app.jrny.browser.address.WebAddress
import app.jrny.browser.data.BookmarkDao
import app.jrny.browser.data.BookmarkEntity
import app.jrny.browser.data.HistoryDao
import app.jrny.browser.data.HistoryEntity
import app.jrny.browser.data.RecordedVisit
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A page visit in a Profile's history. */
data class Visit(val id: Long, val url: String, val title: String, val visitedAt: Long)

/** A bookmarked address in a Profile. */
data class Bookmark(val id: Long, val url: String, val title: String)

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

/** Each Profile's bookmarks. Only web pages can be bookmarked. */
class BookmarkRepository(private val dao: BookmarkDao, private val now: () -> Long = System::currentTimeMillis) {

    /** [profile]'s bookmarks, newest first. */
    fun bookmarks(profile: ProfileId): Flow<List<Bookmark>> =
        dao.observe(profile.toLong()).map { rows -> rows.map { Bookmark(it.id, it.url, it.title) } }

    /** Bookmarks [url] in [profile]. Nothing changes if it's already bookmarked there. */
    suspend fun add(profile: ProfileId, url: String, title: String) {
        if (!WebAddress.isWebAddress(url)) return
        dao.add(BookmarkEntity(profileId = profile.toLong(), url = url, title = title, createdAt = now()))
    }

    suspend fun remove(profile: ProfileId, url: String) = dao.remove(profile.toLong(), url)

    /**
     * Bookmarks [url] in [profile], or removes the bookmark if that Profile has one. Returns
     * whether it is bookmarked afterwards. An address that isn't a web page never is.
     */
    suspend fun toggle(profile: ProfileId, url: String, title: String): Boolean {
        if (!WebAddress.isWebAddress(url)) return false
        return dao.toggle(BookmarkEntity(profileId = profile.toLong(), url = url, title = title, createdAt = now()))
    }

    suspend fun delete(bookmark: Bookmark) = dao.delete(bookmark.id)
}

private fun ProfileId.toLong(): Long = value.toLong()
