package app.jrny.browser.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One row per Profile. A deleted Profile stays as a row with [pendingDeletion] until its web data
 * is wiped at the next cold start (ADR 0001), then the row goes. The id is AUTOINCREMENT, so an id
 * is never reused, even after a delete: it names the Profile's web data store (ADR 0004).
 *
 * The Active Profile is the live one with the highest [activation], a counter that grows each
 * time a Profile becomes active, so there is always exactly one and nothing else to keep in sync.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val icon: String,
    val activation: Long = 0,
    val pendingDeletion: Boolean = false,
    /** How strongly the Profile's color tints the browser: a `ProfileTint` name. */
    @ColumnInfo(defaultValue = "Medium") val tint: String = "Medium",
)

@Dao
abstract class ProfileDao {
    /** Live Profiles, oldest first: the order the switcher shows them in. */
    @Query("SELECT * FROM profiles WHERE pendingDeletion = 0 ORDER BY id")
    abstract fun observeLive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE pendingDeletion = 0 ORDER BY activation DESC LIMIT 1")
    abstract fun observeActive(): Flow<ProfileEntity?>

    @Query("SELECT COUNT(*) FROM profiles WHERE pendingDeletion = 1")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM profiles")
    abstract suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM profiles WHERE pendingDeletion = 0")
    abstract suspend fun countLive(): Int

    @Query("SELECT id FROM profiles WHERE pendingDeletion = 1")
    abstract suspend fun pendingIds(): List<Long>

    @Query("SELECT COALESCE(MAX(activation), 0) FROM profiles")
    protected abstract suspend fun maxActivation(): Long

    @Insert
    protected abstract suspend fun insertRow(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET name = :name, color = :color, icon = :icon, tint = :tint WHERE id = :id AND pendingDeletion = 0")
    abstract suspend fun update(id: Long, name: String, color: String, icon: String, tint: String)

    @Query("UPDATE profiles SET activation = :activation WHERE id = :id AND pendingDeletion = 0")
    protected abstract suspend fun setActivation(id: Long, activation: Long)

    @Query("UPDATE profiles SET pendingDeletion = 1 WHERE id = :id")
    protected abstract suspend fun markPending(id: Long)

    @Query("DELETE FROM tabs WHERE profileId = :id")
    protected abstract suspend fun deleteTabs(id: Long)

    @Query("DELETE FROM history WHERE profileId = :id")
    protected abstract suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM bookmarks WHERE profileId = :id")
    protected abstract suspend fun deleteBookmarks(id: Long)

    @Query("DELETE FROM downloads WHERE profileId = :id")
    protected abstract suspend fun deleteDownloads(id: Long)

    @Query("DELETE FROM site_rules WHERE profileId = :id")
    protected abstract suspend fun deleteSiteRules(id: Long)

    @Query("DELETE FROM folders WHERE profileId = :id")
    protected abstract suspend fun deleteFolders(id: Long)

    @Query("DELETE FROM site_settings WHERE profileId = :id")
    protected abstract suspend fun deleteSiteSettings(id: Long)

    @Query("DELETE FROM profiles WHERE id = :id AND pendingDeletion = 1")
    abstract suspend fun purge(id: Long)

    /** Inserts [profile] as the Active Profile and returns its id. */
    @Transaction
    open suspend fun insertActive(profile: ProfileEntity): Long =
        insertRow(profile.copy(activation = maxActivation() + 1))

    @Transaction
    open suspend fun activate(id: Long) = setActivation(id, maxActivation() + 1)

    /**
     * Marks [id] for deletion unless it's the last live Profile, and removes its Tabs, history,
     * bookmarks, Folders and list of downloads at once, so no address from it outlives it on disk. The
     * downloaded files are the person's own and stay. The Site Rules that sent links to it go
     * too: those links open in the Active Profile again, and so do its Site settings. Returns whether it did.
     */
    @Transaction
    open suspend fun markPendingUnlessLast(id: Long): Boolean {
        if (countLive() <= 1) return false
        markPending(id)
        deleteTabs(id)
        deleteHistory(id)
        deleteBookmarks(id)
        deleteFolders(id)
        deleteDownloads(id)
        deleteSiteRules(id)
        deleteSiteSettings(id)
        return true
    }
}

/**
 * One row per open Tab, so Tabs come back after Android kills the app (SPEC, "Tabs"). Only what's
 * needed to reload the page is kept: its address, title and scroll position, never its content.
 *
 * A Profile's selected Tab is its Tab with the highest [lastUsed], a counter shared by all Tabs
 * that grows each time a Tab is selected, so each Profile has one and nothing else to keep in sync.
 */
@Entity(
    tableName = "tabs",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId")],
)
data class TabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** Null for a new Tab that hasn't loaded anything. */
    val url: String? = null,
    val title: String = "",
    /** The page's vertical scroll position, in pixels. */
    val scrollY: Int = 0,
    val lastUsed: Long = 0,
    /**
     * The bookmark this Tab is the open Pinned Tab of, or null for an ordinary Tab (SPEC, "Pinned
     * Tabs"). A bookmark has at most one. Not a foreign key: the DAOs clear it when the bookmark goes.
     */
    val bookmarkId: Long? = null,
)

