package app.jrny.browser

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewFeature
import androidx.lifecycle.lifecycleScope
import app.jrny.browser.address.WebAddress
import app.jrny.browser.ui.BrowserScreen
import app.jrny.browser.ui.SystemBarsFollowTheme
import app.jrny.browser.ui.theme.JrnyTheme

class MainActivity : ComponentActivity() {

    /** The Profiles and their Tabs. Visible to tests, which drive and inspect it. */
    @VisibleForTesting
    internal var browser: BrowserState? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            setContent {
                SystemBarsFollowTheme()
                JrnyTheme { UnsupportedWebView() }
            }
            return
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // Tabs come back from the database, after a recreation or after Android killed the app.
        val app = application as JrnyApplication
        val browser = BrowserState(this, app.repositories, app.downloader, lifecycleScope)
        this.browser = browser
        // Not after a restore: the link's Tab is already saved, and would open a second time.
        if (savedInstanceState == null) openIncomingLink(intent)
        setContent {
            SystemBarsFollowTheme()
            JrnyTheme {
                BrowserScreen(browser, onSetDefaultBrowser = if (canBecomeDefaultBrowser) ::askToBeDefaultBrowser else null)
            }
        }
    }

    /** The activity is singleTask, so a link from another app arrives here while it's open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openIncomingLink(intent)
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

    /** True while Android would let the person make JRNY browser the default browser, and it isn't. */
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
        browser?.destroy()
        browser = null
        super.onDestroy()
    }
}

@Composable
private fun UnsupportedWebView() {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.unsupported_webview))
        }
    }
}
