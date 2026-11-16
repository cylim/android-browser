package app.jrny.browser

import android.annotation.SuppressLint
import android.app.Application
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileCleanup
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.TabRepository
import kotlinx.coroutines.runBlocking

class JrnyApplication : Application() {

    val database: JrnyDatabase by lazy { JrnyDatabase.open(this) }
    val profiles: ProfileRepository by lazy { ProfileRepository(database.profiles()) }
    val tabs: TabRepository by lazy { TabRepository(database.tabs()) }

    /**
     * Before any activity, and so before any WebView loads a Profile: creates the first Profile on
     * first launch, and wipes the web data of Profiles deleted since the last cold start. WebView
     * can only delete a Profile that this process hasn't loaded yet (ADR 0001). The database is
     * tiny, so blocking here costs a few milliseconds.
     */
    @SuppressLint("RequiresFeature") // Checked first.
    override fun onCreate() {
        super.onCreate()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runBlocking {
            profiles.ensureFirstProfile(getString(R.string.first_profile_name))
            ProfileCleanup(profiles) { id ->
                // False only when there's no such store on disk, which is fine: nothing to wipe.
                ProfileStore.getInstance().deleteProfile(WebProfile.of(id).name)
            }.run()
        }
    }
}