@Dao
abstract class TabDao {
    /** A Profile's Tabs, oldest first: the order the Tab list shows them in. */
    @Query("SELECT * FROM tabs WHERE profileId = :profileId ORDER BY id")
    abstract fun observe(profileId: Long): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    abstract suspend fun get(id: Long): TabEntity?

    @Query("UPDATE tabs SET url = :url, title = :title WHERE id = :id")
    abstract suspend fun savePage(id: Long, url: String?, title: String)

    @Query("UPDATE tabs SET scrollY = :scrollY WHERE id = :id")
    abstract suspend fun saveScroll(id: Long, scrollY: Int)

    @Query("SELECT COALESCE(MAX(lastUsed), 0) FROM tabs")
    protected abstract suspend fun maxLastUsed(): Long

    @Query("SELECT COUNT(*) FROM tabs WHERE profileId = :profileId")
    protected abstract suspend fun count(profileId: Long): Int

    @Insert
    protected abstract suspend fun insertRow(tab: TabEntity): Long

    @Query("UPDATE tabs SET lastUsed = :lastUsed WHERE id = :id")
    protected abstract suspend fun setLastUsed(id: Long, lastUsed: Long)

    @Query("DELETE FROM tabs WHERE id = :id")
    protected abstract suspend fun deleteRow(id: Long)

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    protected abstract suspend fun bookmark(id: Long): BookmarkEntity?

    @Query("SELECT id FROM tabs WHERE bookmarkId = :bookmarkId LIMIT 1")
    protected abstract suspend fun tabOf(bookmarkId: Long): Long?

    /**
     * Shows the bookmark [bookmarkId] as a Pinned Tab: its open Tab is selected, and a closed one
     * gets a new Tab that loads the bookmark's address. Returns the Tab's id, or null when the
     * bookmark is gone.
     */
    @Transaction
    open suspend fun openPinned(bookmarkId: Long): Long? {
        val pinned = bookmark(bookmarkId) ?: return null
        tabOf(bookmarkId)?.let { open ->
            select(open)
            return open
        }
        return insertRow(TabEntity(profileId = pinned.profileId, url = pinned.url, title = pinned.title, lastUsed = maxLastUsed() + 1, bookmarkId = bookmarkId))
    }

    @Query("UPDATE tabs SET url = :url, title = :title, scrollY = 0, bookmarkId = :bookmarkId WHERE id = :id")
    protected abstract suspend fun becomePinned(id: Long, url: String, title: String, bookmarkId: Long)

