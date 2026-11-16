package app.jrny.browser.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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

    @Query("DELETE FROM profiles WHERE id = :id AND pendingDeletion = 1")
    abstract suspend fun purge(id: Long)

    /** Inserts [profile] as the Active Profile and returns its id. */
    @Transaction
    open suspend fun insertActive(profile: ProfileEntity): Long =
        insertRow(profile.copy(activation = maxActivation() + 1))

    @Transaction
    open suspend fun activate(id: Long) = setActivation(id, maxActivation() + 1)

    /** Marks [id] for deletion unless it's the last live Profile. Returns whether it did. */
    @Transaction
    open suspend fun markPendingUnlessLast(id: Long): Boolean {
        if (countLive() <= 1) return false
        markPending(id)
        return true
    }
}

@Database(entities = [ProfileEntity::class], version = 1, exportSchema = true)
abstract class JrnyDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao

    companion object {
        fun open(context: Context): JrnyDatabase =
            Room.databaseBuilder(context, JrnyDatabase::class.java, "jrny.db").build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): JrnyDatabase =
            Room.inMemoryDatabaseBuilder(context, JrnyDatabase::class.java).build()
    }
}
