package app.jrny.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.profile.FirstProfile
import app.jrny.browser.tab.Tab

// The Sidebar layout's spacing. Every gap is one of these, so the edges line up.

/** Between the screen edge and the panels, and between the Sidebar and the page. */
private val GUTTER = 8.dp

/** Width of the expanded Sidebar's content. */
private val SIDEBAR_WIDTH = 288.dp

/**
 * Where text starts inside the Sidebar, measured from its edge: the address field's text, the
 * "Tabs" label and Tab titles share this line.
 */
private val CONTENT_INSET = 16.dp

/**
 * Where 48dp icon-button slots start inside the Sidebar. Icons and the Profile avatar are centered
 * in those slots, so they share one column (centered 28dp from the Sidebar's edge) whether the
 * Sidebar is expanded or collapsed to the Rail.
 */
private val ICON_SLOT_INSET = 4.dp

/** Height of the address field and of a Tab row. */
private val ROW_HEIGHT = 56.dp

/** Corner radius shared by the page, the address field and Tab rows. */
private val CORNER = RoundedCornerShape(12.dp)

/**
 * The wide-window layout: an Arc-style Sidebar with navigation, the address field, the Active
 * Profile's Tabs and the Profile switcher at the bottom, next to the page. [collapsed] shrinks
 * the Sidebar to the Rail so the page gets almost the full width.
 *
 * The layout runs edge to edge. The status bar is hidden (see [HideStatusBar]), and the gesture
 * handle draws over the page, so neither reserves a strip. Only 3-button navigation, display
 * cutouts, a status bar that stays visible under a top cutout, and the keyboard take space.
 */
@Composable
fun SidebarLayout(tab: Tab, collapsed: Boolean, onCollapsedChange: (Boolean) -> Unit) {
    // A Surface, not just a background, so text and icons get the matching content color in dark mode.
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(GUTTER),
            modifier = Modifier
                .fillMaxSize()
                // Gesture navigation has no tappable element at the bottom or sides, so this is
                // zero there, and 3-button navigation keeps its space. The top is left out: the
                // hidden status bar still counts as a tappable element.
                .windowInsetsPadding(
                    WindowInsets.tappableElement.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                // Zero while the status bar is hidden. Under a top cutout it stays visible.
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(GUTTER)
                .testTag("sidebar-layout"),
        ) {
            if (collapsed) {
                Rail(tab, onExpand = { onCollapsedChange(false) })
            } else {
                Sidebar(tab, onCollapse = { onCollapsedChange(true) })
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .imePadding()
                    .clip(CORNER),
            ) {
                PageView(tab, Modifier.fillMaxSize())
                LoadProgress(tab, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun Sidebar(tab: Tab, onCollapse: () -> Unit) {
    Column(
        Modifier
            .width(SIDEBAR_WIDTH)
            .fillMaxHeight()
            .testTag("sidebar"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ICON_SLOT_INSET),
        ) {
            BackButton(tab)
            ForwardButton(tab)
            ReloadOrStopButton(tab)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCollapse) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.hide_sidebar))
            }
        }
        Spacer(Modifier.height(GUTTER))
        // TextField puts its text 16dp inside, which is CONTENT_INSET, and is 56dp tall (ROW_HEIGHT).
        AddressField(tab, Modifier.fillMaxWidth(), shape = CORNER)

        Text(
            stringResource(R.string.tabs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CONTENT_INSET, top = GUTTER * 2, bottom = GUTTER),
        )
        // One Tab, always the selected one, until Milestone 3 adds more.
        SelectedTabRow(tab)

        Spacer(Modifier.weight(1f))
        ProfileSwitcher()
    }
}

/** The collapsed Sidebar: the same icon column, top line and bottom line as the expanded one. */
@Composable
private fun Rail(tab: Tab, onExpand: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = ICON_SLOT_INSET)
            .testTag("sidebar-rail"),
    ) {
        IconButton(onClick = onExpand) {
            Icon(Icons.Filled.Menu, stringResource(R.string.show_sidebar))
        }
        BackButton(tab)
        ForwardButton(tab)
        ReloadOrStopButton(tab)
        Spacer(Modifier.weight(1f))
        ProfileAvatar()
    }
}

@Composable
private fun SelectedTabRow(tab: Tab) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CORNER,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .heightIn(min = ROW_HEIGHT)
                .padding(horizontal = CONTENT_INSET, vertical = GUTTER),
        ) {
            Text(
                tab.pageTitle ?: tab.displayHost.ifEmpty { stringResource(R.string.new_tab) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tab.pageTitle != null && tab.displayHost.isNotEmpty()) {
                Text(
                    tab.displayHost,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The Active Profile, at the bottom of the Sidebar. Switching Profiles arrives in Milestone 2. */
@Composable
private fun ProfileSwitcher() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ICON_SLOT_INSET)
            .testTag("profile-switcher"),
    ) {
        ProfileAvatar()
        Text(
            stringResource(FirstProfile.nameRes),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = ICON_SLOT_INSET),
        )
    }
}