    /**
     * Shows the bookmark [bookmarkId] as a Pinned Tab from the Start Page of the Tab [tabId]: a
     * closed one takes that Tab, which loads the bookmark's address, so no empty Tab is left
     * behind. An open one is selected, and [tabId] closes. Returns the Tab that shows the
     * bookmark, or null when the bookmark is gone or belongs to another Profile. When [tabId] is
     * gone, the bookmark opens as [openPinned] opens it.
     */
    @Transaction
    open suspend fun openPinnedIn(tabId: Long, bookmarkId: Long): Long? {
        val pinned = bookmark(bookmarkId) ?: return null
        val here = get(tabId) ?: return openPinned(bookmarkId)
        if (here.profileId != pinned.profileId) return null
        val open = tabOf(bookmarkId)
        if (open != null && open != tabId) {
            select(open)
            deleteRow(tabId)
            return open
        }
        becomePinned(tabId, pinned.url, pinned.title, bookmarkId)
        select(tabId)
        return tabId
    }

    @Query(
        "UPDATE profiles SET activation = (SELECT COALESCE(MAX(activation), 0) + 1 FROM profiles) " +
            "WHERE id = :profileId AND pendingDeletion = 0",
    )
    protected abstract suspend fun activateProfile(profileId: Long): Int

    /** Opens a Tab at the end of [profileId]'s list, selects it, and returns its id. */
    @Transaction
    open suspend fun open(profileId: Long, url: String?): Long =
        insertRow(TabEntity(profileId = profileId, url = url, lastUsed = maxLastUsed() + 1))

    @Transaction
    open suspend fun select(id: Long) = setLastUsed(id, maxLastUsed() + 1)

    /**
     * Opens [url] as a new Tab at the end of [profileId]'s list without selecting it: the shown
     * Tab stays (SPEC, "Link and image menu"). It counts as the least recently used. Returns its
     * id, or null when the Profile is gone.
     */
    @Transaction
    open suspend fun openBehind(profileId: Long, url: String): Long? =
        if (profileIsLive(profileId)) insertRow(TabEntity(profileId = profileId, url = url, lastUsed = 0)) else null

    /** Opens an empty Tab in [profileId] if it has none, so every Profile always has one. */
    @Transaction
    open suspend fun ensureOne(profileId: Long) {
        if (count(profileId) == 0) open(profileId, null)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean

    /**
     * Opens [url] as a new Tab in [profileId], unless that Profile has been deleted: a deleted
     * Profile's Tabs are gone, and a late one would keep its address on disk. Returns the Tab's
     * id, or null when the Profile is gone.
     */
    @Transaction
    open suspend fun openInLive(profileId: Long, url: String): Long? =
        if (profileIsLive(profileId)) open(profileId, url) else null

    /**
     * Opens [url] as a new Tab in [profileId] and makes that Profile the Active Profile, in one
     * step, so the window never shows one without the other. Returns the Tab's id, or null when
     * the Profile is gone.
     */
    @Transaction
    open suspend fun openAndActivate(profileId: Long, url: String): Long? {
        if (activateProfile(profileId) == 0) return null
        return open(profileId, url)
    }

    /**
     * Moves [id] to [profileId], carrying only [url]: the original Tab closes, and a new Tab in
     * the target Profile, which becomes the Active Profile, loads the address afresh (ADR 0003).
     * Title, scroll position and history stay behind. Returns the new Tab's id, or null, and
     * nothing changes, when the Tab or the Profile is gone or the Tab is already there.
     */
    @Transaction
    open suspend fun move(id: Long, profileId: Long, url: String): Long? {
        val row = get(id) ?: return null
        if (row.profileId == profileId) return null
        val moved = openAndActivate(profileId, url) ?: return null
        close(id)
        return moved
    }

    /**
     * Closes [id]. The Profile's most recently used remaining Tab becomes its selected Tab, and
     * closing its last Tab leaves an empty one.
     */
    @Transaction
    open suspend fun close(id: Long) {
        val row = get(id) ?: return
        deleteRow(id)
        ensureOne(row.profileId)
    }
}

/** One row per page visit, kept per Profile (SPEC, "Browsing features"). */
@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId", "visitedAt")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val url: String,
    val title: String = "",
    /** When the page was visited, in milliseconds since the epoch. */
    val visitedAt: Long,
)

