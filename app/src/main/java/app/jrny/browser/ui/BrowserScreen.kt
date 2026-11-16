package app.jrny.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.jrny.browser.tab.Tab

/** The address field, placed by a layout with its own modifier and shape. */
typealias AddressFieldSlot = @Composable (Modifier, Shape) -> Unit

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
    // One address field that moves between the layouts with its state, so a half-typed
    // address survives a resize across 840dp.
    val movableAddressField = remember(tab) {
        movableContentOf { modifier: Modifier, shape: Shape, layout: BrowserLayout ->
            AddressField(tab, modifier, shape, layout)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = BrowserLayout.forWindowWidth(maxWidth.value)
            val addressField: AddressFieldSlot = { modifier, shape -> movableAddressField(modifier, shape, layout) }
            when (layout) {
                BrowserLayout.Sidebar ->
                    SidebarLayout(tab, addressField, sidebarCollapsed, onCollapsedChange = { sidebarCollapsed = it })
                BrowserLayout.BottomBar -> BottomBarLayout(tab, addressField)
            }
        }
    }
}
