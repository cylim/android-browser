package app.jrny.browser.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.jrny.browser.R
import app.jrny.browser.tab.Tab

/**
 * The Tab's page, with its popup on top while one is open, or the "page stopped working"
 * screen after the renderer died.
 */
@Composable
fun PageView(tab: Tab, modifier: Modifier = Modifier) {
    Box(modifier.testTag("page")) {
        // A new WebView replaces a dead one (Tab.replaceDeadWebView), so key the view on it.
        key(tab.webView) { WebViewHost(tab.webView) }
        tab.popup?.let { popup -> key(popup) { WebViewHost(popup, Modifier.testTag("popup")) } }
        if (tab.crashed) CrashedPage(tab)
    }
}

@Composable
private fun WebViewHost(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        factory = {
            // The WebView outlives this composition (and moves between layouts), so detach it
            // from any earlier parent.
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun CrashedPage(tab: Tab) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            Text(stringResource(R.string.page_crashed), textAlign = TextAlign.Center)
            Button(onClick = tab::reload, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.reload))
            }
        }
    }
}

/** A thin bar showing load progress, shown only while the page loads. */
@Composable
fun LoadProgress(tab: Tab, modifier: Modifier = Modifier) {
    if (tab.isLoading) {
        LinearProgressIndicator(
            progress = { tab.progress / 100f },
            modifier = modifier.fillMaxWidth().height(2.dp),
        )
    }
}

@Composable
fun BackButton(tab: Tab) {
    IconButton(onClick = tab::goBack, enabled = tab.canGoBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
    }
}

@Composable
fun ForwardButton(tab: Tab) {
    IconButton(onClick = tab::goForward, enabled = tab.canGoForward) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward))
    }
}

/** Stop while the page loads, otherwise reload. */
@Composable
fun ReloadOrStopButton(tab: Tab) {
    if (tab.isLoading) {
        IconButton(onClick = tab::stop) {
            Icon(Icons.Filled.Close, stringResource(R.string.stop))
        }
    } else {
        IconButton(onClick = tab::reload, enabled = tab.url != null) {
            Icon(Icons.Filled.Refresh, stringResource(R.string.reload))
        }
    }
}
