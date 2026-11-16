package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.Tab

// The Sidebar layout's spacing. Every gap is one of these, so the edges line up.

/** Around the Sidebar: between it and the screen's edges, and between it and the page. */
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

/** Between the Incoming Link prompt and the page's edges. */
private val PROMPT_GAP = 16.dp

/** Corner radius shared by the address field, Tab rows, menus and the page's edge next to the Sidebar. */
private val CORNER_RADIUS = 12.dp
private val CORNER = RoundedCornerShape(CORNER_RADIUS)

/** The page runs to the screen's edges; only its corners next to the Sidebar are rounded. */
private val PAGE_SHAPE = RoundedCornerShape(topStart = CORNER_RADIUS, bottomStart = CORNER_RADIUS)

/**
 * The wide-window layout: an Arc-style Sidebar with navigation, the address field, the Active
 * Profile's Tabs and the Profile switcher at the bottom, next to the page. [collapsed] shrinks
 * the Sidebar to the Rail so the page gets almost the full width.
 *
 * The page runs to the screen's top, end and bottom edges; only the Sidebar keeps a gutter. The
 * status bar is hidden (see [HideStatusBar]), and the gesture handle draws over the page, so
 * neither reserves a strip. Only 3-button navigation, display cutouts, a freeform window's title
 * bar, a status bar that stays visible under a top cutout, and the keyboard take space.
 */
@Composable
fun SidebarLayout(
    tab: Tab,
    tabs: TabsUi,
    profiles: ProfilesUi,
    slots: WindowSlots,
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
) {
    // A Surface, not just a background, so text and icons get the matching content color in dark
    // mode. Tinted with the Active Profile's color, so it's clear which Profile is showing.
    Surface(color = profileTint(profiles.active), modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().testTag("sidebar-layout")) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(systemSpace(WindowInsetsSides.Start))
                    .padding(GUTTER),
            ) {
                if (collapsed) {
                    Rail(tab, profiles, slots, onExpand = { onCollapsedChange(false) })
                } else {
                    Sidebar(tab, tabs, profiles, slots, onCollapse = { onCollapsedChange(true) })
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(systemSpace(WindowInsetsSides.End))
                    .imePadding()
                    .clip(PAGE_SHAPE),
            ) {
                val panel = slots.panel
                if (panel != null) {
                    // In the page's place, next to the Sidebar. Its list ends clear of the gesture handle.
                    panel(PaddingValues(bottom = floatingBottomSpace(GUTTER)))
                } else {
                    PageView(tab, Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = PROMPT_GAP, end = PROMPT_GAP, bottom = floatingBottomSpace(PROMPT_GAP)),
                        ) { slots.pagePrompt() }
                    }
                    LoadProgress(tab, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

/**
 * The space one side of the layout leaves for the system, at the top, bottom and [side]:
 * - 3-button navigation (gesture navigation has no tappable element at the bottom or sides, so
 *   this is zero there). Its top is left out: the hidden status bar still counts as tappable;
 * - a freeform window's title bar, which statusBars doesn't include;
 * - the status bar, zero while hidden (under a top cutout it stays visible);
 * - a display cutout.
 */
@Composable
private fun systemSpace(side: WindowInsetsSides): WindowInsets =
    WindowInsets.tappableElement.only(WindowInsetsSides.Bottom + side)
        .union(WindowInsets.captionBar.only(WindowInsetsSides.Top))
        .union(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Top + side))

@Composable
private fun Sidebar(tab: Tab, tabs: TabsUi, profiles: ProfilesUi, slots: WindowSlots, onCollapse: () -> Unit) {
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
            slots.pageMenu()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCollapse) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.hide_sidebar))
            }
        }
        Spacer(Modifier.height(GUTTER))
        // TextField puts its text 16dp inside, which is CONTENT_INSET, and is 56dp tall, like a Tab row.
        slots.addressField(Modifier.fillMaxWidth(), CORNER)

        // The Tab area: swiping across it switches to the next or previous Profile (SPEC, "Sidebar").
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalSwipe(profiles.active.id, profiles.all) { step -> profiles.neighbour(step)?.let(profiles.switchTo) }
                .testTag("sidebar-tabs"),
        ) {
            val suggestions = slots.suggestions
            // While the person types, what the address field offers takes the Tab list's place.
            if (suggestions != null) {
                suggestions(Modifier.weight(1f).padding(top = GUTTER))
            } else {
            // The label's text starts on the content line; the new-Tab button sits in the icon
            // column at the end, under the collapse arrow.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = GUTTER, end = ICON_SLOT_INSET),
            ) {
                Text(
                    stringResource(R.string.tabs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = CONTENT_INSET),
                )
                IconButton(onClick = tabs.open, modifier = Modifier.testTag("new-tab")) {
                    Icon(Icons.Filled.Add, stringResource(R.string.open_new_tab))
                }
            }
            TabList(tabs, shape = CORNER, selectedColor = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f))
            }
        }
        ProfileSwitcher(profiles)
    }
}

/** The collapsed Sidebar: the same icon column, top line and bottom line as the expanded one. */
@Composable
private fun Rail(tab: Tab, profiles: ProfilesUi, slots: WindowSlots, onExpand: () -> Unit) {
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
        slots.pageMenu()
        Spacer(Modifier.weight(1f))
        Box {
            var menu by remember { mutableStateOf(false) }
            ProfileAvatar(profiles.active, onClickLabel = stringResource(R.string.switch_profile)) { menu = true }
            // The avatar sits ICON_SLOT_INSET in from the gutter; the menu starts at the gutter,
            // like the expanded Sidebar's, so the avatars in it share the icon column.
            ProfileMenu(profiles, expanded = menu, onDismiss = { menu = false }, offset = DpOffset(-ICON_SLOT_INSET, 0.dp))
        }
    }
}

/**
 * The Active Profile at the bottom of the Sidebar. A tap opens the Profile menu: switch, create,
 * edit. The row's avatar sits in the icon column, and its 48dp height keeps the bottom line.
 */
@Composable
private fun ProfileSwitcher(profiles: ProfilesUi) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CORNER)
                .clickable(onClickLabel = stringResource(R.string.switch_profile), role = Role.Button) { menu = true }
                .padding(start = ICON_SLOT_INSET, end = ICON_SLOT_INSET)
                .testTag("profile-switcher"),
        ) {
            ProfileAvatar(profiles.active)
            Text(
                profiles.active.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = ICON_SLOT_INSET),
            )
            // In a 48dp slot's place, so it mirrors the collapse arrow at the top: 12dp in.
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp))
        }
        ProfileMenu(profiles, expanded = menu, onDismiss = { menu = false })
    }
}

/** The Profile list as a menu, opening from the bottom of the Sidebar or Rail. */
@Composable
private fun ProfileMenu(profiles: ProfilesUi, expanded: Boolean, onDismiss: () -> Unit, offset: DpOffset = DpOffset.Zero) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = CORNER, offset = offset) {
        ProfileList(profiles, onDone = onDismiss, modifier = Modifier.width(SIDEBAR_WIDTH))
    }
}

/** The surface color, tinted 10% toward the Profile's color. */
@Composable
fun profileTint(profile: Profile): Color =
    lerp(MaterialTheme.colorScheme.surfaceContainer, profile.color.color, 0.10f)
