package app.jrny.browser.data

import android.content.Context
import androidx.room.AutoMigration
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

    @Query("UPDATE profiles SET name = :name, color = :color, icon = :icon WHERE id = :id AND pendingDeletion = 0")
    abstract suspend fun update(id: Long, name: String, color: String, icon: String)

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

    @Query("DELETE FROM profiles WHERE id = :id AND pendingDeletion = 1")
    abstract suspend fun purge(id: Long)

    /** Inserts [profile] as the Active Profile and returns its id. */
    @Transaction
    open suspend fun insertActive(profile: ProfileEntity): Long =
        insertRow(profile.copy(activation = maxActivation() + 1))

    @Transaction
    open suspend fun activate(id: Long) = setActivation(id, maxActivation() + 1)

    /**
     * Marks [id] for deletion unless it's the last live Profile, and removes its Tabs, history and
     * bookmarks at once, so no address from it outlives it on disk. Returns whether it did.
     */
    @Transaction
    open suspend fun markPendingUnlessLast(id: Long): Boolean {
        if (countLive() <= 1) return false
        markPending(id)
        deleteTabs(id)
        deleteHistory(id)
        deleteBookmarks(id)
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

/** One row per bookmarked address, kept per Profile. An address is bookmarked at most once in a Profile. */
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
)

@Dao
abstract class BookmarkDao {
    /** A Profile's bookmarks, newest first. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC, id DESC")
    abstract fun observe(profileId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRow(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId AND url = :url")
    abstract suspend fun remove(profileId: Long, url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Bookmarks the address unless the Profile already has it, or is gone. */
    @Transaction
    open suspend fun add(bookmark: BookmarkEntity) {
        if (profileIsLive(bookmark.profileId)) insertRow(bookmark)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId AND pendingDeletion = 0)")
    protected abstract suspend fun profileIsLive(profileId: Long): Boolean
}

@Database(
    entities = [ProfileEntity::class, TabEntity::class, HistoryEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class JrnyDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun tabs(): TabDao
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao

    companion object {
        fun open(context: Context): JrnyDatabase =
            Room.databaseBuilder(context, JrnyDatabase::class.java, "jrny.db").build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): JrnyDatabase =
            Room.inMemoryDatabaseBuilder(context, JrnyDatabase::class.java).build()
    }
}
