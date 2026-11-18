package app.jrny.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
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

/** Five suggestion rows; more scroll. */
private val SUGGESTIONS_MAX_HEIGHT = 288.dp

/** Below a panel's last row, so it can scroll clear of the gesture handle. */
private val PANEL_BOTTOM_SPACE = 24.dp

/** Between the Incoming Link prompt and the page's edges. */
private val PROMPT_GAP = 16.dp

/** Between the Tab Overview's cards and the screen's sides. */
private val OVERVIEW_GUTTER = 8.dp
private val OVERVIEW_ROW_SHAPE = RoundedCornerShape(12.dp)

/**
 * The narrow-window layout: the page, with back, the Active Profile's avatar, the address field
 * (reload sits inside it), the Tab count and the page menu along the bottom (SPEC, "Bottom Bar").
 * Forward has its own button only where there's room; otherwise it's in the page menu.
 * Swiping along the bar switches Tabs, and the Tab count opens the Tab Overview. It runs edge to
 * edge: the page starts at the top edge (only a status bar kept under a camera cutout pushes it
 * down), and the bar leaves just enough room below for the gesture handle.
 */
@Composable
fun BottomBarLayout(
    tab: Tab,
    tabs: TabsUi,
    profiles: ProfilesUi,
    slots: WindowSlots,
    /** False in a narrow window, where Forward is in the page menu instead. */
    showForward: Boolean,
    editingAddress: Boolean = false,
) {
    var sheet by remember { mutableStateOf(false) }
    var overview by rememberSaveable { mutableStateOf(false) }
    // A link from another app shows at once, not behind the Tab Overview.
    LaunchedEffect(tabs.incomingLink) { if (tabs.incomingLink) overview = false }
    // The Tab Overview replaces the page and the bar. Left underneath, they would still take
    // keyboard focus and be read by a screen reader.
    val panel = slots.panel
    if (overview) {
        TabOverview(tabs, profileTint(profiles.active), onClose = { overview = false })
    } else if (panel != null) {
        // Full screen, like the Tab Overview: clear of side buttons, cutouts and what's at the top,
        // with the list running to the bottom edge under the gesture handle.
        Surface(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(overviewTopSpace()),
            ) { panel(PaddingValues(bottom = PANEL_BOTTOM_SPACE)) }
        }
    } else {
        BarAndPage(tab, tabs, profiles, slots, showForward, editingAddress, onProfiles = { sheet = true }, onTabs = { overview = true })
    }
    if (sheet) ProfileSheet(profiles, onDismiss = { sheet = false })
}

