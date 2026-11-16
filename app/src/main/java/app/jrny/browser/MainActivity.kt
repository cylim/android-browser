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
import app.jrny.browser.profile.ProfileId
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

        // Only addresses come back, never page content (SPEC, "Tabs").
        val restored = savedInstanceState?.getBundle(KEY_ADDRESSES)?.let { bundle ->
            bundle.keySet().associate { ProfileId(it) to bundle.getString(it).orEmpty() }
        }.orEmpty()
        val browser = BrowserState(this, (application as JrnyApplication).profiles, lifecycleScope, restored)
        this.browser = browser
        setContent {
            SystemBarsFollowTheme()
            JrnyTheme { BrowserScreen(browser) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val addresses = browser?.addresses() ?: return
        outState.putBundle(KEY_ADDRESSES, Bundle().apply { addresses.forEach { (id, url) -> putString(id.value, url) } })
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

    private companion object {
        const val KEY_ADDRESSES = "addresses"
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
