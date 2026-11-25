package app.jrny.browser.backup

import app.jrny.browser.address.WebAddress
import app.jrny.browser.data.BackupDao
import app.jrny.browser.data.BookmarkEntity
import app.jrny.browser.data.FolderEntity
import app.jrny.browser.data.HistoryEntity
import app.jrny.browser.data.ProfileEntity
import app.jrny.browser.data.ProfileRows
import app.jrny.browser.data.SiteRuleEntity
import app.jrny.browser.data.SiteSettingEntity
import app.jrny.browser.data.TabEntity
import app.jrny.browser.library.FolderName
import app.jrny.browser.library.HistoryRepository
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileEmoji
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileName
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.profile.ProfileTint
import app.jrny.browser.rules.SitePattern
import app.jrny.browser.settings.SearchEngine
import app.jrny.browser.settings.Settings
import app.jrny.browser.settings.SettingsRepository
import app.jrny.browser.settings.ThemeChoice
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting

/** What importing a backup does with what is already here (SPEC, "Backup"). */
enum class ImportMode {
    /** Adds what is missing. Nothing here is changed or removed. */
    Merge,

    /** Deletes every Profile here and puts the backup's in their place, with its settings. */
    Replace,
}

/** What an import took away: the Profiles it replaced, whose web data must go, and their shortcuts. */
data class Imported(val replaced: List<ProfileId>, val shortcuts: List<String>)

/**
 * Makes a [Backup] of what the app keeps, and brings one back (SPEC, "Backup"). Everything read
 * from a backup is checked as if a person had typed it: what Okapi wouldn't have stored itself,
 * such as an address that isn't a web address, is left out.
 */
