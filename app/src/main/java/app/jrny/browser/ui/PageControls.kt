package app.jrny.browser.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.jrny.browser.R
import app.jrny.browser.address.WebAddress
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
        tab.popup?.let { popup ->
            key(popup) {
                Column(Modifier.fillMaxSize().testTag("popup")) {
                    PopupBar(tab.popupUrl.orEmpty(), onClose = tab::closePopup)
                    WebViewHost(popup, Modifier.weight(1f))
                }
            }
        }
        if (tab.crashed) CrashedPage(tab)
    }
}

/**
 * The bar above a popup, so a popup can't pass off a sign-in form as the page behind it:
 * - the popup's host. Its registrable domain (who owns the site, from the Public Suffix List)
 *   always shows in full, wrapping if it must. Only the part before it is cut, from the start:
 *   `…google.com.xxxx.attacker.com` shows `attacker.com` whole, never `accounts.google.com…`;
 * - the full address below it, on one line, or after a tap in a box of at most five lines that
 *   scrolls, so a long address never hides the popup;
 * - a close button.
 * Text starts 16dp in, and the close icon ends 16dp in.
 */
@Composable
fun PopupBar(address: String, onClose: () -> Unit) {
    var expanded by remember(address) { mutableStateOf(false) }
    val host = WebAddress.hostOf(address)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.testTag("popup-bar")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.show_full_address)) { expanded = !expanded }
                    .padding(vertical = 8.dp),
            ) {
                val (beforeSite, site) = WebAddress.splitAtRegistrableDomain(host.ifEmpty { address })
                Row(Modifier.testTag("popup-host")) {
                    // The site is measured first, at full width, so it is never cut; the part
                    // before it gets what's left.
                    if (beforeSite.isNotEmpty()) {
                        Text(
                            beforeSite,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                            modifier = Modifier.weight(1f, fill = false).testTag("popup-host-prefix"),
                        )
                    }
                    Text(
                        site,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag("popup-site"),
                    )
                }
                if (host.isNotEmpty()) {
                    val style = MaterialTheme.typography.bodySmall
                    val fiveLines = with(LocalDensity.current) { (style.lineHeight * 5).toDp() }
                    Text(
                        address,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .testTag("popup-address")
                            .then(
                                if (expanded) {
                                    Modifier.heightIn(max = fiveLines).verticalScroll(rememberScrollState())
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.close_popup))
            }
        }
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
