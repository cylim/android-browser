package app.jrny.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.jrny.browser.tab.Tab

/**
 * The browser window. It shows the Sidebar layout in windows at least 840dp wide and the Bottom
 * Bar layout otherwise, and switches live when the window resizes (rotation, split screen,
 * folding). The Tab and its page survive the switch.
 */
@Composable
fun BrowserScreen(tab: Tab) {
    // Back closes an open popup first, then goes back in the page's history.
    BackHandler(enabled = tab.popup != null || tab.canGoBack) {
        if (tab.popup != null) tab.closePopup() else tab.goBack()
    }
    var sidebarCollapsed by rememberSaveable { mutableStateOf(false) }
    HideStatusBar()

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            when (BrowserLayout.forWindowWidth(maxWidth.value)) {
                BrowserLayout.Sidebar ->
                    SidebarLayout(tab, sidebarCollapsed, onCollapsedChange = { sidebarCollapsed = it })
                BrowserLayout.BottomBar -> BottomBarLayout(tab)
            }
        }
    }
}
