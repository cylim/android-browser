package app.jrny.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.jrny.browser.tab.Tab

/** Space above the controls, and the least space below them. */
private val BAR_GAP = 8.dp

/** Icon buttons are 48dp with a 24dp icon, so 4dp puts the outer icons 16dp from the screen edges. */
private val BAR_SIDE_PADDING = 4.dp

/**
 * The narrow-window layout: the page, with back, forward, the Active Profile's avatar, the
 * address field and reload along the bottom (SPEC, "Bottom Bar"). It runs edge to edge: the page
 * starts at the top edge (only a status bar kept under a camera cutout pushes it down), and the
 * bar leaves just enough room below for the gesture handle.
 */
@Composable
fun BottomBarLayout(tab: Tab) {
    Column(Modifier.fillMaxSize().testTag("bottom-bar-layout")) {
        PageView(
            tab,
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Zero when the status bar is hidden. Under a top cutout it shows and takes its strip.
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                LoadProgress(tab)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = BAR_SIDE_PADDING,
                        end = BAR_SIDE_PADDING,
                        top = BAR_GAP,
                        bottom = bottomEdgeSpace(BAR_GAP),
                    ),
                ) {
                    BackButton(tab)
                    ForwardButton(tab)
                    // Switching Profiles from here arrives in Milestone 2.
                    ProfileAvatar()
                    AddressField(tab, Modifier.weight(1f))
                    ReloadOrStopButton(tab)
                }
            }
        }
    }
}