@Dao
abstract class HistoryDao {
    /** A Profile's most recent visits, newest first. */
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC, id DESC LIMIT :limit")
    abstract fun observe(profileId: Long, limit: Int): Flow<List<HistoryEntity>>

    @Insert
    protected abstract suspend fun insertRow(visit: HistoryEntity): Long

    @Query(
        "DELETE FROM history WHERE profileId = :profileId AND id NOT IN " +
            "(SELECT id FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC, id DESC LIMIT :keep)",
    )
    protected abstract suspend fun trim(profileId: Long, keep: Int)

    /** Gives the Profile's latest visit of [url] its page title, once the page has reported it. */
    @Query(
        "UPDATE history SET title = :title WHERE id = " +
            "(SELECT id FROM history WHERE profileId = :profileId AND url = :url ORDER BY visitedAt DESC, id DESC LIMIT 1)",
    )
    abstract suspend fun setTitle(profileId: Long, url: String, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    abstract suspend fun clear(profileId: Long)

    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC, id DESC LIMIT 1")
    protected abstract suspend fun latest(profileId: Long): HistoryEntity?

    @Query("UPDATE history SET visitedAt = :visitedAt WHERE id = :id")
    abstract suspend fun setVisitedAt(id: Long, visitedAt: Long)

    /**
     * Records a visit and drops the Profile's oldest visits beyond [keep]. Visiting the address
     * of the Profile's latest visit again only moves that visit's time. Returns what was written,
     * so it can be taken back, or null for a Profile that is gone.
     */
    @Transaction
    open suspend fun record(visit: HistoryEntity, keep: Int): RecordedVisit? {
        if (!profileIsLive(visit.profileId)) return null
        val latest = latest(visit.profileId)
        if (latest != null && latest.url == visit.url) {
            setVisitedAt(latest.id, visit.visitedAt)
            return RecordedVisit(latest.id, previousVisitedAt = latest.visitedAt)
        }
        val id = insertRow(visit)
        trim(visit.profileId, keep)
        return RecordedVisit(id, previousVisitedAt = null)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean
}

/**
 * What [HistoryDao.record] wrote: a new row, or, with [previousVisitedAt], only a new time on the
 * row the Profile's latest visit already had.
 */
data class RecordedVisit(val id: Long, val previousVisitedAt: Long?)

/**
 * One row per bookmarked address, kept per Profile. An address is bookmarked at most once in a
 * Profile. The Sidebar lists a Profile's bookmarks as its Pinned Tabs (SPEC, "Pinned Tabs").
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["profileId", "url"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val url: String,
    val title: String = "",
    val createdAt: Long,
    /** The Folder it is in, or null at the top level. Not a foreign key: [BookmarkDao] keeps it right. */
    val folderId: Long? = null,
    /**
     * Its place: lower comes first, among the Profile's top-level bookmarks and Folders, or inside
     * its Folder. One counter per Profile gives every new or moved item the next number.
     */
    @ColumnInfo(defaultValue = "0") val position: Long = 0,
)

/** One row per Folder, a named group of a Profile's bookmarks. Folders don't nest. */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    /** Its place among the Profile's top-level bookmarks and Folders. */
    val position: Long = 0,
    /** Whether the Sidebar hides its bookmarks. */
    val collapsed: Boolean = false,
    val createdAt: Long,
)

@Dao
abstract class BookmarkDao {
    /** A Profile's bookmarks, in their order. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY position, id")
    abstract fun observe(profileId: Long): Flow<List<BookmarkEntity>>

    /** A Profile's Folders, in their order. */
    @Query("SELECT * FROM folders WHERE profileId = :profileId ORDER BY position, id")
    abstract fun observeFolders(profileId: Long): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRow(bookmark: BookmarkEntity): Long

