package app.jrny.browser.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
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

    @Query("DELETE FROM profiles WHERE id = :id AND pendingDeletion = 1")
    abstract suspend fun purge(id: Long)

    /** Inserts [profile] as the Active Profile and returns its id. */
    @Transaction
    open suspend fun insertActive(profile: ProfileEntity): Long =
        insertRow(profile.copy(activation = maxActivation() + 1))

    @Transaction
    open suspend fun activate(id: Long) = setActivation(id, maxActivation() + 1)

    /**
     * Marks [id] for deletion unless it's the last live Profile, and closes its Tabs, so their
     * addresses don't outlive it on disk. Returns whether it did.
     */
    @Transaction
    open suspend fun markPendingUnlessLast(id: Long): Boolean {
        if (countLive() <= 1) return false
        markPending(id)
        deleteTabs(id)
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

@Database(
    entities = [ProfileEntity::class, TabEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class JrnyDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun tabs(): TabDao

    companion object {
        fun open(context: Context): JrnyDatabase =
            Room.databaseBuilder(context, JrnyDatabase::class.java, "jrny.db").build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): JrnyDatabase =
            Room.inMemoryDatabaseBuilder(context, JrnyDatabase::class.java).build()
    }
}
