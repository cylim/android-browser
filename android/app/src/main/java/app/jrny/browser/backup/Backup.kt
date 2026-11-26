package app.jrny.browser.backup

import kotlinx.serialization.Serializable

/**
 * What a backup file holds (SPEC, "Backup"): the app's settings and every live Profile with its
 * look, bookmarks and Folders, history, open Tabs, Site Rules and Site settings. Never web data:
 * no cookies, site storage or sign-ins. Private Tabs, downloads, recently closed Tabs and Home
 * Screen Shortcuts stay on the device too.
 *
 * Read with unknown fields ignored, so a later version of the same [version] can add fields.
 */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** When the backup was made, in milliseconds since the epoch. */
    val createdAt: Long,
    val settings: BackupSettings = BackupSettings(),
    /** In the order they were created; at most one is [BackupProfile.active]. */
    val profiles: List<BackupProfile>,
) {
    companion object {
        const val FORMAT = "okapi-backup"
        const val VERSION = 1
    }
}

/** The app's settings, by name. A value this version doesn't know falls back to the default. */
@Serializable
data class BackupSettings(
    val searchEngine: String? = null,
    val suggestions: Boolean? = null,
    val safeBrowsing: Boolean? = null,
    val theme: String? = null,
    val compactTabs: Boolean? = null,
    val textZoom: Int? = null,
    val darkenPages: Boolean? = null,
    val pullToRefresh: Boolean? = null,
)

@Serializable
data class BackupProfile(
    val name: String,
    /** A `ProfileColor` name. */
    val color: String,
    /** A `ProfileIcon` name. */
    val icon: String,
    /** Shown in the icon's place when set. */
    val emoji: String? = null,
    /** A `ProfileTint` name. */
    val tint: String = "Medium",
    /** Whether it was the Active Profile. */
    val active: Boolean = false,
    val folders: List<BackupFolder> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    /** Oldest first. */
    val history: List<BackupVisit> = emptyList(),
    /** In the Tab list's order. */
    val tabs: List<BackupTab> = emptyList(),
    /** The Site Rules that send links to this Profile. */
    val siteRules: List<BackupSiteRule> = emptyList(),
    val siteSettings: List<BackupSiteSetting> = emptyList(),
)

/**
 * A Folder. [position] orders it among the Profile's top-level bookmarks and Folders; Folders and
 * bookmarks share one counter, as in the database.
 */
@Serializable
data class BackupFolder(val name: String, val position: Long = 0, val collapsed: Boolean = false, val createdAt: Long = 0)

/** A bookmark, at the top level, or in the Folder at index [folder] of its Profile's folders. */
@Serializable
data class BackupBookmark(val url: String, val title: String = "", val createdAt: Long = 0, val folder: Int? = null, val position: Long = 0)

@Serializable
data class BackupVisit(val url: String, val title: String = "", val visitedAt: Long)

/**
 * An open Tab: its address, or none for a Tab that shows the Start Page. [pinned] is the address
 * of the bookmark it is the Pinned Tab of. [selected] marks the Tab the Profile showed.
 */
@Serializable
data class BackupTab(val url: String? = null, val title: String = "", val scrollY: Int = 0, val selected: Boolean = false, val pinned: String? = null)

@Serializable
data class BackupSiteRule(val pattern: String, val createdAt: Long = 0)

/** A Site setting: [setting] is a `SiteSetting` key, [enabled] allowed or blocked. */
@Serializable
data class BackupSiteSetting(val origin: String, val setting: String, val enabled: Boolean)

/** What a backup holds, for the person to see before importing it. */
data class BackupSummary(val profiles: List<String>, val bookmarks: Int, val visits: Int, val tabs: Int)

val Backup.summary: BackupSummary
    get() = BackupSummary(
        profiles = profiles.map { it.name },
        bookmarks = profiles.sumOf { it.bookmarks.size },
        visits = profiles.sumOf { it.history.size },
        tabs = profiles.sumOf { profile -> profile.tabs.count { it.url != null } },
    )
