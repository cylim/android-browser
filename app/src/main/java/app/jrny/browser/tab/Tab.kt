package app.jrny.browser.tab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import app.jrny.browser.address.WebAddress
import app.jrny.browser.profile.WebProfile

/**
 * A single open page, owned by exactly one Profile. It owns a WebView bound to the Profile's
 * [WebProfile], and exposes the page's state as Compose state.
 *
 * A WebView's profile can't change after it is first used, so a Tab never changes Profile.
 * Moving a page to another Profile creates a new Tab (ADR 0003).
 *
 * Create, use and destroy a Tab on the main thread, and only when `WebViewFeature.MULTI_PROFILE`
 * is supported (MainActivity checks). [onPageChanged] runs whenever [url] or [title] changes, even
 * while nothing is drawn, so the Tab can be saved.
 */
@SuppressLint("RequiresFeature")
class Tab(
    private val context: Context,
    val profile: WebProfile,
    private val onPageChanged: (Tab) -> Unit = {},
) {

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

    /** The page's vertical scroll position, in pixels, to save with the Tab. */
    val scrollY: Int
        get() = webView.scrollY

    /** Where to scroll once the page being restored has loaded, or 0. */
    private var restoreScrollY = 0

    /** The saved title of the page being restored, which stays if that page fails to load. */
    private var restoringTitle: String? = null

    /** True once the page's own load failed. WebView then shows its error page, whose title isn't the page's. */
    private var loadFailed = false

    /** Loads [address] in the Tab. A popup over the page closes, so the new page is what shows. */
    fun load(address: String) {
        closePopup()
        crashed = false
        restoreScrollY = 0
        restoringTitle = null
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
        restoringTitle = title
        restoreScrollY = scrollY
    }

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    fun reload() {
        val address = url
        if (crashed && address != null) load(address) else webView.reload()
    }

    fun stop() = webView.stopLoading()

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
        closePopup()
        // WebView must leave the view hierarchy before destroy().
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(popup: Boolean): WebView {
        val view = WebView(context)
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
        // Sign-in flows rely on third-party cookies (the Milestone 0 spike signed in with them on).
        profile.cookieManager.setAcceptThirdPartyCookies(view, true)
        view.webViewClient = if (popup) PopupViewClient() else PageViewClient()
        view.webChromeClient = if (popup) PopupChromeClient() else PageChromeClient()
        return view
    }

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
    private fun replaceDeadWebView(dead: WebView) {
        if (dead != webView) return
        closePopup()
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        webView = newWebView(popup = false)
        crashed = true
        isLoading = false
        canGoBack = false
        canGoForward = false
    }

    /** Only web pages load. Handing other schemes to apps comes with Incoming Links (Milestone 4). */
    private fun isBlocked(request: WebResourceRequest) = !WebAddress.isWebScheme(request.url.scheme)

    /** Navigation callbacks for the Tab's page. */
    private inner class PageViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = isBlocked(request)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view != webView) return
            isLoading = true
            loadFailed = false
            this@Tab.url = url
            updateHistory()
            onPageChanged(this@Tab)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view != webView) return
            isLoading = false
            this@Tab.url = view.url ?: url
            updateHistory()
            onPageChanged(this@Tab)
            if (restoreScrollY > 0) {
                view.scrollTo(0, restoreScrollY)
                restoreScrollY = 0
            }
            restoringTitle = null
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
            updateHistory()
            onPageChanged(this@Tab)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            replaceDeadWebView(view)
            return true
        }
    }

    /** Page callbacks shared by the page and its popup. */
    private open inner class QuietChromeClient : WebChromeClient() {
        /**
         * Keeps page console output out of the system log. WebView would otherwise log every
         * message with the page's address (SPEC, "Data protection"). Debug builds can read the
         * console through chrome://inspect.
         */
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
    }

    /** Progress, title and popups for the Tab's page. */
    private inner class PageChromeClient : QuietChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view == webView) progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view != webView || loadFailed) return
            this@Tab.title = title.orEmpty()
            onPageChanged(this@Tab)
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
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = isBlocked(request)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view == popup) popupUrl = url
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
}
