package app.jrny.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import app.jrny.browser.BrowserState
import app.jrny.browser.BuildConfig
import app.jrny.browser.LinkOffer
import app.jrny.browser.address.AddressInput
import app.jrny.browser.library.ProfilePages
import app.jrny.browser.settings.Suggestion
import kotlinx.coroutines.delay
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.ProfileTheme

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
    /** What the address field offers for the text typed so far, or null when there is nothing. */
    val suggestions: (@Composable (Modifier) -> Unit)? = null,
    /**
     * The Start Page, while the Tab has no web page, or null. The layout draws it over the page,
     * under the prompts and suggestions. It passes the space the content must keep clear at the
     * bottom, and the system's insets the content must keep clear of, while the Start Page's
     * color runs under them.
     */
    val startPage: (@Composable (contentPadding: PaddingValues, contentInsets: WindowInsets) -> Unit)? = null,
    /**
     * Find in page while it is open, or null (SPEC, "Find in page"). The Bottom Bar shows it in
     * the address bar's place; the Sidebar layout across the top of the page.
     */
    val findBar: (@Composable (Modifier, Shape) -> Unit)? = null,
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
    val pinned by browser.pinned.collectAsState()
    val startPagePages by browser.startPagePages.collectAsState()
    val tabs = TabsUi(
        all = current.tabs,
        selected = current.selected,
        select = { browser.selectTab(profile, it) },
        close = browser::closeTab,
        open = { browser.openTab(profile) },
        openPrivate = { browser.openPrivateTab(profile) },
        move = { browser.moveTab(current.selected, it) },
        openIn = { browser.openInProfile(current.selected, it) },
        linkOffer = browser.linkOffer?.takeIf { it.tab == current.selected.id },
        dismissIncomingLink = browser::dismissIncomingLink,
        acceptRuleOffer = browser::acceptRuleOffer,
        pins = PinsUi(
            items = pinned,
            open = { browser.openPinned(profile, it) },
            pin = browser::pinTab,
            unpin = browser::deleteBookmark,
            reset = { bookmark ->
                current.tabs.firstOrNull { it.bookmarkId == bookmark.id }?.let { open ->
                    browser.tabFor(open).load(bookmark.url)
                    browser.selectTab(profile, open.id)
                }
            },
            rename = browser::renameBookmark,
            move = browser::moveBookmark,
            addFolder = { name, first -> browser.addFolder(profile, name, first) },
            renameFolder = browser::renameFolder,
            setCollapsed = browser::setFolderCollapsed,
            deleteFolder = browser::deleteFolder,
            reorder = { browser.reorderPinned(profile, it) },
            reorderIn = { browser.reorderFolder(profile, it) },
            openHere = { browser.openPinnedHere(profile, current.selected, it) },
        ),
    )

    val visits by browser.visits.collectAsState()
    val bookmarks by browser.bookmarks.collectAsState()
    val downloads by browser.downloads.collectAsState()
    val library = LibraryUi(
        visits = visits,
        bookmarks = bookmarks,
        toggleBookmark = { url, title -> browser.toggleBookmark(current.selected, url, title) },
        deleteBookmark = browser::deleteBookmark,
        deleteVisit = browser::deleteVisit,
        clearHistory = { browser.clearHistory(profile) },
        downloads = downloads,
        startDownload = {
            val shown = current.selected
            when {
                // A file the page made itself is saved by Okapi, from the page.
                it.fromPage -> browser.savePageFile(shown, browser.tabFor(shown), it)
                shown.isPrivate -> browser.startPrivateDownload(shown, it)
                else -> browser.startDownload(profile, it)
            }
        },
        downloadStatuses = browser::downloadStatuses,
        openDownload = browser::openDownload,
        removeDownload = browser::removeDownload,
    )

    val appSettings by browser.settings.collectAsState()
    val settings = SettingsUi(appSettings, browser::updateSettings, pending, BuildConfig.VERSION_NAME)

    val rules by browser.siteRules.collectAsState()
    val siteRules = SiteRulesUi(rules, browser::saveSiteRule, browser::deleteSiteRule)

    val links = LinksUi(
        openInNewTab = { browser.openInNewTab(current.selected, it) },
        openInPrivateTab = { browser.openInPrivateTab(profile, it) },
        openInProfile = browser::openLinkInProfile,
        fetchImage = { browser.fetchImage(current.selected, browser.tabFor(current.selected), it) },
        openedBehind = browser.openedBehind?.takeIf { it.profileId == profile.id },
        dismissOpenedBehind = browser::dismissOpenedBehind,
        pageFileSaved = browser.pageFileSaved,
        dismissPageFileSaved = browser::dismissPageFileSaved,
        setDesktopSite = { on -> browser.setDesktopSite(current.selected, browser.tabFor(current.selected), on) },
    )

    val sites by browser.siteSettings.collectAsState()
    val siteSettings = SiteSettingsUi(
        sites = sites,
        set = { origin, setting, enabled -> browser.setSiteSetting(profile, origin, setting, enabled) },
        reset = { origin, setting -> browser.resetSiteSetting(profile, origin, setting) },
        resetAll = { browser.resetAllSiteSettings(profile) },
        rememberPermission = { origin, settings, allowed -> browser.rememberPermission(current.selected, origin, settings, allowed) },
    )

    val selected = current.selected.id
    val tab = browser.tabFor(current.selected)
    LaunchedEffect(selected) { browser.shown(selected) }
    // The Active Profile's site icons. Made anew when one is saved, so the Tab list looks again.
    val iconVersion by browser.siteIcons.version.collectAsState()
    val siteIcons = remember(profile.id, iconVersion) {
        SiteIconSource(
            cached = { browser.siteIcons.cached(profile.id, it)?.asImageBitmap() },
            load = { browser.siteIcons.load(profile.id, it)?.asImageBitmap() },
        )
    }
    // The browser wears the Active Profile's color: its accents here, its tint in the layouts.
    CompositionLocalProvider(LocalSiteIcons provides siteIcons) {
    ProfileTheme(profile.color.color) {
        BrowserWindow(
            tab,
            tabs,
            profiles,
            library,
            onSetDefaultBrowser,
            settings,
            suggest = { browser.suggest(current.selected, it) },
            siteRules = siteRules,
            startPagePages = startPagePages,
            siteSettings = siteSettings,
            links = links,
        )

        editing?.let { target ->
            val existing = target.profile
            ProfileEditorDialog(
                profile = existing,
                canDelete = all.size > 1,
                onSave = { name, look ->
                    if (existing == null) {
                        browser.create(name, look)
                    } else {
                        browser.update(existing.copy(name = name, color = look.color, icon = look.icon, emoji = look.emoji, tint = look.tint))
                    }
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
    settings: SettingsUi = SettingsUi(),
    /** What to offer for the text typed in the address field so far. */
    suggest: suspend (String) -> List<Suggestion> = { emptyList() },
    siteRules: SiteRulesUi = SiteRulesUi(),
    /**
     * The Pinned Tabs and history the Start Page shows, with the Profile they belong to, or null
     * until they have loaded. The Start Page shows them only once they are the Active Profile's.
     */
    startPagePages: ProfilePages? = ProfilePages(profiles.active.id, tabs.pins.items, library.visits),
    siteSettings: SiteSettingsUi = SiteSettingsUi(),
    links: LinksUi = LinksUi(),
) {
    // Back closes an open popup first, then goes back in the page's history.
    BackHandler(enabled = tab.popup != null || tab.canGoBack) {
        if (tab.popup != null) tab.closePopup() else tab.goBack()
    }
    // Find in page closes before anything else on the page.
    BackHandler(enabled = tab.find != null) { tab.closeFind() }
    var sidebarCollapsed by rememberSaveable { mutableStateOf(false) }
    // The Profile picker for Move or Open in Profile, opened from the page menu or the Incoming
    // Link prompt. It belongs to the Tab it was opened for: if another Tab is shown meanwhile
    // (an Incoming Link, say), it closes, so a choice never moves a Tab the person didn't mean.
    var pick by remember(tabs.selected.id) { mutableStateOf<ProfilePick?>(null) }
    // A link from the link menu, while the person chooses a Profile to open it in.
    var linkToPick by remember(tabs.selected.id) { mutableStateOf<String?>(null) }
    // Fetching an image for Copy or Share outlives the menu it was chosen in.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The history or bookmarks, shown in the page's place. Back closes it before anything else.
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    // The file whose download just started, named over the page with a way to the Downloads list.
    var downloading by remember(tab) { mutableStateOf<String?>(null) }
    // The Site Rules and Site settings are reached from the settings, and Back leads there again.
    BackHandler(enabled = panel != null) { panel = if (panel == Panel.SiteRules || panel == Panel.SiteSettings) Panel.Settings else null }
    // A link from another app shows at once, with its Move offer, not behind an open panel.
    LaunchedEffect(tabs.incomingLink) { if (tabs.incomingLink) panel = null }
    HideStatusBar(fullscreen = tab.fullscreen != null)
    // One address field that moves between the layouts, and its draft kept here, so a
    // half-typed address survives a resize across 840dp.
    val draft = remember(tab) { AddressDraft() }
    val private = tabs.selected.isPrivate
    val searchTemplate = settings.settings.searchEngine.searchTemplate
    val movableAddressField = remember(tab, private, searchTemplate) {
        movableContentOf { modifier: Modifier, shape: Shape, layout: BrowserLayout ->
            AddressField(tab, draft, modifier, shape, layout, private, searchTemplate)
        }
    }

    // Suggestions for what is typed, asked for a moment after the last keystroke. Just focusing
    // the field, which selects the page's own address, asks for nothing.
    var suggestions by remember(tab) { mutableStateOf(emptyList<Suggestion>()) }
    val typed = draft.value?.text
    LaunchedEffect(tab, typed) {
        val text = typed?.trim().orEmpty()
        if (text.isEmpty() || text == tab.url) {
            suggestions = emptyList()
        } else {
            delay(SUGGESTION_DELAY_MS)
            suggestions = suggest(text)
        }
    }
    val focusManager = LocalFocusManager.current
    val openSuggestion: (Suggestion) -> Unit = { suggestion ->
        (suggestion.url ?: AddressInput.toAddress(suggestion.text, searchTemplate))?.let { address ->
            tab.load(address)
            draft.value = null
            suggestions = emptyList()
            // As after Go: the page takes the focus, and the keyboard closes.
            focusManager.clearFocus(force = true)
            tab.webView.requestFocus()
        }
    }
    // Leaving the Start Page gives up an address being typed, and the keyboard closes.
    val leaveStartPage = {
        draft.value = null
        suggestions = emptyList()
        focusManager.clearFocus(force = true)
    }
    // What a Private Tab shows can't be captured (SPEC, "Private Tabs").
    SecureWindow(private)

    // One line a row or two, as the settings say, for every Tab list in the window.
    CompositionLocalProvider(
        LocalCompactTabs provides settings.settings.compactTabs,
        LocalPullToRefresh provides settings.settings.pullToRefresh,
    ) {
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
                        onPanel = {
                            // A panel takes the page's place, so an address being typed is given up.
                            draft.value = null
                            focusManager.clearFocus(force = true)
                            panel = it
                        },
                        onSetDefaultBrowser = onSetDefaultBrowser,
                        onFind = {
                            draft.value = null
                            tab.openFind()
                        },
                        onDesktopSite = links.setDesktopSite,
                    )
                },
                findBar = if (tab.find != null) {
                    { modifier, shape -> FindBar(tab, shape, modifier) }
                } else {
                    null
                },
                pagePrompt = {
                    val started = downloading
                    val savedFile = links.pageFileSaved
                    val behind = links.openedBehind
                    val handoff = tab.handoff
                    if (started != null) {
                        DownloadStartedPrompt(
                            started,
                            onShow = {
                                panel = Panel.Downloads
                                downloading = null
                            },
                            onDismiss = { downloading = null },
                        )
                    } else if (savedFile != null) {
                        PageFileSavedPrompt(
                            savedFile.first,
                            savedFile.second,
                            // A Private Tab's files aren't on the list.
                            onShow = if (private) null else ({
                                panel = Panel.Downloads
                                links.dismissPageFileSaved()
                            }),
                            onDismiss = links.dismissPageFileSaved,
                        )
                    } else if (handoff != null) {
                        HandoffPrompt(
                            appName = remember(handoff) { handoff.appName(context) },
                            canOpen = handoff.openable,
                            onOpen = {
                                if (handoff.open(context)) tab.dismissHandoff() else tab.offerHandoff(handoff.unopenable())
                            },
                            onDismiss = tab::dismissHandoff,
                        )
                    } else if (behind != null) {
                        // A note, not a question: it goes by itself.
                        LaunchedEffect(behind) {
                            delay(OPENED_BEHIND_MS)
                            links.dismissOpenedBehind()
                        }
                        OpenedBehindPrompt(
                            onShow = {
                                tabs.select(behind.id)
                                links.dismissOpenedBehind()
                            },
                            onDismiss = links.dismissOpenedBehind,
                        )
                    } else when (val offer = tabs.linkOffer) {
                        // With one Profile there's nowhere to move the page to, so nothing to offer.
                        is LinkOffer.Opened -> if (profiles.all.size > 1) {
                            IncomingLinkPrompt(
                                profiles.active.name,
                                onMove = { pick = ProfilePick.Move },
                                onDismiss = tabs.dismissIncomingLink,
                                byRule = offer.rule != null,
                            )
                        }
                        is LinkOffer.Always ->
                            SiteRuleOfferPrompt(offer.pattern.text, offer.profile.name, onAlways = tabs.acceptRuleOffer, onDismiss = tabs.dismissIncomingLink)
                        is LinkOffer.RuleSaved -> SiteRuleSavedPrompt(
                            offer.pattern.text,
                            offer.profile.name,
                            onShow = {
                                panel = Panel.SiteRules
                                tabs.dismissIncomingLink()
                            },
                            onDismiss = tabs.dismissIncomingLink,
                        )
                        null -> Unit
                    }
                },
                suggestions = if (suggestions.isNotEmpty() && draft.value != null) {
                    { modifier -> SuggestionList(suggestions, openSuggestion, modifier) }
                } else {
                    null
                },
                // A Tab without a web page shows the Start Page in the page's place. A dead
                // renderer's message and a popup come first, though neither is likely there.
                startPage = if (StartPage.shows(tab.url) && !tab.crashed && tab.popup == null) {
                    { padding, insets ->
                        StartPage(
                            profile = profiles.active,
                            private = private,
                            pages = startPagePages,
                            onOpenPinned = { bookmark ->
                                leaveStartPage()
                                tabs.pins.openHere(bookmark)
                            },
                            onOpenPage = { address ->
                                leaveStartPage()
                                tab.load(address)
                            },
                            onShowHistory = {
                                leaveStartPage()
                                panel = Panel.History
                            },
                            contentPadding = padding,
                            contentInsets = insets,
                        )
                    }
                } else {
                    null
                },
                panel = panel?.let { open ->
                    { padding ->
                        if (open == Panel.Settings) {
                            SettingsPanel(
                                settings,
                                onClose = { panel = null },
                                contentPadding = padding,
                                siteRules = siteRules.rules.size,
                                onSiteRules = { panel = Panel.SiteRules },
                                profileName = profiles.active.name,
                                sites = siteSettings.sites.size,
                                onSiteSettings = { panel = Panel.SiteSettings },
                            )
                        } else if (open == Panel.SiteSettings) {
                            SiteSettingsPanel(
                                siteSettings,
                                profileName = profiles.active.name,
                                onBack = { panel = Panel.Settings },
                                onClose = { panel = null },
                                contentPadding = padding,
                            )
                        } else if (open == Panel.SiteRules) {
                            SiteRulesPanel(
                                siteRules,
                                profiles,
                                // A Private Tab's page is named nowhere outside it.
                                pageUrl = if (private) null else tab.url,
                                onBack = { panel = Panel.Settings },
                                onClose = { panel = null },
                                contentPadding = padding,
                            )
                        } else LibraryPanel(
                            panel = open,
                            library = library,
                            profileName = profiles.active.name,
                            onOpen = {
                                tab.load(it)
                                panel = null
                            },
                            onClose = { panel = null },
                            contentPadding = padding,
                            pins = tabs.pins,
                            onOpenBookmark = {
                                tabs.pins.open(it)
                                panel = null
                            },
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
            // Over everything, while the page's video is fullscreen.
            FullscreenVideoHost(tab)
        }
    }
    }

    // What the page asks for: permissions, files and a sign-in.
    PageAsks(tab, profiles.active.name, private, onRemember = siteSettings.rememberPermission)

    // A page can only offer a file. It downloads once the person agrees.
    tab.download?.let { request ->
        DownloadDialog(
            request,
            onDownload = {
                library.startDownload(request)
                // A Private Tab's download isn't on the list, so there is nothing to show there.
                // A file the page made says once it is saved.
                if (!private && request.fromWeb) downloading = request.fileName
                // The page keeps its copy of a file it made until Okapi has read it.
                tab.dismissDownload(kept = request.fromPage)
            },
            onDismiss = tab::dismissDownload,
        )
    }

    tab.pressed?.let { item ->
        LinkMenu(
            item,
            private = private,
            canUseOtherProfile = profiles.all.size > 1,
            links = links,
            onSaveImage = { tab.offerFile(it) },
            onPickProfile = { linkToPick = it },
            onDismiss = tab::dismissPressed,
            scope = scope,
        )
    }

    linkToPick?.let { url ->
        ProfilePickerDialog(
            pick = ProfilePick.OpenIn,
            others = profiles.all.filter { it.id != profiles.active.id },
            onChoose = { target ->
                links.openInProfile(target, url)
                linkToPick = null
            },
            onDismiss = { linkToPick = null },
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

/** How long "Opened in a new Tab" stays before it goes by itself. */
private const val OPENED_BEHIND_MS = 4_000L

/** How long typing has to pause before suggestions are asked for. */
private const val SUGGESTION_DELAY_MS = 150L

/** From this window width up, the Bottom Bar shows the Forward button itself. */
private val BOTTOM_BAR_FORWARD_MIN_WIDTH = 600.dp
