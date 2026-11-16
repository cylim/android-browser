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
import app.jrny.browser.profile.FirstProfile
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.BrowserScreen
import app.jrny.browser.ui.SystemBarsFollowTheme
import app.jrny.browser.ui.theme.JrnyTheme

class MainActivity : ComponentActivity() {

    /** The one Tab until Milestone 3. Visible to tests, which drive and inspect it. */
    @VisibleForTesting
    internal var tab: Tab? = null
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

        val tab = Tab(this, WebProfile.of(FirstProfile.id))
        this.tab = tab
        // Only the address comes back, never page content (SPEC, "Tabs").
        savedInstanceState?.getString(KEY_ADDRESS)?.let(tab::load)
        setContent {
            SystemBarsFollowTheme()
            JrnyTheme { BrowserScreen(tab) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tab?.url?.let { outState.putString(KEY_ADDRESS, it) }
    }

    override fun onStop() {
        super.onStop()
        tab?.profile?.flush()
    }

    override fun onDestroy() {
        tab?.destroy()
        tab = null
        super.onDestroy()
    }

    private companion object {
        const val KEY_ADDRESS = "address"
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
