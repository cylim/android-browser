package app.jrny.browser.tab

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import app.jrny.browser.address.WebAddress
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadSource
import app.jrny.browser.engine.Engine
import app.jrny.browser.engine.PageBridge
import app.jrny.browser.engine.PageFile
import app.jrny.browser.handoff.AppLink
import app.jrny.browser.handoff.HandoffRules
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.ScrollDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/**
 * A single open page, owned by exactly one Profile. It owns a GeckoSession in the Profile's
 * session context ([WebProfile]), and exposes the page's state as Compose state.
 *
 * A session's context can't change once it is open, so a Tab never changes Profile. Moving a
 * page to another Profile creates a new Tab (ADR 0003).
 *
 * Create, use and destroy a Tab on the main thread. [onPageChanged] runs whenever [url] or
 * [title] changes, even while nothing is drawn, so the Tab can be saved. [onVisited] runs for
 * each page the person goes to, for the history: not for a reload, Back or Forward, and not for a
 * saved page coming back. [onPageSeen] follows for the same page, once it has a title and again
 * when it has finished loading, with the title it has then (none, for a page that never names
 * itself). A page that only passes the person on to another one, a redirect's stop, is never
 * reported as seen.
 */
class Tab(
    private val context: Context,
    val profile: WebProfile,
    private val onPageChanged: (Tab) -> Unit = {},
    private val onVisited: (url: String) -> Unit = {},
    private val onPageSeen: (url: String, title: String) -> Unit = { _, _ -> },
    /** The page at [url] has shown its site's icon. */
    private val onIcon: (url: String, icon: Bitmap) -> Unit = { _, _ -> },
    /**
     * The page asks for the camera, the microphone or location. The owner answers it, or shows
     * it with [showPermissionAsk]. Without an owner every ask is refused.
     */
    private val onPermissionAsk: (Tab, PermissionAsk) -> Unit = { _, ask -> ask.deny() },
    /**
     * The page opened a link that belongs to another app ([tapped] when the person tapped it).
     * The owner hands it over, or offers it with [offerHandoff]. Without an owner it is offered.
     */
    private val onHandoff: (Tab, AppLink, tapped: Boolean) -> Unit = { tab, link, _ -> tab.offerHandoff(link) },
    /** Whether the Tab's Profile asks [SiteOrigin] for its desktop version (SPEC, "Desktop site"). */
    private val desktopFor: (SiteOrigin) -> Boolean = { false },
    /** Whether pages are shown dark, darkened when they have no dark theme of their own (SPEC, "Dark pages"). */
    darkPages: Boolean = false,
) {
    private val runtime = Engine.runtime(context)
    private val main = Handler(Looper.getMainLooper())

    /** Whether pages are shown dark. It reaches the page that is open at once. */
    var darkPages: Boolean = darkPages
        set(value) {
            field = value
            bridge.dark = value
            popupBridge?.dark = value
        }

    /** The Desktop site choices a Private Tab made: it keeps them only here. A normal Tab follows its Profile's. */
    private val desktopHere = mutableMapOf<SiteOrigin, Boolean>()

    /** True while the Tab asks the shown page's site for its desktop version. */
    var desktopSite by mutableStateOf(false)
        private set

    /**
     * Asks [origin] for its desktop version, or its mobile one, from now on in this Tab only: a
     * Private Tab's choice (SPEC, "Desktop site"). A normal Tab follows its Profile's choices,
     * which the owner keeps, and hears of them through [desktopChoicesChanged].
     */
    fun setDesktopSite(origin: SiteOrigin, on: Boolean) {
        desktopHere[origin] = on
        desktopChoicesChanged()
    }

    /**
     * The site's choice may have changed (it was set, reset, or has just been read at start): the
     * page loads again as its site wants it now, if that isn't how it was loaded.
     */
    fun desktopChoicesChanged() {
        val shown = url ?: return
        val desktop = wantsDesktop(SiteOrigin.of(shown))
        if (desktop == desktopSite && isDesktop(session) == desktop) return
        applyMode(session, desktop)
        if (!crashed && WebAddress.isWebAddress(shown)) session.reload()
    }

    private fun wantsDesktop(origin: SiteOrigin?): Boolean = origin != null && (desktopHere[origin] ?: desktopFor(origin))

    private fun isDesktop(of: GeckoSession) = of.settings.userAgentMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP

    /** Asks for desktop pages, with a desktop user agent and width, or mobile ones, from the next load on. */
    private fun applyMode(of: GeckoSession, desktop: Boolean) {
        if (of == session) desktopSite = desktop
        if (isDesktop(of) == desktop) return
        of.settings.userAgentMode = if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        of.settings.viewportMode = if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }

    /** What Find in page is looking for and has found, or null while it is closed (SPEC, "Find in page"). */
    var find: FindState? by mutableStateOf(null)
        private set

    /** Opens Find in page, empty. */
    fun openFind() {
        if (find == null) find = FindState("", 0, 0)
    }

    /** Looks for [text] in the page and highlights every match. */
    fun find(text: String) {
        find = FindState(text, 0, 0)
        val finder = session.finder
        if (text.isEmpty()) return finder.clear()
        finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        finder.find(text, 0).accept { result -> showFound(text, result) }
    }

    /** Moves to the next match, or the one before with [forward] false. */
    fun findNext(forward: Boolean) {
        val query = find?.query
        if (query.isNullOrEmpty()) return
        session.finder.find(null, if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS).accept { result -> showFound(query, result) }
    }

    private fun showFound(query: String, result: GeckoSession.FinderResult?) {
        val open = find ?: return
        if (open.query != query || result == null) return
        find = open.copy(current = if (result.total == 0) 0 else result.current.coerceAtLeast(1), total = result.total)
    }

    /** Closes Find in page and clears its highlights. */
    fun closeFind() {
        if (find == null) return
        find = null
        session.finder.clear()
    }

    /** What the page's own scripts ask the person: an alert, a question, a list to choose from. */
    var prompt: PagePrompt? by mutableStateOf(null)
        private set

    /** The shown [prompt] has been answered. */
    fun closePrompt() {
        prompt = null
    }

    /** Talks to the page's content script: its icon, dark pages, files it can fetch. */
    private var bridge: PageBridge = newBridge()

    private var popupBridge: PageBridge? = null

    /**
     * The session showing this Tab. It is replaced with a new one if its content process dies, so
     * observe it rather than keeping a reference.
     */
    var session: GeckoSession by mutableStateOf(newSession(popup = false).also { open(it, bridge) })
        private set

    /**
     * A window the page opened with `window.open`, such as a "Sign in with Google" popup. It
     * shows over the page in the same Profile, and closes with Back or `window.close()`. It is
     * set once GeckoView has opened it, so it can be shown.
     */
    var popup: GeckoSession? by mutableStateOf(null)
        private set

    /** The popup GeckoView is opening, until it is open and becomes [popup]. */
    private var openingPopup: GeckoSession? = null

    /**
     * The [popup]'s own address, which the browser shows above it. Without it, a popup could put
     * a sign-in form under the page's address.
     */
    var popupUrl: String? by mutableStateOf(null)
        private set

    /**
     * A file the page offers for download, until the person has answered. Only the response's
     * headers have arrived: nothing of the file is read before they agree.
     */
    var download: DownloadRequest? by mutableStateOf(null)
        private set

    /**
     * What a site asked for and the person must answer, or, when [PermissionAsk.decided], only
     * Android's permission is left to check. Shown by the owner with [showPermissionAsk].
     */
    var permissionAsk: PermissionAsk? by mutableStateOf(null)
        private set

    /** The page's file field waits for files, until [answerFileChooser]. */
    var fileChooser: FileChooserAsk? by mutableStateOf(null)
        private set

    /** A site asks for a username and password before it sends the page, until [closeHttpAuth]. */
    var httpAuth: HttpAuthAsk? by mutableStateOf(null)
        private set

    /** The page shows a video fullscreen, until [exitFullscreen] or the page ends it. */
    var fullscreen: FullscreenVideo? by mutableStateOf(null)
        private set

    /**
     * A link to another app the page opened, offered under the page with "Open" until the person
     * answers or the page moves on (SPEC, "Links to other apps").
     */
    var handoff: AppLink? by mutableStateOf(null)
        private set

    fun offerHandoff(link: AppLink) {
        handoff = link
    }

    /** The page that opened the offered link: only another page's start ends the offer. */
    private var handoffPage: String? = null

    fun dismissHandoff() {
        handoff = null
    }

    /** A link or an image the person is holding on the page, for its menu (SPEC, "Link and image menu"). */
    var pressed: PressedItem? by mutableStateOf(null)
        private set

    fun dismissPressed() {
        pressed = null
    }

    /**
     * Offers the file at [url] for download, as if the page had: the person agrees first. Used for
     * "Save image": the image is then fetched from the page, as the page would fetch it.
     */
    fun offerFile(url: String, mimeType: String = "") {
        val name = DownloadRequest.safeFileName(URLUtil.guessFileName(url, null, mimeType.ifBlank { null }))
        val page = loadedUrl?.takeIf { WebAddress.isWebAddress(it) }?.substringBefore('#')
        download?.let(::discard)
        download = DownloadRequest(url, name, mimeType, -1, page, DownloadSource.FromPage)
    }

    /**
     * Runs [script] in the page's content script world, which sees the page's DOM, and gives its
     * result as JSON, or null when there is no page. Debug builds only, for tests. Call it on the
     * main thread.
     */
    suspend fun evaluate(script: String): String? = bridge.evaluate(script)

    /** Runs [script] in the [popup]'s page, as [evaluate] does in the Tab's. Null without a popup. */
    suspend fun evaluateInPopup(script: String): String? = popupBridge?.evaluate(script)

    /** Fetches [url] as the page would, with the Tab's Profile, or null when it can't be had. */
    suspend fun fetchFromPage(url: String): PageFile? = bridge.fetch(url)

    /** What the page asked with, so it can withdraw [permissionAsk]. */
    private var permissionSource: Any? = null

    /**
     * What sites were allowed once, for this Tab only (SPEC, "Site permissions and Site
     * settings"). It goes when the page leaves the site, and with the Tab.
     */
    private val allowedOnce = mutableMapOf<SiteOrigin, MutableSet<SiteSetting>>()

    /** The site of the last page that started, for [allowedOnce]. */
    private var pageOrigin: SiteOrigin? = null

    /** Lets [origin] use [settings] until the page leaves the site or the Tab closes. */
    fun allowOnce(origin: SiteOrigin, settings: Set<SiteSetting>) {
        allowedOnce.getOrPut(origin) { mutableSetOf() } += settings
    }

    fun isAllowedOnce(origin: SiteOrigin, setting: SiteSetting): Boolean = allowedOnce[origin]?.contains(setting) == true

    /** Shows [ask] for the person to answer, unless the page withdrew it meanwhile. */
    fun showPermissionAsk(ask: PermissionAsk) {
        if (ask.open) permissionAsk = ask
    }

    /** The shown [permissionAsk] has been answered. */
    fun closePermissionAsk() {
        permissionAsk = null
        permissionSource = null
        pendingAsk = null
    }

    /**
     * The ask the owner is deciding, from when the page made it until it is answered or shown. It
     * holds the Tab's one place for an ask while the owner reads the Profile's Site settings, so a
     * second ask meanwhile is refused.
     */
    private var pendingAsk: PermissionAsk? = null

    /** Hands [files] to the page's file field, or none when the person cancelled. */
    fun answerFileChooser(files: List<Uri>?) {
        val ask = fileChooser ?: return
        fileChooser = null
        ask.answer(files)
    }

    /** The [httpAuth] ask has been answered. */
    fun closeHttpAuth() {
        httpAuth = null
    }

    /** Ends [fullscreen]: the video goes back into its page. */
    fun exitFullscreen() {
        val video = fullscreen ?: return
        fullscreen = null
        video.end()
    }

    /**
     * Measures the video that is fullscreen: its width and height, or null when it can't be told,
     * as when it plays inside a frame of another site.
     */
    suspend fun measureFullscreenVideo(): Pair<Int, Int>? = bridge.measureFullscreenVideo()

    /** Refuses or drops whatever the page is waiting on, when the page or the Tab goes away. */
    private fun endAsks() {
        permissionAsk?.deny()
        pendingAsk?.deny()
        closePermissionAsk()
        answerFileChooser(null)
        httpAuth?.cancel()
        closeHttpAuth()
        prompt?.dismiss()
        prompt = null
        exitFullscreen()
        handoff = null
        pressed = null
    }

    /** The page's address, or null before anything has loaded. Set as soon as a load starts. */
    var url: String? by mutableStateOf(null)
        private set
    var title by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** Load progress from 0 to 100. */
    var progress by mutableIntStateOf(0)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    /** True after the page's content process stopped. [reload] loads the page again. */
    var crashed by mutableStateOf(false)
        private set

    /** The page's own title, or null when it has none (or only repeats the address). */
    val pageTitle: String? get() = pageTitle(title, url)

    /** The site's host for display, without a leading `www.`, or empty for a non-web page. */
    val displayHost: String get() = displayHost(url)

    /** How far the page is scrolled down, in pixels, as GeckoView last said. */
    private var pageScrollY = 0

    /**
     * The page's vertical scroll position, in pixels, to save with the Tab. While a restored page
     * is still loading, or waits for a reload after a crash, it's the position the page goes back
     * to, so saving the Tab in that moment doesn't lose it.
     */
    val scrollY: Int
        get() = if (restoreScrollY > 0) restoreScrollY else pageScrollY

    /** True while the page is scrolled to its very top, so a pull down can reload it. */
    val atTop: Boolean get() = pageScrollY <= 0

    /** Where to scroll once the page being restored has loaded and is laid out, or 0. */
    private var restoreScrollY = 0

    /** True once the restored page has loaded. The restore ends when the next page starts. */
    private var restoreDone = false

    /** True from [restore] until the restored page has loaded: that load is not a new visit. */
    private var restoring = false

    /**
     * The saved title of the page being restored. The page's own title replaces it on screen, and
     * it comes back if the page fails to load.
     */
    private var restoringTitle: String? = null

    /** True once the page's own load failed, while Okapi's error page shows in its place. */
    private var loadFailed = false

    /** Loads [address] in the Tab. A popup over the page closes, so the new page is what shows. */
    fun load(address: String) {
        closePopup()
        applyMode(session, wantsDesktop(SiteOrigin.of(address)))
        crashed = false
        restoreScrollY = 0
        restoreDone = false
        restoring = false
        restoringTitle = null
        addressRequested = true
        url = address
        onPageChanged(this)
        loadInto(session, address)
    }

    /**
     * Brings back a saved Tab: loads [address] and, once it has loaded, scrolls to [scrollY].
     * The page itself is never saved, so it loads afresh (SPEC, "Tabs"). The Tab keeps its saved
     * [title] until the page reports its own, so a page that fails to load doesn't lose its name.
     */
    fun restore(address: String, title: String, scrollY: Int) {
        this.title = title
        load(address)
        restoring = true
        restoringTitle = title
        restoreScrollY = scrollY
    }

    fun goBack() {
        if (!canGoBack) return
        historyStep = true
        historyStepDirection = -1
        session.goBack()
    }

    fun goForward() {
        if (!canGoForward) return
        historyStep = true
        historyStepDirection = 1
        session.goForward()
    }

    /**
     * The address of the page that is loaded now. [url] can already be ahead of it, at the
     * address a load was asked for, while this page and its title are still what shows.
     */
    private var loadedUrl: String? = null

    /** True from Back or Forward until that page arrives: going back to a page isn't a new visit. */
    private var historyStep = false

    /** True from a page load starting until the new page takes over. A move within one page never sets it. */
    private var pageStarting = false

    /** True from [load] until its page arrives: the address came from the person, not from a link on the page. */
    private var addressRequested = false

    /** True from [reload] until the page arrives again. */
    private var reloading = false

    /** The page that has just stopped loading, reported as seen unless another page starts first. */
    private var pendingSeen: Runnable? = null

    /** True when the page loading next is one the person tapped a link to. */
    private var nextLoadByPerson = false

    /** True while a new session loads its own empty page first. */
    private var blankStarted = false

    /** The title the Tab had when the page now loading started, for when it turns out to be a file. */
    private var titleBeforeStart: String? = null

    /** The Tab's back/forward list, with each page's title, as GeckoView last told it. */
    private var history: GeckoSession.HistoryDelegate.HistoryList? = null

    /**
     * The title of the page Back or Forward goes to. A page that comes back from the back-forward
     * cache doesn't name itself again.
     */
    private fun historyStepTitle(): String? {
        if (!historyStep) return null
        val list = history ?: return null
        val target = list.currentIndex + historyStepDirection
        return list.getOrNull(target)?.title?.takeIf { it.isNotBlank() }
    }

    /** -1 for Back, 1 for Forward, while [historyStep]. */
    private var historyStepDirection = 0

    fun reload() {
        val address = url
        if (crashed && address != null) {
            // The new session loads the page afresh, like a saved page coming back: it keeps its
            // title, scrolls back to where the old one was, and is no new visit.
            restore(address, title, restoreScrollY)
        } else if (loadFailed && address != null && WebAddress.isWebAddress(address)) {
            // The error page is what's loaded: the page itself is asked for again.
            loadInto(session, address)
        } else {
            reloading = true
            session.reload()
        }
    }

    fun stop() = session.stop()

    /**
     * Loads [address], which the person or Okapi asked for. GeckoView refuses a `data:` page at
     * the top of a Tab unless the load says it may: only pages can't open one by themselves.
     */
    private fun loadInto(target: GeckoSession, address: String) {
        // A load still on its way, such as a restored page, must not arrive after this one.
        target.stop()
        val flags = if (address.startsWith("data:", ignoreCase = true)) GeckoSession.LOAD_FLAGS_FORCE_ALLOW_DATA_URI else GeckoSession.LOAD_FLAGS_NONE
        target.load(GeckoSession.Loader().uri(address).flags(flags))
    }

    /** Ends the [download] offer, after the person has answered it. Declining closes its response. */
    fun dismissDownload(kept: Boolean = false) {
        val offered = download ?: return
        download = null
        if (!kept) discard(offered)
    }

    private fun discard(request: DownloadRequest) {
        (request.source as? DownloadSource.Response)?.discard()
    }

    /** Closes the [popup], if one is open. */
    fun closePopup() {
        val window = popup ?: openingPopup ?: return
        popup = null
        openingPopup = null
        popupUrl = null
        popupBridge?.detach()
        popupBridge = null
        if (window.isOpen) window.close()
    }

    /** Frees the sessions. The Tab can't be used afterwards. */
    fun destroy() {
        pendingSeen?.let(main::removeCallbacks)
        endAsks()
        download?.let(::discard)
        download = null
        find = null
        allowedOnce.clear()
        closePopup()
        bridge.detach()
        session.stop()
        session.close()
    }

    /** Asks the page to print, through Android's print dialog, which can also save a PDF. */
    fun print(then: (java.io.InputStream?) -> Unit) {
        session.saveAsPdf().accept({ then(it) }, { then(null) })
    }

    private fun newBridge() = PageBridge(onIcon = { page, icon ->
        // The page's own, not an error page's.
        if (!loadFailed && page == loadedUrl) onIcon(page, icon)
    }).also { it.dark = darkPages }

    private fun newSession(popup: Boolean): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            // The Profile's own store (ADR 0004), in memory only for a Private Tab.
            .contextId(profile.name)
            .usePrivateMode(profile.isPrivate)
            .useTrackingProtection(false)
            .build()
        val created = GeckoSession(settings)
        created.navigationDelegate = if (popup) PopupNavigation() else PageNavigation()
        created.contentDelegate = if (popup) PopupContent() else PageContent()
        created.promptDelegate = Prompts()
        created.permissionDelegate = Permissions()
        if (!popup) {
            created.progressDelegate = PageProgress()
            created.scrollDelegate = PageScroll()
            created.historyDelegate = object : GeckoSession.HistoryDelegate {
                override fun onHistoryStateChange(session: GeckoSession, historyList: GeckoSession.HistoryDelegate.HistoryList) {
                    if (session == this@Tab.session) history = historyList
                }
            }
        }
        return created
    }

    /** True for the Tab's own session and its open popup: the pages the person can see. */
    private fun shows(session: GeckoSession): Boolean = session == this.session || session == popup

    /**
     * The site [session] shows, for display. A popup that has no address of its own yet shows what
     * its opener wrote into it, so it counts as the opener's site.
     */
    private fun siteOf(session: GeckoSession): String =
        if (session == popup) displayHost(popupUrl).ifEmpty { displayHost } else displayHost

    private fun open(session: GeckoSession, bridge: PageBridge) {
        session.open(runtime)
        bridge.attach(session)
    }

    /**
     * Scrolls the restored page to [scrollY] once it has loaded. The page may not be laid out yet
     * when GeckoView says it has stopped loading, so it tries again a moment later.
     */
    private fun scrollWhenLaidOut(scrollY: Int) {
        val target = session
        fun attempt(left: Int) {
            if (target != session || !restoreDone || restoreScrollY != scrollY) return
            target.panZoomController.scrollTo(ScreenLength.zero(), ScreenLength.fromPixels(scrollY.toDouble()), PanZoomController.SCROLL_BEHAVIOR_AUTO)
            if (left > 0) {
                main.postDelayed({ if (pageScrollY < scrollY) attempt(left - 1) else restoreScrollY = 0 }, SCROLL_RETRY_MS)
            } else {
                restoreScrollY = 0
            }
        }
        main.postDelayed({ attempt(SCROLL_RETRIES) }, SCROLL_RETRY_MS)
    }

    /**
     * A link turned out to be a file, not a page. The Tab offers the download and still shows the
     * page before, so its address and loading state go back to that page. A popup that only
     * started a download has nothing to show, and closes.
     */
    private fun offerDownload(from: GeckoSession, response: WebResponse) {
        val request = downloadRequest(response)
        when (from) {
            session -> {
                // The page before is still what shows, with its title.
                titleBeforeStart?.let { title = it }
                isLoading = false
                pageStarting = false
                addressRequested = false
                url = loadedUrl
                onPageChanged(this)
            }
            popup, openingPopup -> closePopup()
            else -> return discard(request)
        }
        download?.let(::discard)
        download = request
    }

    private fun downloadRequest(response: WebResponse): DownloadRequest {
        fun header(name: String) = response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val disposition = header("Content-Disposition")
        val mimeType = header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
        val size = header("Content-Length")?.trim()?.toLongOrNull() ?: -1
        val suggested = DownloadRequest.suggestedFileName(disposition)
            ?: URLUtil.guessFileName(response.uri, disposition, mimeType.ifBlank { null })
        // The page the link was on: only a web page, without its fragment.
        val page = loadedUrl?.takeIf { !addressRequested && WebAddress.isWebAddress(it) }?.substringBefore('#')
        return DownloadRequest(response.uri, DownloadRequest.safeFileName(suggested), mimeType, size, page, DownloadSource.Response(response.body))
    }

    /**
     * The content process stopped (a crash, or Android reclaiming memory). The session can't be
     * used again. The Tab gets a fresh one and waits for the person to reload, so a page that
     * crashes doesn't crash it in a loop.
     */
    private fun replaceDeadSession(dead: GeckoSession) {
        if (dead != session) return
        restoreScrollY = scrollY
        endAsks()
        closePopup()
        bridge.detach()
        bridge = newBridge()
        history = null
        val fresh = newSession(popup = false)
        open(fresh, bridge)
        applyMode(fresh, desktopSite)
        session = fresh
        crashed = true
        isLoading = false
        canGoBack = false
        canGoForward = false
    }

    /** Only these load as a Tab's page. Anything else is handed to another app, or dropped. */
    private fun loadsHere(uri: String): Boolean {
        val scheme = schemeOf(uri)
        return WebAddress.isWebScheme(scheme) || scheme == "data" || scheme == "blob" || uri == "about:blank"
    }

    /**
     * Decides a page's load: a web page loads, asking its site for the desktop or mobile version
     * as the Profile wants; a link for another app, opened by the page itself, goes to the owner
     * to hand over (SPEC, "Links to other apps"). Anything else is dropped.
     */
    private fun decideLoad(from: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
        // GeckoView asks once more, with no address, while it turns a response into a download.
        if (request.uri.isBlank()) return GeckoResult.allow()
        if (loadsHere(request.uri)) {
            if (from == session && !request.isRedirect) nextLoadByPerson = request.hasUserGesture
            if (from == session && WebAddress.isWebAddress(request.uri)) applyMode(from, wantsDesktop(SiteOrigin.of(request.uri)))
            return GeckoResult.allow()
        }
        val scheme = schemeOf(request.uri)
        if (HandoffRules.isForAnotherApp(scheme)) {
            AppLink.of(request.uri)?.let {
                handoffPage = loadedUrl
                onHandoff(this, it, request.hasUserGesture)
            }
        }
        return GeckoResult.deny()
    }

    /** Navigation of the Tab's page. */
    private inner class PageNavigation : NavigationDelegate {
        override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> =
            decideLoad(session, request)

        override fun onSubframeLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> =
            if (loadsHere(request.uri) || request.uri.startsWith("about:")) GeckoResult.allow() else GeckoResult.deny()

        override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
            if (session != this@Tab.session || url == null || !loadsHere(url)) return
            // Okapi's error page shows in the failed page's place: the address stays the page's.
            if (ErrorPages.isErrorPage(url) || (loadFailed && url == "about:blank")) return
            // A new session starts at an empty page of its own, which no one went to.
            if (url == "about:blank" && this@Tab.url != "about:blank") return
            if (loadFailed) {
                // GeckoView names the failed page itself while the error page shows. No visit.
                this@Tab.url = url
                pageStarting = false
                return onPageChanged(this@Tab)
            }
            // Also comes for a move within the page (history.pushState), which starts no new page.
            val newPage = pageStarting
            val isReload = reloading || (newPage && url == loadedUrl)
            reloading = false
            this@Tab.url = url
            loadedUrl = url
            pageStarting = false
            addressRequested = false
            onPageChanged(this@Tab)
            val step = historyStep
            historyStep = false
            if (!isReload && !restoring && !step) {
                onVisited(url)
                // A move within the page keeps the page's title, and nothing else will report it.
                // A new page's title can come before its address does: it is reported now.
                if (title.isNotBlank()) onPageSeen(url, title)
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            if (session == this@Tab.session) this@Tab.canGoBack = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            if (session == this@Tab.session) this@Tab.canGoForward = canGoForward
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            if (session != this@Tab.session) return null
            closePopup()
            // Same Profile as the page, so the popup sees the page's sign-ins.
            val window = newSession(popup = true)
            openingPopup = window
            popupUrl = uri.ifBlank { "about:blank" }
            val windowBridge = newBridge()
            popupBridge = windowBridge
            // GeckoView opens the session itself once it has it, and only then can it be shown.
            fun shownWhenOpen(left: Int) {
                if (openingPopup != window) return
                if (window.isOpen) {
                    openingPopup = null
                    popup = window
                    windowBridge.attach(window)
                } else if (left > 0) {
                    main.postDelayed({ shownWhenOpen(left - 1) }, POPUP_OPEN_POLL_MS)
                } else {
                    closePopup()
                }
            }
            main.post { shownWhenOpen(POPUP_OPEN_POLLS) }
            return GeckoResult.fromValue(window)
        }

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
            if (session != this@Tab.session || uri == null) return null
            // The page didn't load. Keep the title it was saved with, or none, so the Tab shows its site.
            loadFailed = true
            title = restoringTitle.orEmpty()
            pageStarting = false
            url = uri
            onPageChanged(this@Tab)
            return GeckoResult.fromValue(ErrorPages.page(context, uri, ErrorPages.kindOf(error), darkPages))
        }
    }

    /** Loading of the Tab's page. */
    private inner class PageProgress : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            // A link for another app that was refused, or Okapi's error page, isn't a page start.
            if (session != this@Tab.session || ErrorPages.isErrorPage(url) || !loadsHere(url)) return
            // A new session's own empty page, which no one asked for: it isn't the Tab's page.
            blankStarted = url == "about:blank" && this@Tab.url != "about:blank"
            if (blankStarted) return
            isLoading = true
            progress = 0
            loadFailed = false
            // The page before was seen, unless it sent the person on by itself: the person asked
            // for this one, or tapped a link to it.
            pendingSeen?.let { seen ->
                main.removeCallbacks(seen)
                if (addressRequested || nextLoadByPerson) seen.run()
            }
            pendingSeen = null
            nextLoadByPerson = false
            // A new page starts without a title, not with the one of the page before. A reload
            // and a saved page coming back keep theirs. Cleared here, not when the page arrives:
            // GeckoView can report the new page's title before its address.
            titleBeforeStart = title
            if (!reloading && url != loadedUrl && !restoring) title = historyStepTitle().orEmpty()
            pageStarting = true
            if (restoreDone) {
                // The restored page had loaded, and this is the next page.
                restoreScrollY = 0
                restoringTitle = null
                restoreDone = false
            }
            if (url != "about:blank") this@Tab.url = url
            // Find in page belongs to the page it was opened on.
            if (find != null && url != loadedUrl) closeFind()
            // An offer to open another app belongs to the page that made it.
            if (url != handoffPage) handoff = null
            // A grant for once ends when the page leaves its site.
            val origin = SiteOrigin.of(url)
            if (origin != pageOrigin) allowedOnce.clear()
            pageOrigin = origin
            onPageChanged(this@Tab)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            if (session == this@Tab.session) this@Tab.progress = progress
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            if (session != this@Tab.session) return
            if (blankStarted) {
                blankStarted = false
                return
            }
            isLoading = false
            pageStarting = false
            // The address was a file, not a page: the Tab goes back to the page it shows.
            if (download != null && url == download?.url) {
                url = loadedUrl
                addressRequested = false
            }
            onPageChanged(this@Tab)
            if (loadFailed) return
            // Seen once it has stayed a moment: a page whose script sends the person on as it loads
            // is a redirect's stop, and the next page starting cancels this.
            loadedUrl?.let { page ->
                val seen = Runnable { if (loadedUrl == page && !pageStarting) onPageSeen(page, title) }
                pendingSeen = seen
                main.postDelayed(seen, REDIRECT_GRACE_MS)
            }
            // A page that failed keeps its saved title and position for a reload.
            if (!restoreDone && restoring) {
                restoreDone = true
                restoring = false
                if (restoreScrollY > 0) scrollWhenLaidOut(restoreScrollY)
            }
        }
    }

    private inner class PageScroll : ScrollDelegate {
        override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
            if (session == this@Tab.session) pageScrollY = scrollY
        }
    }

    /** The Tab's page: its title, files, the link menu, fullscreen and crashes. */
    private inner class PageContent : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            if (session != this@Tab.session || loadFailed) return
            // A restored page keeps its saved title until it has one of its own.
            if (restoringTitle != null && title.isNullOrBlank()) return
            this@Tab.title = title.orEmpty()
            onPageChanged(this@Tab)
            // Before the new page has arrived, its address isn't known yet: it is reported then.
            if (!title.isNullOrBlank() && !pageStarting) loadedUrl?.let { onPageSeen(it, title) }
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) = offerDownload(session, response)

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
            if (session != this@Tab.session) return
            // Holding a link or an image opens its menu; anything else keeps GeckoView's own
            // handling, such as selecting text.
            val link = element.linkUri?.takeIf { it.isNotBlank() }
            val image = element.srcUri?.takeIf { it.isNotBlank() && element.type == ContentDelegate.ContextElement.TYPE_IMAGE }
            if (link == null && image == null) return
            pressed = PressedItem(link = link, image = image)
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            if (session != this@Tab.session) return
            fullscreen = if (fullScreen) fullscreen ?: FullscreenVideo { session.exitFullScreen() } else null
        }

        override fun onCrash(session: GeckoSession) = replaceDeadSession(session)

        override fun onKill(session: GeckoSession) = replaceDeadSession(session)
    }

    /** Navigation of a popup: its address, and a popup of its own loads in it. */
    private inner class PopupNavigation : NavigationDelegate {
        override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
            if (request.target == NavigationDelegate.TARGET_WINDOW_NEW && loadsHere(request.uri)) {
                main.post { if (session == popup) session.loadUri(request.uri) }
                return GeckoResult.deny()
            }
            return decideLoad(session, request)
        }

        override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
            if ((session == popup || session == openingPopup) && url != null && !ErrorPages.isErrorPage(url)) popupUrl = url
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? =
            uri?.let { GeckoResult.fromValue(ErrorPages.page(context, it, ErrorPages.kindOf(error), darkPages)) }
    }

    /** Lets a popup close itself with `window.close()`, as sign-in popups do when they finish. */
    private inner class PopupContent : ContentDelegate {
        override fun onCloseRequest(session: GeckoSession) {
            if (session == popup || session == openingPopup) closePopup()
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) = offerDownload(session, response)

        override fun onCrash(session: GeckoSession) {
            if (session == popup) closePopup()
        }

        override fun onKill(session: GeckoSession) {
            if (session == popup) closePopup()
        }
    }

    /**
     * Passes a site's ask to the owner. A page that asks again while its ask is still shown is
     * refused: one question at a time.
     */
    private fun raise(ask: PermissionAsk, source: Any) {
        if (permissionAsk != null || pendingAsk?.open == true) return ask.deny()
        permissionSource = source
        pendingAsk = ask
        onPermissionAsk(this, ask)
    }

    /** What the page and its popup ask to use. */
    private inner class Permissions : PermissionDelegate {
        /**
         * GeckoView asks for Android's own permissions before it asks about the site. Okapi asks
         * Android only once the person allowed the site, so it says yes here and asks later.
         */
        override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback) =
            callback.grant()

        override fun onContentPermissionRequest(session: GeckoSession, perm: PermissionDelegate.ContentPermission): GeckoResult<Int> {
            val answer = GeckoResult<Int>()
            val allow = PermissionDelegate.ContentPermission.VALUE_ALLOW
            val deny = PermissionDelegate.ContentPermission.VALUE_DENY
            when (perm.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> {
                    val site = SiteOrigin.of(perm.uri)
                    if (site == null || !shows(session)) {
                        answer.complete(deny)
                    } else {
                        // GeckoView never remembers the answer: Site settings do.
                        val ask = PermissionAsk(
                            site,
                            setOf(SiteSetting.Location),
                            onGrant = { answer.complete(allow); forget(perm) },
                            onDeny = { answer.complete(deny); forget(perm) },
                        )
                        raise(ask, perm)
                    }
                }
                // A video with sound waits for a tap; one without plays.
                PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> answer.complete(allow)
                // Protected video (DRM), as on streaming sites.
                PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> answer.complete(allow)
                // An embedded sign-in asking for its own cookies under Total Cookie Protection.
                PermissionDelegate.PERMISSION_STORAGE_ACCESS -> answer.complete(allow)
                else -> answer.complete(deny)
            }
            return answer
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<out PermissionDelegate.MediaSource>?,
            audio: Array<out PermissionDelegate.MediaSource>?,
            callback: PermissionDelegate.MediaCallback,
        ) {
            // The camera and microphone are passed on. Sharing the screen isn't supported.
            val camera = video?.firstOrNull { it.source == PermissionDelegate.MediaSource.SOURCE_CAMERA }
            val microphone = audio?.firstOrNull { it.source == PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
            val origin = SiteOrigin.of(uri)
            val asked = (video != null && camera == null) || (audio != null && microphone == null)
            if (origin == null || asked || (camera == null && microphone == null) || !shows(session)) {
                return callback.reject()
            }
            val wanted = buildSet {
                if (camera != null) add(SiteSetting.Camera)
                if (microphone != null) add(SiteSetting.Microphone)
            }
            raise(PermissionAsk(origin, wanted, onGrant = { callback.grant(camera, microphone) }, onDeny = callback::reject), callback)
        }

        /** The answer was for this request only: the next one asks again. */
        private fun forget(perm: PermissionDelegate.ContentPermission) {
            main.post { runCatching { runtime.storageController.setPermission(perm, PermissionDelegate.ContentPermission.VALUE_PROMPT) } }
        }
    }

    /** What the page's scripts and forms ask the person, for the page and its popup. */
    private inner class Prompts : PromptDelegate {
        private fun <P : PromptDelegate.BasePrompt> show(session: GeckoSession, prompt: P, make: (PromptAnswer<P>) -> PagePrompt): GeckoResult<PromptResponse> {
            val answer = PromptAnswer(prompt)
            // One at a time, and only from a page that is shown.
            if (this@Tab.prompt != null || !shows(session)) {
                answer.dismiss()
                return answer.result
            }
            val shown = make(answer)
            shown.site = siteOf(session)
            prompt.delegate = object : PromptDelegate.PromptInstanceDelegate {
                override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                    if (this@Tab.prompt === shown) this@Tab.prompt = null
                }
            }
            this@Tab.prompt = shown
            return answer.result
        }

        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) = show(session, prompt) { PagePrompt.Alert(it) }

        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) = show(session, prompt) { PagePrompt.Confirm(it) }

        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) = show(session, prompt) { PagePrompt.Text(it) }

        override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) = show(session, prompt) { PagePrompt.LeavePage(it) }

        override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) = show(session, prompt) { PagePrompt.Resend(it) }

        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) = show(session, prompt) { PagePrompt.Choice(it) }

        override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt) = show(session, prompt) { PagePrompt.DateTime(it) }

        override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) = show(session, prompt) { PagePrompt.Color(it) }

        /** A pop-up the page opened without a tap stays blocked. */
        override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse> =
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

        /** The page's own Share button, through Android's share sheet. */
        override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt): GeckoResult<PromptResponse> {
            val text = listOfNotNull(prompt.text?.takeIf { it.isNotBlank() }, prompt.uri?.takeIf { it.isNotBlank() }).joinToString(" ")
            if (text.isEmpty()) return GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.FAILURE))
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .apply { prompt.title?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_TITLE, it) } }
            return try {
                context.startActivity(Intent.createChooser(send, null))
                GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.SUCCESS))
            } catch (_: ActivityNotFoundException) {
                GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.FAILURE))
            }
        }

        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
            val answer = PromptAnswer(prompt)
            // One at a time, files only (not a folder), and only from a page that is shown.
            if (fileChooser != null || prompt.type == PromptDelegate.FilePrompt.Type.FOLDER || !shows(session)) {
                answer.dismiss()
            } else {
                fileChooser = FileChooserAsk(context, answer)
            }
            return answer.result
        }

        override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse> {
            val answer = PromptAnswer(prompt)
            if (!shows(session)) {
                answer.dismiss()
                return answer.result
            }
            httpAuth?.cancel()
            val host = prompt.authOptions.uri.orEmpty().toUri().host.orEmpty().ifEmpty { prompt.authOptions.uri.orEmpty() }
            // The site's own words, its realm, are the part of the message in quotes.
            val realm = REALM.find(prompt.message.orEmpty())?.groupValues?.get(1).orEmpty()
            httpAuth = HttpAuthAsk(host, realm, answer)
            return answer.result
        }
    }

    private companion object {
        /** The scheme of any address, `mailto:` and `data:` too, lowercased. */
        fun schemeOf(uri: String): String? = runCatching { uri.toUri().scheme?.lowercase() }.getOrNull()

        const val SCROLL_RETRIES = 5

        /** How long a page has to stay, after it stops loading, to count as seen and not a redirect's stop. */
        const val REDIRECT_GRACE_MS = 500L
        const val SCROLL_RETRY_MS = 150L
        const val POPUP_OPEN_POLLS = 100
        const val POPUP_OPEN_POLL_MS = 20L

        /** The realm in GeckoView's sign-in message: “…”. */
        val REALM = Regex("[“\"]([^”\"]*)[”\"]")
    }
}

private typealias Uri = android.net.Uri

/** What Find in page looks for, and which of its matches is the current one (1-based, 0 for none) of [total]. */
data class FindState(val query: String, val current: Int, val total: Int)

/** What the person is holding on the page: a link, an image, or an image that is a link. */
data class PressedItem(val link: String?, val image: String?)
