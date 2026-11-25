package app.jrny.browser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * One Profile's rows, as a backup holds them (SPEC, "Backup"). Read from the database, ids are
 * the database's own. Handed to [BackupDao.restore], ids are the backup's: a bookmark's
 * [BookmarkEntity.folderId] names one of [folders] by its id, a Tab's [TabEntity.bookmarkId] one
 * of [bookmarks], the Active Profile has an [ProfileEntity.activation] above 0, and the selected
 * Tab a [TabEntity.lastUsed] above 0. Every `profileId` is ignored.
 */
data class ProfileRows(
    val profile: ProfileEntity,
    val folders: List<FolderEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val tabs: List<TabEntity> = emptyList(),
    val siteRules: List<SiteRuleEntity> = emptyList(),
    val siteSettings: List<SiteSettingEntity> = emptyList(),
)

/** What [BackupDao.restore] took away: the Profiles it replaced, and their Home Screen Shortcuts. */
data class RestoreOutcome(val replaced: List<Long>, val shortcuts: List<String>)

/** Reads everything a backup holds, and writes a backup back, each in one transaction. */
@Dao
abstract class BackupDao(private val database: RoomDatabase) {

    private val jrny: JrnyDatabase get() = database as JrnyDatabase

    @Query("SELECT * FROM profiles WHERE pendingDeletion = 0 ORDER BY id")
    protected abstract suspend fun liveProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM folders WHERE profileId = :profileId ORDER BY position, id")
    protected abstract suspend fun folders(profileId: Long): List<FolderEntity>

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY position, id")
    protected abstract suspend fun bookmarks(profileId: Long): List<BookmarkEntity>

    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitedAt, id")
    protected abstract suspend fun history(profileId: Long): List<HistoryEntity>

    @Query("SELECT * FROM tabs WHERE profileId = :profileId ORDER BY id")
    protected abstract suspend fun tabs(profileId: Long): List<TabEntity>

    @Query("SELECT * FROM site_rules WHERE profileId = :profileId ORDER BY id")
    protected abstract suspend fun siteRules(profileId: Long): List<SiteRuleEntity>

    @Query("SELECT * FROM site_settings WHERE profileId = :profileId ORDER BY origin, setting")
    protected abstract suspend fun siteSettings(profileId: Long): List<SiteSettingEntity>

    /** Every live Profile and what it holds, read together. */
    @Transaction
    open suspend fun snapshot(): List<ProfileRows> = liveProfiles().map { profile ->
        ProfileRows(
            profile,
            folders(profile.id),
            bookmarks(profile.id),
            history(profile.id),
            tabs(profile.id),
            siteRules(profile.id),
            siteSettings(profile.id),
        )
    }

    @Insert
    protected abstract suspend fun insertProfile(profile: ProfileEntity): Long

    @Insert
    protected abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Insert
    protected abstract suspend fun insertVisit(visit: HistoryEntity): Long

