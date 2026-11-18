package app.jrny.browser.tab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.jrny.browser.R
import app.jrny.browser.address.WebAddress
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.PageFiles
import app.jrny.browser.handoff.AppLink
import app.jrny.browser.handoff.HandoffRules
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting

/**
 * A single open page, owned by exactly one Profile. It owns a WebView bound to the Profile's
 * [WebProfile], and exposes the page's state as Compose state.
 *
 * A WebView's profile can't change after it is first used, so a Tab never changes Profile.
 * Moving a page to another Profile creates a new Tab (ADR 0003).
 *
 * Create, use and destroy a Tab on the main thread, and only when `WebViewFeature.MULTI_PROFILE`
 * is supported (MainActivity checks). [onPageChanged] runs whenever [url] or [title] changes, even
 * while nothing is drawn, so the Tab can be saved. [onVisited] runs for each page the person
 * goes to, for the history: not for a reload, Back or Forward, and not for a saved page coming
 * back. [onPageSeen] follows for the same page, once it has a title and again when it has
 * finished loading, with the title it has then (none, for a page that never names itself). A page
 * that only passes the person on to another one, a redirect's stop, is never reported as seen.
 */
@SuppressLint("RequiresFeature")
class Tab(
    private val context: Context,
    val profile: WebProfile,
    private val onPageChanged: (Tab) -> Unit = {},
    private val onVisited: (url: String) -> Unit = {},
    private val onPageSeen: (url: String, title: String) -> Unit = { _, _ -> },
    safeBrowsing: Boolean = true,
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
    /** How large page text is, in percent (SPEC, "Text size"). */
    textZoom: Int = 100,
    /** Whether pages are shown dark, darkened when they have no dark theme of their own (SPEC, "Dark pages"). */
    darkPages: Boolean = false,
) {
    /** How large page text is, in percent. It reaches pages that are already open. */
    var textZoom: Int = textZoom
        set(value) {
            field = value
            webView.settings.textZoom = value
            popup?.settings?.textZoom = value
        }

    /**
     * Whether pages are shown dark. WebView takes this from the theme of the context it was made
     * with, so a change gives the Tab a new WebView, which loads the page again.
     */
    var darkPages: Boolean = darkPages
        set(value) {
            if (field == value) return
            field = value
            rebuildWebView()
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
     * page loads again with the agent its site wants now, if that isn't the one it was loaded with.
     */
    fun desktopChoicesChanged() {
        val shown = url ?: return
        val desktop = wantsDesktop(SiteOrigin.of(shown))
        if (desktop == desktopSite && UserAgents.isDesktop(webView.settings.userAgentString) == desktop) return
        applyUserAgent(webView, desktop)
        if (!crashed) webView.reload()
    }

    private fun wantsDesktop(origin: SiteOrigin?): Boolean = origin != null && (desktopHere[origin] ?: desktopFor(origin))

    /** Sends the desktop user agent, or WebView's own, from the next request on. */
    private fun applyUserAgent(view: WebView, desktop: Boolean) {
        if (view == webView) desktopSite = desktop
        val settings = view.settings
        if (UserAgents.isDesktop(settings.userAgentString) == desktop) return
        settings.userAgentString = if (desktop) UserAgents.desktop(WebSettings.getDefaultUserAgent(context)) else null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
            WebSettingsCompat.setUserAgentMetadata(
                settings,
                UserAgentMetadata.Builder(metadata).setMobile(!desktop).setPlatform(if (desktop) "Linux" else "Android").build(),
            )
        }
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
        if (text.isEmpty()) webView.clearMatches() else webView.findAllAsync(text)
    }

    /** Moves to the next match, or the one before with [forward] false. */
    fun findNext(forward: Boolean) {
        if (find?.query.isNullOrEmpty()) return
        webView.findNext(forward)
    }

    /** Closes Find in page and clears its highlights. */
    fun closeFind() {
        if (find == null) return
        find = null
        webView.clearMatches()
    }
    /**
     * Whether WebView checks pages against Safe Browsing and warns before a dangerous one
     * (SPEC, "Browsing features"). It follows the app's setting, also for pages already open.
     */
    var safeBrowsing: Boolean = safeBrowsing
        set(value) {
            field = value
            applySafeBrowsing(webView)
            popup?.let(::applySafeBrowsing)
        }

    private fun applySafeBrowsing(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(view.settings, safeBrowsing)
        }
    }


    /**
     * The WebView showing this Tab. It is replaced with a new one if the renderer dies, so
     * observe it rather than keeping a reference.
     */
    var webView: WebView by mutableStateOf(newWebView(popup = false))
        private set

    /**
     * A window the page opened with `window.open`, such as a "Sign in with Google" popup. It
     * shows over the page in the same Profile, and closes with Back or `window.close()`.
     */
    var popup: WebView? by mutableStateOf(null)
        private set

    /**
     * The [popup]'s own address, which the browser shows above it. Without it, a popup could put
     * a sign-in form under the page's address.
     */
    var popupUrl: String? by mutableStateOf(null)
        private set

    /**
     * A file the page offers for download, until the person has answered. Nothing is fetched
     * before they agree.
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

    /** The page's video, shown fullscreen until [exitFullscreen] or the page ends it. */
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

    /**
     * The page that opened the offered link. WebView can report that this page started only
     * after the page has already opened the link, so only another page's start ends the offer.
     */
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
     * "Save image". A `blob:` image is copied in the page first, as a page's own file is.
     */
    fun offerFile(url: String, mimeType: String = "") {
        val view = webView
        val name = DownloadRequest.safeFileName(URLUtil.guessFileName(url, null, mimeType.ifBlank { null }))
        val copy = if (url.startsWith("blob:", ignoreCase = true)) keepPageCopy(view, url) else null
        val referrer = shownPage(view)?.takeIf { WebAddress.isWebAddress(it) }?.substringBefore('#')
        download = DownloadRequest(url, view.settings.userAgentString.orEmpty(), name, mimeType, -1, referrer, copy)
    }

    /**
     * Runs [script] in the page and gives its result as JSON, or null when the page has gone.
     * Call it on the main thread.
     */
    suspend fun evaluate(script: String): String? {
        val view = webView
        return suspendCancellableCoroutine { done -> view.evaluateJavascript(script) { done.resume(it) } }
    }

    /**
     * A file the page made has no name of its own: WebView passes on none. The link that offered
     * it may name it (`<a download="name">`), and then the offer takes that name.
     */
    private fun takeNameFromPage(view: WebView, url: String) {
        view.evaluateJavascript(PageFiles.nameOf(url)) { result ->
            val named = result?.let { runCatching { org.json.JSONArray("[$it]").optString(0) }.getOrNull() }?.takeIf { it.isNotBlank() && it != "null" }
                ?: return@evaluateJavascript
            val offered = download?.takeIf { it.url == url } ?: return@evaluateJavascript
            download = offered.copy(fileName = DownloadRequest.safeFileName(named))
        }
    }

    /** Makes the page keep its own copy of the `blob:` file at [url], and returns the copy's name. */
    private fun keepPageCopy(view: WebView, url: String): String {
        val name = PageFiles.newCopyName()
        view.evaluateJavascript(PageFiles.keep(url, name), null)
        return name
    }

    /** What WebView asked with, so the page can withdraw [permissionAsk]: a request, or a location origin. */
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
     * second ask meanwhile is refused, and a withdrawal reaches it.
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
    fun measureFullscreenVideo(result: (Pair<Int, Int>?) -> Unit) {
        webView.evaluateJavascript(MEASURE_FULLSCREEN_VIDEO) { value ->
            val numbers = value?.trim('[', ']', '"')?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            result(numbers?.takeIf { it.size == 2 && it[0] > 0 && it[1] > 0 }?.let { it[0] to it[1] })
        }
    }

    /** Refuses or drops whatever the page is waiting on, when the page or the Tab goes away. */
    private fun endAsks() {
        permissionAsk?.deny()
        closePermissionAsk()
        answerFileChooser(null)
        httpAuth?.cancel()
        closeHttpAuth()
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
    /** True after the page's renderer stopped. [reload] loads the page again. */
    var crashed by mutableStateOf(false)
        private set

    /** The page's own title, or null when it has none (or only repeats the address). */
    val pageTitle: String? get() = pageTitle(title, url)

    /** The site's host for display, without a leading `www.`, or empty for a non-web page. */
    val displayHost: String get() = displayHost(url)

    /**
     * The page's vertical scroll position, in pixels, to save with the Tab. While a restored page
     * is still loading, or waits for a reload after a crash, it's the position the page goes back
     * to, so saving the Tab in that moment doesn't lose it.
     */
    val scrollY: Int
        get() = if (restoreScrollY > 0) restoreScrollY else webView.scrollY

    /** Where to scroll once the page being restored has loaded and is laid out, or 0. */
    private var restoreScrollY = 0

    /**
     * True once the restored page has loaded. The restore ends when the next page starts. Until
     * then a failure, in whatever order WebView reports it, still brings back the saved title.
     */
    private var restoreDone = false

    /** True from [restore] until the restored page has loaded: that load is not a new visit. */
    private var restoring = false

    /**
     * The saved title of the page being restored. The page's own title replaces it on screen, and
     * it comes back if the page fails to load.
     */
    private var restoringTitle: String? = null

    /** True once the page's own load failed. WebView then shows its error page, whose title isn't the page's. */
    private var loadFailed = false

    /** Loads [address] in the Tab. A popup over the page closes, so the new page is what shows. */
    fun load(address: String) {
        closePopup()
        applyUserAgent(webView, wantsDesktop(SiteOrigin.of(address)))
        crashed = false
        restoreScrollY = 0
        restoreDone = false
        restoring = false
        restoringTitle = null
        addressRequested = true
        url = address
        onPageChanged(this)
        webView.loadUrl(address)
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
        if (!webView.canGoBack()) return
        historyStep = true
        webView.goBack()
    }

    fun goForward() {
        if (!webView.canGoForward()) return
        historyStep = true
        webView.goForward()
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

    fun reload() {
        val address = url
        if (crashed && address != null) {
            // The new WebView loads the page afresh, like a saved page coming back: it keeps its
            // title, scrolls back to where the old one was, and is no new visit.
            restore(address, title, restoreScrollY)
        } else {
            webView.reload()
        }
    }

    fun stop() = webView.stopLoading()

    /**
     * Ends the [download] offer, after the person has answered it. When they declined, the page's
     * copy of a file it made goes too; [kept] leaves it for Okapi to read.
     */
    fun dismissDownload(kept: Boolean = false) {
        val offered = download ?: return
        download = null
        if (!kept) offered.pageCopy?.let { webView.evaluateJavascript(PageFiles.drop(it), null) }
    }

    /** Closes the [popup], if one is open. */
    fun closePopup() {
        val view = popup ?: return
        popup = null
        popupUrl = null
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    /** Frees the WebViews. The Tab can't be used afterwards. */
    fun destroy() {
        endAsks()
        find = null
        allowedOnce.clear()
        closePopup()
        // WebView must leave the view hierarchy before destroy().
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(popup: Boolean): WebView {
        // WebView shows pages dark when the theme of its context is dark.
        val view = WebView(if (darkPages) ContextThemeWrapper(context, R.style.Theme_Jrny_DarkPages) else context)
        // Must be the first call on a new WebView: the profile is fixed once the WebView is used.
        WebViewCompat.setProfile(view, profile.name)
        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Pages can't read the app's files or content providers.
            allowFileAccess = false
            allowContentAccess = false
            // Pinch zoom without the on-screen +/- buttons.
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            // The page may open one popup (onCreateWindow). A popup's own window.open loads in
            // the popup. Without a tap, window.open stays blocked
            // (javaScriptCanOpenWindowsAutomatically is false by default).
            setSupportMultipleWindows(!popup)
        }
        applySafeBrowsing(view)
        view.settings.textZoom = textZoom
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, darkPages)
        }
        // A desktop width for pages that ask for the device's, while the Tab asks for desktop sites.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, UserAgents.DESKTOP_VIEWPORT_SCRIPT, setOf("*"))
        }
        if (!popup) view.setFindListener { active, matches, _ -> find?.let { find = it.copy(current = if (matches == 0) 0 else active + 1, total = matches) } }
        // Sign-in flows rely on third-party cookies (the Milestone 0 spike signed in with them on).
        profile.cookieManager.setAcceptThirdPartyCookies(view, true)
        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val suggested = DownloadRequest.suggestedFileName(contentDisposition) ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
            val name = DownloadRequest.safeFileName(suggested)
            // The page the link was on, as a page's own request for the file would say: only a
            // web page, without its fragment, which never leaves the browser. An address the
            // person typed, or one another app sent, was linked from nowhere.
            val referrer = shownPage(view)?.takeIf { !addressRequested && WebAddress.isWebAddress(it) && it != url }?.substringBefore('#')
            // A file the page made itself may be dropped by the page at once: it keeps a copy
            // until the person answers.
            val copy = if (url.startsWith("blob:", ignoreCase = true)) keepPageCopy(view, url) else null
            offerDownload(view, DownloadRequest(url, userAgent.orEmpty(), name, mimeType.orEmpty(), contentLength, referrer, copy))
            if (!WebAddress.isWebAddress(url)) takeNameFromPage(view, url)
        }
        if (!popup) view.setOnLongClickListener { longPress(view) }
        view.webViewClient = if (popup) PopupViewClient() else PageViewClient()
        view.webChromeClient = if (popup) PopupChromeClient() else PageChromeClient()
        return view
    }

    /**
     * Scrolls the restored page to [scrollY] once it's ready to draw. When the load finishes the
     * page may not be laid out yet, and scrolling then does nothing.
     */
    private fun scrollWhenLaidOut(view: WebView, scrollY: Int) {
        view.postVisualStateCallback(
            0,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (view != webView || !restoreDone || restoreScrollY != scrollY) return
                    view.scrollTo(0, scrollY)
                    restoreScrollY = 0
                }
            },
        )
    }

    /**
     * A link turned out to be a file, not a page. The Tab offers the download and still shows the
     * page before, so its address and loading state go back to that page. A popup that only
     * started a download has nothing to show, and closes.
     */
    private fun offerDownload(view: WebView, request: DownloadRequest) {
        when (view) {
            webView -> {
                isLoading = false
                pageStarting = false
                addressRequested = false
                url = shownPage(view)
                updateHistory()
                onPageChanged(this)
            }
            popup -> closePopup()
            else -> return
        }
        download = request
    }

    /**
     * The address of the page [view] really shows: the last one that loaded. `WebView.getUrl` is
     * no use here, because while a load is on its way it already gives the address being loaded.
     */
    private fun shownPage(view: WebView): String? = view.copyBackForwardList().currentItem?.url

    private fun updateHistory() {
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

    /**
     * WebView runs one renderer process for every Tab in every Profile. When it dies (a crash,
     * or Android reclaiming memory), a dead WebView can't be used again, and the app would crash
     * without this. The Tab gets a fresh WebView and waits for the person to reload, so a page
     * that crashes the renderer doesn't crash it in a loop.
     */
    /**
     * Gives the Tab a new WebView that loads the page again where it was, after a change only a
     * new WebView can take (SPEC, "Dark pages"). Back/forward history is lost, as after a kill.
     */
    private fun rebuildWebView() {
        val old = webView
        val address = url
        val savedTitle = title
        val position = scrollY
        endAsks()
        closeFind()
        closePopup()
        (old.parent as? ViewGroup)?.removeView(old)
        old.stopLoading()
        old.destroy()
        webView = newWebView(popup = false)
        canGoBack = false
        canGoForward = false
        isLoading = false
        if (address != null && !crashed) restore(address, savedTitle, position)
    }

    private fun replaceDeadWebView(dead: WebView) {
        if (dead != webView) return
        restoreScrollY = scrollY
        endAsks()
        closePopup()
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        webView = newWebView(popup = false)
        crashed = true
        isLoading = false
        canGoBack = false
        canGoForward = false
    }

    /**
     * Only web pages load in a Tab. A link for another app, opened by the page itself rather than
     * one of its frames, goes to the owner to hand over (SPEC, "Links to other apps"). Anything
     * else is dropped.
     */
    private fun isBlocked(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme
        if (WebAddress.isWebScheme(scheme)) return switchesUserAgent(view, request)
        if (request.isForMainFrame && HandoffRules.isForAnotherApp(scheme)) {
            AppLink.of(request.url.toString())?.let {
                handoffPage = view.url
                onHandoff(this, it, request.hasGesture())
            }
        }
        return true
    }

    /**
     * A page on a site that wants the other user agent than the Tab sends now: the Tab switches,
     * and asks for the page again with it. Only a plain GET in the page itself is asked again; a
     * form's POST keeps the agent it has, so nothing is sent twice.
     */
    private fun switchesUserAgent(view: WebView, request: WebResourceRequest): Boolean {
        if (view != webView || !request.isForMainFrame || request.method != "GET") return false
        val desktop = wantsDesktop(SiteOrigin.of(request.url.toString()))
        if (UserAgents.isDesktop(view.settings.userAgentString) == desktop) {
            desktopSite = desktop
            return false
        }
        applyUserAgent(view, desktop)
        val address = request.url.toString()
        Handler(Looper.getMainLooper()).post { if (view == webView) view.loadUrl(address) }
        return true
    }

    /**
     * Holding a link or an image opens its menu. Anything else keeps WebView's own handling, such
     * as selecting text. An image that is also a link asks WebView for the link's address first.
     */
    private fun longPress(view: WebView): Boolean {
        if (view != webView) return false
        val hit = view.hitTestResult
        val extra = hit.extra?.takeIf { it.isNotBlank() }
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> pressed = PressedItem(link = extra ?: return false, image = null)
            WebView.HitTestResult.IMAGE_TYPE -> pressed = PressedItem(link = null, image = extra ?: return false)
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val reply = Handler(Looper.getMainLooper()) { message ->
                    if (view == webView) pressed = PressedItem(link = message.data.getString("url")?.takeIf { it.isNotBlank() }, image = extra)
                    true
                }.obtainMessage()
                view.requestFocusNodeHref(reply)
            }
            else -> return false
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    /** Navigation callbacks for the Tab's page. */
    private inner class PageViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = isBlocked(view, request)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view != webView) return
            isLoading = true
            loadFailed = false
            pageStarting = true
            if (restoreDone) {
                // The restored page had loaded, and this is the next page.
                restoreScrollY = 0
                restoringTitle = null
                restoreDone = false
            }
            this@Tab.url = url
            // Find in page belongs to the page it was opened on.
            if (find != null && url != loadedUrl) closeFind()
            // An offer to open another app belongs to the page that made it.
            if (url != handoffPage) handoff = null
            // A grant for once ends when the page leaves its site.
            val origin = SiteOrigin.of(url)
            if (origin != pageOrigin) allowedOnce.clear()
            pageOrigin = origin
            updateHistory()
            onPageChanged(this@Tab)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
            askForSignIn(view, handler, host, realm)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view != webView) return
            isLoading = false
            this@Tab.url = view.url ?: url
            updateHistory()
            onPageChanged(this@Tab)
            if (!loadFailed) loadedUrl?.let { onPageSeen(it, title) }
            // A page that failed keeps its saved title and position for a reload.
            if (!loadFailed && !restoreDone && restoring) {
                restoreDone = true
                restoring = false
                if (restoreScrollY > 0) scrollWhenLaidOut(view, restoreScrollY)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view != webView || !request.isForMainFrame) return
            // The page didn't load. Keep the title it was saved with, or none, so the Tab shows
            // its site, instead of the error page's "Webpage not available".
            loadFailed = true
            title = restoringTitle.orEmpty()
            onPageChanged(this@Tab)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (view != webView) return
            // Also fires for same-document navigations (history.pushState), which don't start a page load.
            this@Tab.url = url
            loadedUrl = url
            updateHistory()
            // A new page starts without a title, not with the one of the page before. A reload,
            // a saved page coming back and a move within the page keep theirs.
            val newPage = pageStarting
            pageStarting = false
            addressRequested = false
            if (newPage && !isReload && !restoring) title = ""
            onPageChanged(this@Tab)
            val step = historyStep
            historyStep = false
            if (!isReload && !restoring && !step) {
                onVisited(url)
                // A move within the page (a new address, the same document) keeps the page's
                // title, and nothing else will report it.
                if (!newPage && title.isNotBlank()) onPageSeen(url, title)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            replaceDeadWebView(view)
            return true
        }
    }

    /** Asks the person to sign in to [host], for the page or its popup. An earlier ask is cancelled. */
    private fun askForSignIn(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
        if (view != webView && view != popup) return handler.cancel()
        httpAuth?.cancel()
        httpAuth = HttpAuthAsk(host, realm.orEmpty(), handler)
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

    /** The page withdrew the ask that [source] made. */
    private fun withdraw(source: Any?) {
        if (source == null || source != permissionSource) return
        pendingAsk?.withdraw()
        permissionAsk?.withdraw()
        closePermissionAsk()
    }

    /** Page callbacks shared by the page and its popup. */
    private open inner class QuietChromeClient : WebChromeClient() {
        /**
         * Keeps page console output out of the system log. WebView would otherwise log every
         * message with the page's address (SPEC, "Data protection"). Debug builds can read the
         * console through chrome://inspect.
         */
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            // One at a time. False tells WebView nothing was shown, and it answers the page itself.
            if (fileChooser != null || (view != webView && view != popup)) return false
            fileChooser = FileChooserAsk(callback, params)
            return true
        }
    }

    /** Progress, title and popups for the Tab's page. */
    private inner class PageChromeClient : QuietChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view == webView) progress = newProgress
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            // The page's own, not a popup's, and not an error page's.
            if (view != webView || loadFailed || icon == null) return
            loadedUrl?.let { onIcon(it, icon) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view != webView || loadFailed) return
            // A restored page keeps its saved title until it has one of its own.
            if (restoringTitle != null && title.isNullOrBlank()) return
            this@Tab.title = title.orEmpty()
            onPageChanged(this@Tab)
            if (!title.isNullOrBlank()) loadedUrl?.let { onPageSeen(it, title) }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val known = request.resources.filter { it in MEDIA_RESOURCES }
            val origin = SiteOrigin.of(request.origin?.toString())
            // Only the camera and microphone are passed on. Anything else is refused, as WebView
            // would without this.
            if (known.isEmpty() || origin == null) return request.deny()
            val wanted = known.map { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) SiteSetting.Camera else SiteSetting.Microphone }.toSet()
            raise(PermissionAsk(origin, wanted, onGrant = { request.grant(known.toTypedArray()) }, onDeny = request::deny), request)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) = withdraw(request)

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            val site = SiteOrigin.of(origin) ?: return callback.invoke(origin, false, false)
            // WebView never remembers the answer (retain = false): Site settings do.
            val ask = PermissionAsk(
                site,
                setOf(SiteSetting.Location),
                onGrant = { callback.invoke(origin, true, false) },
                onDeny = { callback.invoke(origin, false, false) },
            )
            raise(ask, origin)
        }

        override fun onGeolocationPermissionsHidePrompt() = withdraw(permissionSource as? String)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullscreen != null) return callback.onCustomViewHidden()
            fullscreen = FullscreenVideo(view, callback)
        }

        override fun onHideCustomView() {
            fullscreen = null
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (view != webView) return false
            closePopup()
            // Same Profile as the page, so the popup sees the page's sign-ins (spike result).
            val window = newWebView(popup = true)
            popup = window
            // A new window starts at about:blank, which reports no page start. Its real address
            // replaces this as soon as it loads.
            popupUrl = "about:blank"
            (resultMsg.obj as WebView.WebViewTransport).webView = window
            resultMsg.sendToTarget()
            return true
        }
    }

    /** Navigation callbacks for a popup. */
    private inner class PopupViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = isBlocked(view, request)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view == popup) popupUrl = url
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
            askForSignIn(view, handler, host, realm)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            // Also catches redirects and same-document navigations inside the popup.
            if (view == popup) popupUrl = url
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view == popup) closePopup()
            return true
        }
    }

    /** Lets a popup close itself with `window.close()`, as sign-in popups do when they finish. */
    private inner class PopupChromeClient : QuietChromeClient() {
        override fun onCloseWindow(window: WebView) {
            if (window == popup) closePopup()
        }
    }

    private companion object {
        val MEDIA_RESOURCES = setOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        /**
         * The size of the video in the fullscreen element, or of the first video inside it, as
         * `[width,height]`, or null. A video in another site's frame can't be seen from here.
         */
        const val MEASURE_FULLSCREEN_VIDEO =
            "(function(){var e=document.fullscreenElement||document.webkitFullscreenElement;if(!e)return null;" +
                "var v=e.tagName==='VIDEO'?e:e.querySelector('video');return v?[v.videoWidth,v.videoHeight]:null})()"
    }
}

/** What Find in page looks for, and which of its matches is the current one (1-based, 0 for none) of [total]. */
data class FindState(val query: String, val current: Int, val total: Int)

/** What the person is holding on the page: a link, an image, or an image that is a link. */
data class PressedItem(val link: String?, val image: String?)