@Composable
private fun BarAndPage(
    tab: Tab,
    tabs: TabsUi,
    profiles: ProfilesUi,
    slots: WindowSlots,
    showForward: Boolean,
    editingAddress: Boolean,
    onProfiles: () -> Unit,
    onTabs: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            // 3-button navigation on the left or right of a landscape window. Zero with gestures.
            .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal))
            .testTag("bottom-bar-layout"),
    ) {
        // What floats over the page: the address field's suggestions, else the page's prompt.
        val floating: @Composable BoxScope.() -> Unit = {
            val suggestions = slots.suggestions
            if (suggestions != null) {
                // What the address field offers, over the page and right above the bar, tinted like it.
                Surface(color = profileTint(profiles.active), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    suggestions(Modifier.heightIn(max = SUGGESTIONS_MAX_HEIGHT).padding(top = BAR_GAP))
                }
            } else {
                Box(Modifier.align(Alignment.BottomCenter).padding(PROMPT_GAP)) { slots.pagePrompt() }
            }
        }
        val startPage = slots.startPage
        val pageInsets = pageTopInsets()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PageView(
                tab,
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(pageInsets),
                covered = startPage != null,
                overlay = if (startPage == null) floating else ({}),
            )
            if (startPage != null) {
                // Runs up to the top edge, under a status bar kept for a cutout, with its content
                // below it, and ends a gap above the bar.
                startPage(PaddingValues(bottom = BAR_GAP), pageInsets)
                Box(Modifier.fillMaxSize().windowInsetsPadding(pageInsets), content = floating)
            }
        }
        // Tinted with the Active Profile's color, like the Sidebar.
        Surface(color = profileTint(profiles.active)) {
            Column(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                LoadProgress(tab)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        // Swiping along the bar switches to the next or previous Tab (SPEC, "Bottom Bar"),
                        // also across the address field, except while typing, when a drag moves the cursor.
                        .horizontalSwipe(tabs.selected.id, tabs.all, beforeChildren = !editingAddress) { step ->
                            tabs.neighbour(step)?.let { tabs.select(it.id) }
                        }
                        .padding(
                            start = BAR_SIDE_PADDING,
                            end = BAR_SIDE_PADDING,
                            top = BAR_GAP,
                            bottom = bottomEdgeSpace(BAR_GAP),
                        )
                        .testTag("bottom-bar"),
                ) {
                    val findBar = slots.findBar
                    if (findBar != null) {
                        // Find in page takes the address bar's place, above the keyboard (SPEC, "Find in page").
                        findBar(Modifier.weight(1f), FIELD_SHAPE)
                    } else {
                        BackButton(tab)
                        if (showForward) ForwardButton(tab)
                        // The Profile sheet: switch, create, edit (SPEC, "Bottom Bar").
                        ProfileAvatar(profiles.active, onClickLabel = stringResource(R.string.switch_profile), onClick = onProfiles)
                        slots.addressField(Modifier.weight(1f), FIELD_SHAPE)
                        TabCountButton(tabs.all.size, onClick = onTabs)
                        slots.pageMenu()
                    }
                }
            }
        }
    }
}

/**
 * The Tab Overview: all the Active Profile's Tabs, full screen over the page. Choosing a Tab shows
 * it; the bar at the bottom opens a new Tab or goes back to the page, as Back does. The list's
 * text and close buttons line up with the bar's icons, [OVERVIEW_GUTTER] plus a row's own inset
 * from the screen's edges.
 */
@Composable
private fun TabOverview(tabs: TabsUi, tint: Color, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(color = tint, modifier = Modifier.fillMaxSize().testTag("tab-overview")) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            TabList(
                tabs,
                shape = OVERVIEW_ROW_SHAPE,
                selectedColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(top = OVERVIEW_GUTTER) + overviewTopSpace(),
                onChosen = onClose,
                modifier = Modifier.weight(1f).padding(horizontal = OVERVIEW_GUTTER),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = OVERVIEW_GUTTER + BAR_SIDE_PADDING,
                    end = OVERVIEW_GUTTER + BAR_SIDE_PADDING,
                    top = BAR_GAP,
                    bottom = bottomEdgeSpace(BAR_GAP),
                ),
            ) {
                IconButton(
                    onClick = {
                        tabs.open()
                        onClose()
                    },
                    modifier = Modifier.testTag("new-tab"),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.open_new_tab))
                }
                Text(
                    pluralStringResource(R.plurals.tab_count, tabs.all.size, tabs.all.size),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose, modifier = Modifier.testTag("close-overview")) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

/**
 * What the page keeps clear of at the top and sides: a freeform window's title bar (statusBars
 * doesn't include it), the status bar (zero while hidden; under a top cutout it shows), and the
 * display cutout.
 */
@Composable
private fun pageTopInsets(): WindowInsets =
    WindowInsets.captionBar.union(WindowInsets.statusBars)
        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

/** Above the Tab Overview's list: a freeform window's title bar, a status bar kept under a top cutout, and the cutout. */
@Composable
private fun overviewTopSpace(): PaddingValues =
    WindowInsets.captionBar.union(WindowInsets.statusBars).union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top)
        .asPaddingValues()

@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + other.calculateStartPadding(direction),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(direction) + other.calculateEndPadding(direction),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}

/** The Profile sheet, from the Bottom Bar's avatar: the same list as the Sidebar's menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(profiles: ProfilesUi, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("profile-sheet")) {
        // Many Profiles can outgrow the sheet, so the list scrolls.
        ProfileList(
            profiles,
            onDone = onDismiss,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = BAR_GAP),
        )
    }
}
