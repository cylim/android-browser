package app.jrny.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.jrny.browser.BrowserState
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.Tab

/** The address field, placed by a layout with its own modifier and shape. */
typealias AddressFieldSlot = @Composable (Modifier, Shape) -> Unit

/**
 * The browser: the Active Profile's Tab in [BrowserWindow], and the Profile editor when it's open.
 * Switching Profiles shows the other Profile's Tab, which keeps its page.
 */
@Composable
fun BrowserScreen(browser: BrowserState) {
    val all by browser.profiles.collectAsState()
    val active by browser.activeProfile.collectAsState()
    val pending by browser.pendingDeletionCount.collectAsState()
    val profile = active ?: return
    // Some(null) creates a Profile, Some(profile) changes one.
    var editing by remember { mutableStateOf<EditorTarget?>(null) }
    val profiles = ProfilesUi(all, profile, pending, browser::switchTo, edit = { editing = EditorTarget(it) })

    BrowserWindow(browser.tabFor(profile), profiles)

    editing?.let { target ->
        val existing = target.profile
        ProfileEditorDialog(
            profile = existing,
            canDelete = all.size > 1,
            onSave = { name, color, icon ->
                if (existing == null) browser.create(name, color, icon) else browser.update(existing.copy(name = name, color = color, icon = icon))
                editing = null
            },
            onDelete = {
                existing?.let(browser::delete)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private class EditorTarget(val profile: Profile?)

/**
 * The browser window for [tab]. It shows the Sidebar layout in windows at least 840dp wide and the
 * Bottom Bar layout otherwise, and switches live when the window resizes (rotation, split screen,
 * folding). The Tab and its page survive the switch.
 */
@Composable
fun BrowserWindow(tab: Tab, profiles: ProfilesUi) {
    // Back closes an open popup first, then goes back in the page's history.
    BackHandler(enabled = tab.popup != null || tab.canGoBack) {
        if (tab.popup != null) tab.closePopup() else tab.goBack()
    }
    var sidebarCollapsed by rememberSaveable { mutableStateOf(false) }
    HideStatusBar()
    // One address field that moves between the layouts, and its draft kept here, so a
    // half-typed address survives a resize across 840dp.
    val draft = remember(tab) { AddressDraft() }
    val movableAddressField = remember(tab) {
        movableContentOf { modifier: Modifier, shape: Shape, layout: BrowserLayout ->
            AddressField(tab, draft, modifier, shape, layout)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = BrowserLayout.forWindowWidth(maxWidth.value)
            val addressField: AddressFieldSlot = { modifier, shape -> movableAddressField(modifier, shape, layout) }
            // Mid-edit when the window widens: open the Sidebar, which holds the address field,
            // even if it was collapsed to the Rail.
            LaunchedEffect(layout) {
                if (layout == BrowserLayout.Sidebar && draft.value != null) sidebarCollapsed = false
            }
            when (layout) {
                BrowserLayout.Sidebar ->
                    SidebarLayout(tab, profiles, addressField, sidebarCollapsed, onCollapsedChange = { sidebarCollapsed = it })
                BrowserLayout.BottomBar -> BottomBarLayout(tab, profiles, addressField)
            }
        }
    }
}
