package app.jrny.browser

import android.app.Application
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.download.Downloader
import app.jrny.browser.icons.SiteIconStore
import java.io.File
import app.jrny.browser.download.SystemDownloader
import app.jrny.browser.engine.Engine
import app.jrny.browser.profile.ProfileCleanup
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.rules.LinkWrapperRepository
import app.jrny.browser.settings.HttpSearchSuggestions
import app.jrny.browser.settings.SettingsRepository
import app.jrny.browser.tab.PrivateTabs
import app.jrny.browser.ui.clearPageFiles
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class JrnyApplication : Application() {

    val database: JrnyDatabase by lazy { JrnyDatabase.open(this) }
    val repositories: Repositories by lazy { Repositories(database) }
    val downloader: Downloader by lazy { SystemDownloader(this) }

    /** Private Tabs live in memory for as long as the process does, across activity restarts. */
    val privateTabs = PrivateTabs()

    val services: BrowserServices by lazy {
        BrowserServices(
            repositories = repositories,
            downloader = downloader,
            privateTabs = privateTabs,
            settings = SettingsRepository(getSharedPreferences("settings", MODE_PRIVATE)),
            linkWrappers = LinkWrapperRepository(getSharedPreferences("settings", MODE_PRIVATE)),
            searchSuggestions = HttpSearchSuggestions(),
            siteIcons = SiteIconStore(File(filesDir, "site-icons")),
        )
    }

    /**
     * Before any activity, in the app's own process: starts the browser engine and creates the
     * first Profile on first launch. GeckoView's content processes start the Application too, and
     * do none of this. The database is tiny, so blocking here costs a few milliseconds. The web
     * data of Profiles deleted since the last cold start is wiped once the engine has started.
     */
    private companion object {
        /** How long a Profile's wipe gets to finish before the Profile is forgotten. A wipe takes well under a second. */
        const val WIPE_SETTLE_MS = 10_000L
    }

    override fun onCreate() {
        super.onCreate()
        if (!Engine.isMainProcess(this)) return
        // Photos and videos made for pages' file fields, and images shared or copied, last until the
        // next start (SPEC, "File upload" and "Link and image menu").
        clearPageFiles(this)
        Engine.runtime(this)
        runBlocking { repositories.profiles.ensureFirstProfile(getString(R.string.first_profile_name)) }
        MainScope().launch {
            ProfileCleanup(repositories.profiles) { id ->
                suspendCancellableCoroutine { handed -> WebProfile.of(id).clear(this@JrnyApplication) { handed.resume(Unit) } }
                // GeckoView gives no word when the wipe is done. The Profile is forgotten only
                // once it has had time to finish; if the app ends sooner, the next start wipes again.
                delay(WIPE_SETTLE_MS)
            }.run()
        }
    }
}