    @Insert
    protected abstract suspend fun insertTab(tab: TabEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSiteRule(rule: SiteRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSiteSetting(setting: SiteSettingEntity): Long

    @Query("SELECT id FROM bookmarks WHERE profileId = :profileId AND url = :url")
    protected abstract suspend fun bookmarkOf(profileId: Long, url: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM tabs WHERE bookmarkId = :bookmarkId)")
    protected abstract suspend fun isOpen(bookmarkId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM history WHERE profileId = :profileId AND url = :url AND visitedAt = :visitedAt)")
    protected abstract suspend fun hasVisit(profileId: Long, url: String, visitedAt: Long): Boolean

    @Query(
        "DELETE FROM history WHERE profileId = :profileId AND id NOT IN " +
            "(SELECT id FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC, id DESC LIMIT :keep)",
    )
    protected abstract suspend fun trimHistory(profileId: Long, keep: Int)

    @Query("SELECT COALESCE(MAX(lastUsed), 0) FROM tabs")
    protected abstract suspend fun maxLastUsed(): Long

    @Query(
        "SELECT 1 + MAX(" +
            "COALESCE((SELECT MAX(position) FROM bookmarks WHERE profileId = :profileId), 0), " +
            "COALESCE((SELECT MAX(position) FROM folders WHERE profileId = :profileId), 0))",
    )
    protected abstract suspend fun nextPosition(profileId: Long): Long

    /**
     * Writes [incoming] in one step (SPEC, "Backup").
     *
     * To merge ([replace] false), each incoming Profile joins the live Profile of the same name,
     * ignoring case, one to one, and the rest are added. Only what is missing is added: a
     * bookmark or Folder the Profile has, by address or name, a visit at the same time, a Tab
     * with the same address, a Site Rule's pattern or a site's setting stays as it is here. The
     * Active Profile and every selected Tab stay too, and the added Tabs come last in use.
     *
     * To replace, every incoming Profile is added as a new one, the backup's Active Profile
     * becomes the Active Profile, and every Profile that was here is deleted as a person deletes
     * one: its rows go at once and its web data is wiped by the caller.
     *
     * A Profile keeps at most [keepVisits] visits. Returns what was replaced.
     */
    @Transaction
    open suspend fun restore(incoming: List<ProfileRows>, replace: Boolean, keepVisits: Int): RestoreOutcome {
        require(incoming.isNotEmpty()) { "A backup has a Profile" }
        val before = liveProfiles()
        val taken = mutableSetOf<Long>()
        var active: Long? = null
        val targets = incoming.map { rows ->
            val same = if (replace) null else before.firstOrNull { it.id !in taken && it.name.equals(rows.profile.name, ignoreCase = true) }
            val id = same?.id ?: insertProfile(rows.profile.copy(id = 0, activation = 0, pendingDeletion = false))
            taken += id
            if (rows.profile.activation > 0 && active == null) active = id
            Target(id, merged = same != null, rows)
        }
        targets.forEach { write(it, keepVisits) }
        val rules = targets.flatMap { target -> target.rows.siteRules.map { it.copy(id = 0, profileId = target.id) } }

        if (!replace) {
            rules.forEach { insertSiteRule(it) }
            return RestoreOutcome(emptyList(), emptyList())
        }
        val replaced = before.map { it.id }
        // A deleted Profile's shortcuts go with its rows, so read them first.
        val shortcuts = replaced.flatMap { jrny.shortcuts().idsOf(it) }
        jrny.profiles().activate(active ?: targets.first().id)
        replaced.forEach { jrny.profiles().markPendingUnlessLast(it) }
        // Only now: a pattern names one Profile, and the replaced Profiles' rules are gone.
        rules.forEach { insertSiteRule(it) }
        return RestoreOutcome(replaced, shortcuts)
    }

    private class Target(val id: Long, val merged: Boolean, val rows: ProfileRows)

    private suspend fun write(target: Target, keepVisits: Int) {
        val profileId = target.id
        val rows = target.rows

        // Folders and bookmarks share one order. The incoming ones keep theirs, after what the
        // Profile has.
        val base = nextPosition(profileId)
        val places = (rows.folders.map { Triple(true, it.id, it.position) } + rows.bookmarks.map { Triple(false, it.id, it.position) })
            .sortedBy { it.third }
            .mapIndexed { index, (folder, id, _) -> (folder to id) to base + index }
            .toMap()

        val existingFolders = if (target.merged) folders(profileId) else emptyList()
        val folderIds = mutableMapOf<Long, Long>()
        rows.folders.forEach { folder ->
            folderIds[folder.id] = existingFolders.firstOrNull { it.name.equals(folder.name, ignoreCase = true) }?.id
                ?: insertFolder(folder.copy(id = 0, profileId = profileId, position = places.getValue(true to folder.id)))
        }

        val bookmarkIds = mutableMapOf<Long, Long>()
        rows.bookmarks.forEach { bookmark ->
            val id = bookmarkOf(profileId, bookmark.url) ?: run {
                insertBookmark(
                    bookmark.copy(
                        id = 0,
                        profileId = profileId,
                        folderId = bookmark.folderId?.let(folderIds::get),
                        position = places.getValue(false to bookmark.id),
                    ),
                )
                bookmarkOf(profileId, bookmark.url)
            }
            if (id != null) bookmarkIds[bookmark.id] = id
        }

        rows.history.forEach { visit ->
            if (!target.merged || !hasVisit(profileId, visit.url, visit.visitedAt)) insertVisit(visit.copy(id = 0, profileId = profileId))
        }
        trimHistory(profileId, keepVisits)

        val open = if (target.merged) tabs(profileId).mapNotNull { it.url }.toMutableSet() else mutableSetOf()
        var selected: Long? = null
        rows.tabs.forEach { tab ->
            // An empty Tab adds nothing to a Profile that has Tabs.
            if (target.merged && (tab.url == null || tab.url in open)) return@forEach
            tab.url?.let(open::add)
            val pinned = tab.bookmarkId?.let(bookmarkIds::get)?.takeUnless { isOpen(it) }
            val id = insertTab(tab.copy(id = 0, profileId = profileId, lastUsed = 0, bookmarkId = pinned))
            if (!target.merged && tab.lastUsed > 0) selected = id
        }
        if (!target.merged) {
            selected?.let { jrny.tabs().select(it) }
            jrny.tabs().ensureOne(profileId)
        }

        rows.siteSettings.forEach { insertSiteSetting(it.copy(profileId = profileId)) }
    }
}
