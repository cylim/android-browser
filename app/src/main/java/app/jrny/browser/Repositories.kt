package app.jrny.browser

import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.download.DownloadRepository
import app.jrny.browser.library.BookmarkRepository
import app.jrny.browser.library.HistoryRepository
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.tab.TabRepository

/** Everything the app keeps in its database, behind one repository per kind. */
class Repositories(database: JrnyDatabase) {
    val profiles = ProfileRepository(database.profiles())
    val tabs = TabRepository(database.tabs())
    val history = HistoryRepository(database.history())
    val bookmarks = BookmarkRepository(database.bookmarks())
    val downloads = DownloadRepository(database.downloads())
}
