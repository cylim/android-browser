package app.jrny.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.tab.Tab

/** The Bottom Bar's address field is a pill. */
private val FIELD_SHAPE = RoundedCornerShape(24.dp)

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
fun BottomBarLayout(tab: Tab, profiles: ProfilesUi, addressField: AddressFieldSlot) {
    var sheet by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            // 3-button navigation on the left or right of a landscape window. Zero with gestures.
            .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal))
            .testTag("bottom-bar-layout"),
    ) {
        PageView(
            tab,
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // A freeform window's title bar, which statusBars doesn't include.
                .windowInsetsPadding(WindowInsets.captionBar)
                // Zero when the status bar is hidden. Under a top cutout it shows and takes its strip.
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        )
        // Tinted with the Active Profile's color, like the Sidebar.
        Surface(color = profileTint(profiles.active)) {
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
                    // The Profile sheet: switch, create, edit (SPEC, "Bottom Bar").
                    ProfileAvatar(profiles.active, onClickLabel = stringResource(R.string.switch_profile)) { sheet = true }
                    addressField(Modifier.weight(1f), FIELD_SHAPE)
                    ReloadOrStopButton(tab)
                }
            }
        }
    }
    if (sheet) ProfileSheet(profiles, onDismiss = { sheet = false })
}

/** The Profile sheet, from the Bottom Bar's avatar: the same list as the Sidebar's menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(profiles: ProfilesUi, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("profile-sheet")) {
        ProfileList(profiles, onDone = onDismiss, modifier = Modifier.padding(bottom = BAR_GAP))
    }
}
