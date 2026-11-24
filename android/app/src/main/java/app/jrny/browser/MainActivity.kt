package app.jrny.browser

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.jrny.browser.address.WebAddress
import app.jrny.browser.shortcut.HomeShortcuts
import android.widget.Toast
import kotlinx.coroutines.launch
import app.jrny.browser.ui.BrowserScreen
import app.jrny.browser.ui.SystemBarsFollowTheme
import app.jrny.browser.ui.theme.JrnyTheme
import app.jrny.browser.ui.theme.isDark

class MainActivity : ComponentActivity() {

    /** The Profiles and their Tabs. Visible to tests, which drive and inspect it. */
    @VisibleForTesting
    internal var browser: BrowserState? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Tabs come back from the database, after a recreation or after Android killed the app.
        val app = application as JrnyApplication
        val browser = BrowserState(this, app.services, lifecycleScope)
        this.browser = browser
        // Not after a restore: the link's Tab is already saved, and would open a second time.
        if (savedInstanceState == null) {
            openIncomingLink(intent)
            openShortcut(intent)
        }
        setContent {
            // The theme is a setting: the device's, or always light or dark.
            val settings by browser.settings.collectAsState()
            SystemBarsFollowTheme(settings.theme.isDark())
            JrnyTheme(settings.theme) {
                BrowserScreen(browser, onSetDefaultBrowser = if (canBecomeDefaultBrowser) ::askToBeDefaultBrowser else null)
            }
        }
    }

    /** The activity is singleTask, so a link from another app arrives here while it's open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openIncomingLink(intent)
        openShortcut(intent)
    }

    /**
     * Opens a Home Screen Shortcut's page in its own Profile (SPEC, "Add to Home screen"). The
     * intent names only the shortcut's random id; the address and the Profile are looked up here.
     */
    private fun openShortcut(intent: Intent?) {
        if (intent?.action != HomeShortcuts.ACTION_OPEN) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val id = intent.getStringExtra(HomeShortcuts.EXTRA_ID) ?: return
        val browser = browser ?: return
        lifecycleScope.launch {
            if (!browser.openShortcut(id)) {
                Toast.makeText(this@MainActivity, R.string.shortcut_profile_deleted, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Dark mode doesn't recreate the activity (configChanges), so the browser hears of it here. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pages hear of rotation and resizing through the engine (screen.orientation).
        app.jrny.browser.engine.Engine.runtime(this).configurationChanged(newConfig)
        browser?.deviceThemeChanged()
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultBrowser()
    }

    /**
     * Opens an Incoming Link as a new Tab in the Active Profile (SPEC, "Incoming Links"). The
     * intent comes from another app, so only its address is used, and only a web address.
     * Reopening the app from Recents redelivers the old intent, which must not open the link again.
     */
    private fun openIncomingLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val address = intent.data ?: return
        if (!WebAddress.isWebScheme(address.scheme)) return
        browser?.openIncomingLink(address.toString())
    }

    /** True while Android would let the person make Okapi the default browser, and it isn't. */
    private var canBecomeDefaultBrowser by mutableStateOf(false)

    private val defaultBrowserRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDefaultBrowser()
    }

    private fun refreshDefaultBrowser() {
        val roles = getSystemService(RoleManager::class.java)
        canBecomeDefaultBrowser = roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    /** Shows Android's own "set as default browser" dialog. */
    private fun askToBeDefaultBrowser() {
        defaultBrowserRequest.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_BROWSER))
    }

    override fun onStop() {
        super.onStop()
        browser?.flush()
    }

    override fun onDestroy() {
        // Finishing means the person closed the window (it left Recents, or the task ended), not
        // that Android is rebuilding it. Private Tabs end with it (SPEC, "Private Tabs").
        if (isFinishing) browser?.closeAllPrivateTabs()
        browser?.destroy()
        browser = null
        super.onDestroy()
    }
}