    @Insert
    protected abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    protected abstract suspend fun get(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND url = :url")
    protected abstract suspend fun find(profileId: Long, url: String): BookmarkEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    protected abstract suspend fun folder(id: Long): FolderEntity?

    @Query("SELECT * FROM tabs WHERE id = :id")
    protected abstract suspend fun tab(id: Long): TabEntity?

    @Query("DELETE FROM bookmarks WHERE id = :id")
    protected abstract suspend fun deleteRow(id: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    protected abstract suspend fun deleteFolderRow(id: Long)

    /** The Tab that showed the bookmark becomes an ordinary Tab. */
    @Query("UPDATE tabs SET bookmarkId = NULL WHERE bookmarkId = :bookmarkId")
    protected abstract suspend fun unpinTabs(bookmarkId: Long)

    @Query("UPDATE tabs SET bookmarkId = :bookmarkId WHERE id = :tabId")
    protected abstract suspend fun setTabBookmark(tabId: Long, bookmarkId: Long)

    @Query("UPDATE bookmarks SET title = :title WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String)

    @Query("UPDATE bookmarks SET folderId = :folderId, position = :position WHERE id = :id")
    protected abstract suspend fun place(id: Long, folderId: Long?, position: Long)

    @Query("UPDATE bookmarks SET folderId = NULL WHERE folderId = :folderId")
    protected abstract suspend fun emptyFolder(folderId: Long)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    abstract suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE folders SET collapsed = :collapsed WHERE id = :id")
    abstract suspend fun setCollapsed(id: Long, collapsed: Boolean)

    @Query("UPDATE bookmarks SET position = :position WHERE id = :id AND profileId = :profileId")
    protected abstract suspend fun setPosition(profileId: Long, id: Long, position: Long)

    @Query("UPDATE folders SET position = :position WHERE id = :id AND profileId = :profileId")
    protected abstract suspend fun setFolderPosition(profileId: Long, id: Long, position: Long)

    /** The next place in [profileId]'s order, after every bookmark and Folder it has. */
    @Query(
        "SELECT 1 + MAX(" +
            "COALESCE((SELECT MAX(position) FROM bookmarks WHERE profileId = :profileId), 0), " +
            "COALESCE((SELECT MAX(position) FROM folders WHERE profileId = :profileId), 0))",
    )
    protected abstract suspend fun nextPosition(profileId: Long): Long

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE profileId = :profileId AND url = :url)")
    protected abstract suspend fun has(profileId: Long, url: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean

    /**
     * Bookmarks the address at the end of the Profile's top level, unless the Profile already has
     * it, or is gone. Returns the bookmark's id either way, or null for a Profile that is gone.
     */
    @Transaction
    open suspend fun add(bookmark: BookmarkEntity): Long? {
        if (!profileIsLive(bookmark.profileId)) return null
        find(bookmark.profileId, bookmark.url)?.let { return it.id }
        return insertRow(bookmark.copy(position = nextPosition(bookmark.profileId)))
    }

    /** Removes the bookmark. A Tab that showed it as a Pinned Tab stays open as an ordinary Tab. */
    @Transaction
    open suspend fun delete(id: Long) {
        unpinTabs(id)
        deleteRow(id)
    }

    @Transaction
    open suspend fun remove(profileId: Long, url: String) {
        find(profileId, url)?.let { delete(it.id) }
    }

    /**
     * Removes the Profile's bookmark of that address if it has one, and adds it otherwise, decided
     * by what is in the table now. A newly added bookmark takes the Tab [pinTabId], if that is a
     * Tab of the same Profile, as its Pinned Tab, in the same step: two quick toggles can't leave
     * the second one's removal undone by the first one's pinning. Returns whether the address is
     * bookmarked afterwards.
     */
    @Transaction
    open suspend fun toggle(bookmark: BookmarkEntity, pinTabId: Long? = null): Boolean {
        if (has(bookmark.profileId, bookmark.url)) {
            remove(bookmark.profileId, bookmark.url)
            return false
        }
        val id = add(bookmark) ?: return false
        if (pinTabId != null && tab(pinTabId)?.profileId == bookmark.profileId) setTabBookmark(pinTabId, id)
        return true
    }

    /**
     * Pins the Tab [tabId]: bookmarks [bookmark]'s address in the Tab's Profile, unless it is
     * bookmarked already, and makes the Tab that bookmark's Pinned Tab. A bookmark has one Pinned
     * Tab, so a Tab that showed it before becomes an ordinary Tab, and so does this Tab for a
     * bookmark it showed before. Returns the bookmark's id, or null when the Tab is gone.
     */
    @Transaction
    open suspend fun pin(tabId: Long, bookmark: BookmarkEntity): Long? {
        val tab = tab(tabId) ?: return null
        val id = add(bookmark.copy(profileId = tab.profileId)) ?: return null
        unpinTabs(id)
        setTabBookmark(tabId, id)
        return id
    }

    /** Moves the bookmark into [folderId], or to the top level with null, at the end. */
    @Transaction
    open suspend fun move(id: Long, folderId: Long?) {
        val bookmark = get(id) ?: return
        // A Folder of another Profile, or one that is gone, takes nothing.
        if (folderId != null && folder(folderId)?.profileId != bookmark.profileId) return
        place(id, folderId, nextPosition(bookmark.profileId))
    }

    /** Adds an empty Folder at the end of the Profile's top level. Null for a Profile that is gone. */
    @Transaction
    open suspend fun addFolder(profileId: Long, name: String, createdAt: Long): Long? {
        if (!profileIsLive(profileId)) return null
        return insertFolder(FolderEntity(profileId = profileId, name = name, position = nextPosition(profileId), createdAt = createdAt))
    }

    /** Deletes the Folder. Its bookmarks stay, at the top level. */
    @Transaction
    open suspend fun deleteFolder(id: Long) {
        emptyFolder(id)
        deleteFolderRow(id)
    }

    /**
     * Gives the listed bookmarks and Folders of [profileId] new places, in the order given: the
     * new order of the Profile's top level, or of one Folder's bookmarks.
     */
    @Transaction
    open suspend fun reorder(profileId: Long, order: List<Placed>) {
        var position = nextPosition(profileId)
        order.forEach { item ->
            if (item.folder) setFolderPosition(profileId, item.id, position++) else setPosition(profileId, item.id, position++)
        }
    }
}

/** A bookmark or a Folder ([folder]) by its id: an entry of a list being put in a new order. */
data class Placed(val id: Long, val folder: Boolean)

/**
 * One row per download a Profile started. The file itself is with Android's download service,
 * which [downloadId] names; this row is the Profile's own list of what it downloaded.
 */
@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId", "startedAt")],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val downloadId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val startedAt: Long,
    /** A file Okapi saved itself, a page's own, at this address in the Downloads folder; [downloadId] is then 0. */
    @ColumnInfo(defaultValue = "NULL")
    val fileUri: String? = null,
)

@Dao
abstract class DownloadDao {
    /** A Profile's downloads, newest first. */
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY startedAt DESC, id DESC")
    abstract fun observe(profileId: Long): Flow<List<DownloadEntity>>

    @Insert
    protected abstract suspend fun insertRow(download: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Lists the download in its Profile. Returns false, and lists nothing, when the Profile is gone. */
    @Transaction
    open suspend fun add(download: DownloadEntity): Boolean {
        if (!profileIsLive(download.profileId)) return false
        insertRow(download)
        return true
    }

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean
}

/**
 * One row per Site Rule: Incoming Links whose address [pattern] covers open in [profileId]
 * (SPEC, "Site Rules"). A pattern has one rule, so it never names two Profiles.
 */
@Entity(
    tableName = "site_rules",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["pattern"], unique = true), Index("profileId")],
)
data class SiteRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val profileId: Long,
    val createdAt: Long,
)

