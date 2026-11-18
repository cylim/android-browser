package app.jrny.browser

import android.annotation.SuppressLint
import android.app.Application
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.download.Downloader
import app.jrny.browser.icons.SiteIconStore
import java.io.File
import app.jrny.browser.download.SystemDownloader
import app.jrny.browser.profile.ProfileCleanup
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.settings.HttpSearchSuggestions
import app.jrny.browser.settings.SettingsRepository
import app.jrny.browser.tab.PrivateTabs
import app.jrny.browser.ui.clearCaptures
import kotlinx.coroutines.runBlocking

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
            searchSuggestions = HttpSearchSuggestions(),
            siteIcons = SiteIconStore(File(filesDir, "site-icons")),
        )
    }

    /**
     * Before any activity, and so before any WebView loads a Profile: creates the first Profile on
     * first launch, and wipes the web data of Profiles deleted since the last cold start. WebView
     * can only delete a Profile that this process hasn't loaded yet (ADR 0001). The database is
     * tiny, so blocking here costs a few milliseconds.
     */
    @SuppressLint("RequiresFeature") // Checked first.
    override fun onCreate() {
        super.onCreate()
        // Photos and videos made for pages' file fields last until the next start (SPEC, "File upload").
        clearCaptures(this)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        // Whatever Private Tabs left on disk goes before anything loads (SPEC, "Private Tabs").
        WebProfile.wipePrivateStores()
        runBlocking {
            repositories.profiles.ensureFirstProfile(getString(R.string.first_profile_name))
            ProfileCleanup(repositories.profiles) { id ->
                // False only when there's no such store on disk, which is fine: nothing to wipe.
                ProfileStore.getInstance().deleteProfile(WebProfile.of(id).name)
            }.run()
        }
    }
}
