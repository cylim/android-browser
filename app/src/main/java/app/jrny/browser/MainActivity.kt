package app.jrny.browser

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewFeature
import androidx.lifecycle.lifecycleScope
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
        val browser = BrowserState(this, app.profiles, app.tabs, lifecycleScope)
        this.browser = browser
        setContent {
            SystemBarsFollowTheme()
            JrnyTheme { BrowserScreen(browser) }
        }
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