@Dao
abstract class SiteRuleDao {
    /** Every rule, oldest first. */
    @Query("SELECT * FROM site_rules ORDER BY id")
    abstract fun observe(): Flow<List<SiteRuleEntity>>

    @Query("SELECT * FROM site_rules ORDER BY id")
    abstract suspend fun all(): List<SiteRuleEntity>

    @Insert
    protected abstract suspend fun insertRow(rule: SiteRuleEntity): Long

    @Query("UPDATE site_rules SET pattern = :pattern, profileId = :profileId WHERE id = :id")
    protected abstract suspend fun updateRow(id: Long, pattern: String, profileId: Long): Int

    @Query("DELETE FROM site_rules WHERE pattern = :pattern AND id != :exceptId")
    protected abstract suspend fun deleteOthersWith(pattern: String, exceptId: Long)

    @Query("DELETE FROM site_rules WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean

    /**
     * Saves a rule: the one with [id] is changed, and without an [id], or when that rule is gone,
     * a new one is added. Another rule with the same pattern is replaced. Returns the rule's id,
     * or null, with nothing saved, when the Profile is gone.
     */
    @Transaction
    open suspend fun save(id: Long?, pattern: String, profileId: Long, createdAt: Long): Long? {
        if (!profileIsLive(profileId)) return null
        deleteOthersWith(pattern, exceptId = id ?: 0)
        if (id != null && updateRow(id, pattern, profileId) > 0) return id
        return insertRow(SiteRuleEntity(pattern = pattern, profileId = profileId, createdAt = createdAt))
    }
}

/**
 * One row per thing a Profile remembers about a site (SPEC, "Site permissions and Site
 * settings"): [setting] is a `SiteSetting` key, and [enabled] says allowed (or desktop version
 * on) or blocked. A setting without a row is unset.
 */
@Entity(
    tableName = "site_settings",
    primaryKeys = ["profileId", "origin", "setting"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SiteSettingEntity(
    val profileId: Long,
    val origin: String,
    val setting: String,
    val enabled: Boolean,
)

@Dao
abstract class SiteSettingDao {
    /** A Profile's settings, by site in alphabetical order. */
    @Query("SELECT * FROM site_settings WHERE profileId = :profileId ORDER BY origin, setting")
    abstract fun observe(profileId: Long): Flow<List<SiteSettingEntity>>

    /** Every Profile's sites that have [setting] on, such as all desktop sites. */
    @Query("SELECT * FROM site_settings WHERE setting = :setting AND enabled = 1")
    abstract fun observeOn(setting: String): Flow<List<SiteSettingEntity>>

    @Query("SELECT enabled FROM site_settings WHERE profileId = :profileId AND origin = :origin AND setting = :setting")
    abstract suspend fun get(profileId: Long, origin: String, setting: String): Boolean?

    @Upsert
    protected abstract suspend fun upsertRow(row: SiteSettingEntity)

    @Query("DELETE FROM site_settings WHERE profileId = :profileId AND origin = :origin AND setting = :setting")
    abstract suspend fun delete(profileId: Long, origin: String, setting: String)

    @Query("DELETE FROM site_settings WHERE profileId = :profileId")
    abstract suspend fun deleteAll(profileId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean

    /** Keeps [row], replacing what the site had for that setting. Nothing is kept for a deleted Profile. */
    @Transaction
    open suspend fun set(row: SiteSettingEntity) {
        if (profileIsLive(row.profileId)) upsertRow(row)
    }
}

@Database(
    entities = [
        ProfileEntity::class, TabEntity::class, HistoryEntity::class, BookmarkEntity::class, DownloadEntity::class,
        SiteRuleEntity::class, FolderEntity::class, SiteSettingEntity::class,
    ],
    version = 9,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
    ],
)
abstract class JrnyDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun tabs(): TabDao
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun downloads(): DownloadDao
    abstract fun siteRules(): SiteRuleDao
    abstract fun siteSettings(): SiteSettingDao

    companion object {
        fun open(context: Context): JrnyDatabase =
            Room.databaseBuilder(context, JrnyDatabase::class.java, "jrny.db").build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): JrnyDatabase =
            Room.inMemoryDatabaseBuilder(context, JrnyDatabase::class.java).build()
    }
}
