package app.jrny.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import app.jrny.browser.BrowserState
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.Tab

/** The address field, placed by a layout with its own modifier and shape. */
typealias AddressFieldSlot = @Composable (Modifier, Shape) -> Unit

/** What [BrowserWindow] builds once and each layout places where it belongs. */
class WindowSlots(
    val addressField: AddressFieldSlot,
    /** The "More" button with the page menu. */
    val pageMenu: @Composable () -> Unit,
    /** The Incoming Link prompt, floating at the bottom of the page. Empty when there's nothing to offer. */
    val pagePrompt: @Composable () -> Unit,
    /**
     * The open panel (history or bookmarks), which the layout shows in the page's place, or null.
     * The layout passes the space its list must keep clear at the bottom.
     */
    val panel: (@Composable (PaddingValues) -> Unit)? = null,
)

/**
 * The browser: the Active Profile's selected Tab in [BrowserWindow], and the Profile editor when
 * it's open. Switching Profiles or Tabs shows the other Tab, which keeps its page while it's live.
 */
@Composable
fun BrowserScreen(browser: BrowserState, onSetDefaultBrowser: (() -> Unit)? = null) {
    val all by browser.profiles.collectAsState()
    val active by browser.active.collectAsState()
    val pending by browser.pendingDeletionCount.collectAsState()
    val current = active ?: return
    val profile = current.profile
    // Some(null) creates a Profile, Some(profile) changes one.
    var editing by remember { mutableStateOf<EditorTarget?>(null) }
    val profiles = ProfilesUi(all, profile, pending, browser::switchTo, edit = { editing = EditorTarget(it) })
    val tabs = TabsUi(
        all = current.tabs,
        selected = current.selected,
        select = browser::selectTab,
        close = browser::closeTab,
        open = { browser.openTab(profile) },
        move = { browser.moveTab(current.selected, it) },
        openIn = { browser.openInProfile(current.selected, it) },
        incomingLink = browser.incomingLinkTab == current.selected.id,
        dismissIncomingLink = browser::dismissIncomingLink,
    )

    val visits by browser.visits.collectAsState()
    val bookmarks by browser.bookmarks.collectAsState()
    val downloads by browser.downloads.collectAsState()
    val library = LibraryUi(
        visits = visits,
        bookmarks = bookmarks,
        toggleBookmark = { url, title -> browser.toggleBookmark(profile, url, title) },
        deleteBookmark = browser::deleteBookmark,
        deleteVisit = browser::deleteVisit,
        clearHistory = { browser.clearHistory(profile) },
        downloads = downloads,
        startDownload = { browser.startDownload(profile, it) },
        downloadStatuses = browser::downloadStatuses,
        openDownload = browser::openDownload,
        removeDownload = browser::removeDownload,
    )

    val selected = current.selected.id
    val tab = browser.tabFor(current.selected)
    LaunchedEffect(selected) { browser.shown(selected) }
    BrowserWindow(tab, tabs, profiles, library, onSetDefaultBrowser)

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
fun BrowserWindow(
    tab: Tab,
    tabs: TabsUi,
    profiles: ProfilesUi,
    library: LibraryUi = LibraryUi(),
    onSetDefaultBrowser: (() -> Unit)? = null,
) {
    // Back closes an open popup first, then goes back in the page's history.
    BackHandler(enabled = tab.popup != null || tab.canGoBack) {
        if (tab.popup != null) tab.closePopup() else tab.goBack()
    }
    var sidebarCollapsed by rememberSaveable { mutableStateOf(false) }
    // The Profile picker for Move or Open in Profile, opened from the page menu or the Incoming
    // Link prompt. It belongs to the Tab it was opened for: if another Tab is shown meanwhile
    // (an Incoming Link, say), it closes, so a choice never moves a Tab the person didn't mean.
    var pick by remember(tabs.selected.id) { mutableStateOf<ProfilePick?>(null) }
    // The history or bookmarks, shown in the page's place. Back closes it before anything else.
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    // The file whose download just started, named over the page with a way to the Downloads list.
    var downloading by remember(tab) { mutableStateOf<String?>(null) }
    BackHandler(enabled = panel != null) { panel = null }
    // A link from another app shows at once, with its Move offer, not behind an open panel.
    LaunchedEffect(tabs.incomingLink) { if (tabs.incomingLink) panel = null }
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
            // A Bottom Bar at least this wide (a tablet in portrait) has room for the Forward
            // button. A narrower one keeps Forward in the page menu.
            val forwardInBar = maxWidth >= BOTTOM_BAR_FORWARD_MIN_WIDTH
            val slots = WindowSlots(
                addressField = addressField,
                pageMenu = {
                    PageMenu(
                        tab,
                        tabs,
                        profiles,
                        library,
                        showForward = layout == BrowserLayout.BottomBar && !forwardInBar,
                        onPick = { pick = it },
                        onPanel = { panel = it },
                        onSetDefaultBrowser = onSetDefaultBrowser,
                    )
                },
                pagePrompt = {
                    val started = downloading
                    if (started != null) {
                        DownloadStartedPrompt(
                            started,
                            onShow = {
                                panel = Panel.Downloads
                                downloading = null
                            },
                            onDismiss = { downloading = null },
                        )
                    } else if (tabs.incomingLink && profiles.all.size > 1) {
                        // With one Profile there's nowhere to move the page to, so nothing to offer.
                        IncomingLinkPrompt(profiles.active.name, onMove = { pick = ProfilePick.Move }, onDismiss = tabs.dismissIncomingLink)
                    }
                },
                panel = panel?.let { open ->
                    { padding ->
                        LibraryPanel(
                            panel = open,
                            library = library,
                            profileName = profiles.active.name,
                            onOpen = {
                                tab.load(it)
                                panel = null
                            },
                            onClose = { panel = null },
                            contentPadding = padding,
                        )
                    }
                },
            )
            when (layout) {
                BrowserLayout.Sidebar ->
                    SidebarLayout(tab, tabs, profiles, slots, sidebarCollapsed, onCollapsedChange = { sidebarCollapsed = it })
                BrowserLayout.BottomBar ->
                    BottomBarLayout(tab, tabs, profiles, slots, showForward = forwardInBar, editingAddress = draft.value != null)
            }
        }
    }

    // A page can only offer a file. It downloads once the person agrees.
    tab.download?.let { request ->
        DownloadDialog(
            request,
            onDownload = {
                library.startDownload(request)
                downloading = request.fileName
                tab.dismissDownload()
            },
            onDismiss = tab::dismissDownload,
        )
    }

    pick?.let { action ->
        ProfilePickerDialog(
            pick = action,
            others = profiles.all.filter { it.id != profiles.active.id },
            onChoose = { target ->
                if (action == ProfilePick.Move) tabs.move(target) else tabs.openIn(target)
                pick = null
            },
            onDismiss = { pick = null },
        )
    }
}

/** From this window width up, the Bottom Bar shows the Forward button itself. */
private val BOTTOM_BAR_FORWARD_MIN_WIDTH = 600.dp