class Backups(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun snapshot(): Backup {
        val rows = dao.snapshot()
        val active = rows.maxByOrNull { it.profile.activation }?.profile?.id
        return Backup(createdAt = now(), settings = settings.settings.value.toBackup(), profiles = rows.map { it.toBackup(active) })
    }

    suspend fun restore(backup: Backup, mode: ImportMode): Imported {
        val incoming = backup.profiles.withOneActive().map { it.toRows() }
        val outcome = dao.restore(incoming, replace = mode == ImportMode.Replace, keepVisits = HistoryRepository.MAX_VISITS)
        if (mode == ImportMode.Replace) {
            val restored = backup.settings.toSettings()
            settings.update { restored }
        }
        return Imported(outcome.replaced.map { ProfileId(it.toString()) }, outcome.shortcuts)
    }

    private fun ProfileRows.toBackup(active: Long?): BackupProfile {
        val stored = profile.icon
        val emoji = stored.takeIf { it.startsWith(ProfileRepository.EMOJI) }?.removePrefix(ProfileRepository.EMOJI)
        val folderIndex = folders.mapIndexed { index, folder -> folder.id to index }.toMap()
        val bookmarkUrl = bookmarks.associate { it.id to it.url }
        val selected = tabs.maxByOrNull { it.lastUsed }?.id
        return BackupProfile(
            name = profile.name,
            color = profile.color,
            icon = if (emoji != null) ProfileIcon.Person.name else stored,
            emoji = emoji,
            tint = profile.tint,
            active = profile.id == active,
            folders = folders.map { BackupFolder(it.name, it.position, it.collapsed, it.createdAt) },
            bookmarks = bookmarks.map { BackupBookmark(it.url, it.title, it.createdAt, it.folderId?.let(folderIndex::get), it.position) },
            history = history.map { BackupVisit(it.url, it.title, it.visitedAt) },
            tabs = tabs.map { BackupTab(it.url, it.title, it.scrollY, selected = it.id == selected, pinned = it.bookmarkId?.let(bookmarkUrl::get)) },
            siteRules = siteRules.map { BackupSiteRule(it.pattern, it.createdAt) },
            siteSettings = siteSettings.map { BackupSiteSetting(it.origin, it.setting, it.enabled) },
        )
    }

    /** At most one Profile is active; with none, the first is. */
    private fun List<BackupProfile>.withOneActive(): List<BackupProfile> {
        val active = indexOfFirst { it.active }.takeIf { it >= 0 } ?: 0
        return mapIndexed { index, profile -> profile.copy(active = index == active) }
    }

    /** The backup's Profile as rows to restore, with ids local to the backup (see [ProfileRows]). */
    private fun BackupProfile.toRows(): ProfileRows {
        val emoji = emoji?.let(ProfileEmoji::clean)
        val icon = if (emoji != null) ProfileRepository.EMOJI + emoji else enumName<ProfileIcon>(icon) ?: ProfileIcon.Person.name
        val profile = ProfileEntity(
            name = ProfileName.clean(name) ?: ProfileName.clean(name.trim().take(ProfileName.MAX_LENGTH)) ?: FALLBACK_NAME,
            color = enumName<ProfileColor>(color) ?: ProfileColor.Blue.name,
            icon = icon,
            tint = enumName<ProfileTint>(tint) ?: ProfileTint.Medium.name,
            activation = if (active) 1 else 0,
        )
        // Local ids start at 1: 0 would read as "none".
        val folders = folders.mapIndexed { index, folder ->
            FolderEntity(
                id = index + 1L,
                profileId = 0,
                name = FolderName.clean(folder.name) ?: FolderName.clean(folder.name.trim().take(FolderName.MAX_LENGTH)) ?: FALLBACK_FOLDER,
                position = folder.position,
                collapsed = folder.collapsed,
                createdAt = folder.createdAt,
            )
        }
        val bookmarks = bookmarks.filter { isWeb(it.url) }.distinctBy { it.url }.mapIndexed { index, bookmark ->
            BookmarkEntity(
                id = index + 1L,
                profileId = 0,
                url = bookmark.url,
                title = bookmark.title.take(MAX_TITLE),
                createdAt = bookmark.createdAt,
                folderId = bookmark.folder?.takeIf { it in folders.indices }?.let { it + 1L },
                position = bookmark.position,
            )
        }
        val bookmarkIds = bookmarks.associate { it.url to it.id }
        val history = history.filter { isWeb(it.url) }
            .sortedBy { it.visitedAt }
            .takeLast(HistoryRepository.MAX_VISITS)
            .map { HistoryEntity(profileId = 0, url = it.url, title = it.title.take(MAX_TITLE), visitedAt = it.visitedAt) }
        val tabs = tabs.filter { it.url == null || isWeb(it.url) }.let { kept ->
            val selected = kept.indexOfFirst { it.selected }
            kept.mapIndexed { index, tab ->
                TabEntity(
                    profileId = 0,
                    url = tab.url,
                    title = tab.title.take(MAX_TITLE),
                    scrollY = tab.scrollY.coerceAtLeast(0),
                    lastUsed = if (index == selected) 1 else 0,
                    bookmarkId = tab.pinned?.let(bookmarkIds::get),
                )
            }
        }
        val siteRules = siteRules.mapNotNull { rule ->
            SitePattern.parse(rule.pattern)?.let { SiteRuleEntity(pattern = it.text, profileId = 0, createdAt = rule.createdAt) }
        }.distinctBy { it.pattern }
        val siteSettings = siteSettings.mapNotNull { row ->
            val origin = SiteOrigin.of(row.origin) ?: return@mapNotNull null
            val setting = SiteSetting.fromKey(row.setting) ?: return@mapNotNull null
            SiteSettingEntity(profileId = 0, origin = origin.text, setting = setting.key, enabled = row.enabled)
        }.distinctBy { it.origin to it.setting }
        return ProfileRows(profile, folders, bookmarks, history, tabs, siteRules, siteSettings)
    }

    private fun isWeb(url: String): Boolean = url.length <= MAX_URL && WebAddress.isWebAddress(url)

    private inline fun <reified T : Enum<T>> enumName(name: String): String? = enumValues<T>().firstOrNull { it.name == name }?.name

    private fun Settings.toBackup() = BackupSettings(
        searchEngine = searchEngine.name,
        suggestions = suggestions,
        safeBrowsing = safeBrowsing,
        theme = theme.name,
        compactTabs = compactTabs,
        textZoom = textZoom,
        darkenPages = darkenPages,
        pullToRefresh = pullToRefresh,
    )

    private fun BackupSettings.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            searchEngine = SearchEngine.entries.firstOrNull { it.name == searchEngine } ?: defaults.searchEngine,
            suggestions = suggestions ?: defaults.suggestions,
            safeBrowsing = safeBrowsing ?: defaults.safeBrowsing,
            theme = ThemeChoice.entries.firstOrNull { it.name == theme } ?: defaults.theme,
            compactTabs = compactTabs ?: defaults.compactTabs,
            textZoom = (textZoom ?: defaults.textZoom).coerceIn(Settings.MIN_TEXT_ZOOM, Settings.MAX_TEXT_ZOOM),
            darkenPages = darkenPages ?: defaults.darkenPages,
            pullToRefresh = pullToRefresh ?: defaults.pullToRefresh,
        )
    }

    private companion object {
        /** Longer addresses and titles than any page gives aren't kept. */
        const val MAX_URL = 64 * 1024
        const val MAX_TITLE = 1_000
        const val FALLBACK_NAME = "Profile"
        const val FALLBACK_FOLDER = "Folder"
    }
}
