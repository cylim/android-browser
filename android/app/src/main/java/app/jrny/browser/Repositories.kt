package app.jrny.browser

import app.jrny.browser.backup.Backups
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.download.DownloadRepository
import app.jrny.browser.download.Downloader
import app.jrny.browser.icons.SiteIconStore
import app.jrny.browser.settings.SearchSuggestions
import app.jrny.browser.settings.SettingsRepository
import app.jrny.browser.tab.PrivateTabs
import app.jrny.browser.library.BookmarkRepository
import app.jrny.browser.library.HistoryRepository
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.rules.LinkWrapperRepository
import app.jrny.browser.rules.SiteRuleRepository
import app.jrny.browser.site.SiteSettingsRepository
import app.jrny.browser.tab.TabRepository

/** Everything the app keeps in its database, behind one repository per kind. */
class Repositories(database: JrnyDatabase) {
    val profiles = ProfileRepository(database.profiles())
    val tabs = TabRepository(database.tabs())
    val history = HistoryRepository(database.history())
    val bookmarks = BookmarkRepository(database.bookmarks())
    val downloads = DownloadRepository(database.downloads())
    val siteRules = SiteRuleRepository(database.siteRules())
    val siteSettings = SiteSettingsRepository(database.siteSettings())
    val closedTabs = app.jrny.browser.tab.ClosedTabRepository(database.closedTabs())
    val shortcuts = app.jrny.browser.shortcut.ShortcutRepository(database.shortcuts())

    /** Everything at once, for backups (SPEC, "Backup"). */
    val backup = database.backup()
}

/** What a [BrowserState] works with besides the screen: what is stored, and the services around it. */
class BrowserServices(
    val repositories: Repositories,
    val downloader: Downloader,
    val privateTabs: PrivateTabs,
    val settings: SettingsRepository,
    /** The Link Wrappers that Site Rules see through, kept with the settings. */
    val linkWrappers: LinkWrapperRepository,
    val searchSuggestions: SearchSuggestions,
    val siteIcons: SiteIconStore,
) {
    /** Makes and restores backups of what [repositories], [settings] and [linkWrappers] keep. */
    val backups = Backups(repositories.backup, settings, linkWrappers)
}
