package app.jrny.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.jrny.browser.address.WebAddress
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadSource
import app.jrny.browser.tab.FileChooserAsk
import app.jrny.browser.tab.PagePrompt
import app.jrny.browser.tab.PromptAnswer
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebResponse
import kotlin.concurrent.thread
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * uBlock Origin, built into the engine as Okapi's content blocker (ADR 0008). It ships unchanged
 * in the APK's assets (app/build.gradle.kts) and blocks in every Profile and in Private Tabs.
 *
 * The person controls it with its own pages: its popup, for the shown Tab's site, and its
 * dashboard, with any page it opens from there. They are uBlock Origin's, so it keeps one list of
 * trusted sites and one set of settings for every Profile: it can't tell which Profile a request
 * comes from.
 */
object ContentBlocker {
    /** Where the build puts uBlock Origin in the app's assets. */
    const val LOCATION = "resource://android/assets/ublock/"
    const val ID = "uBlock0@raymondhill.net"

    private var runtime: GeckoRuntime? = null

    // The application's context, which lives as long as the process: no leak.
    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null

    /** For copying a chosen file, which outlives the page that asked for it only briefly. */
    private val scope = MainScope()

    /** uBlock Origin once it is installed, else null. */
    var extension: WebExtension? by mutableStateOf(null)
        private set

    /** Its button, which opens the popup. Set once uBlock Origin has said what it is. */
    private var action: WebExtension.Action? by mutableStateOf(null)

    /** True once the popup can open. */
    val ready: Boolean get() = extension != null && action != null

    /** The popup for the shown Tab, while it is open. */
    var popup: GeckoSession? by mutableStateOf(null)
        private set

    /** uBlock Origin's other pages open in Okapi, the shown one last. */
    val pages = mutableStateListOf<GeckoSession>()

    /** Opens a web address that one of its pages links to, in a Tab. The app sets it. */
    var openWeb: (String) -> Unit = {}

    /** What one of its pages asks in a dialog, such as whether to restore a backup. One at a time. */
    var prompt: PagePrompt? by mutableStateOf(null)
        private set

    /** One of its file fields, waiting for the person to choose a file to import. */
    var fileField: FileChooserAsk? by mutableStateOf(null)
        private set

    /** A file one of its pages made, such as a backup, while the person chooses where it goes. */
    var fileToSave: DownloadRequest? by mutableStateOf(null)
        private set

    /** Installs uBlock Origin, or updates it to the version in the APK, and lets it work in Private Tabs. */
    fun install(runtime: GeckoRuntime, context: Context) {
        this.runtime = runtime
        this.context = context.applicationContext
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, ID).accept({ installed ->
            if (installed == null) return@accept
            use(installed)
            if (!installed.metaData.allowedInPrivateBrowsing) {
                controller.setAllowedInPrivateBrowsing(installed, true).accept({ allowed -> if (allowed != null) use(allowed) }, {})
            }
        }, {
            // Pages still load, unblocked. Nothing else depends on it.
        })
    }

    private fun use(installed: WebExtension) {
        extension = installed
        installed.setActionDelegate(Actions)
        installed.setTabDelegate(NewPages)
    }

    /** Opens the popup for the shown Tab. */
    fun openPopup() {
        action?.click()
    }

    fun closePopup() {
        popup?.close()
        popup = null
        endAsksWhenClosed()
    }

    /** Opens the dashboard: uBlock Origin's settings, filter lists, own filters and trusted sites. */
    fun openDashboard() {
        val url = extension?.metaData?.optionsPageUrl ?: return
        pages += newSession().apply { loadUri(url) }
    }

    /** Closes the shown page, which shows the one under it, if any. */
    fun closePage() {
        pages.removeLastOrNull()?.close()
        endAsksWhenClosed()
    }

    fun closePages() {
        pages.forEach(GeckoSession::close)
        pages.clear()
        endAsksWhenClosed()
    }

    /** A dialog or file field outlives no page of uBlock Origin's: with none open, it goes. */
    private fun endAsksWhenClosed() {
        if (popup != null || pages.isNotEmpty()) return
        prompt?.dismiss()
        prompt = null
        fileField?.cancel()
        fileField = null
    }

    fun closePrompt() {
        prompt = null
    }

    /** Hands the chosen file to the file field, or none when the person cancelled. */
    fun answerFileField(file: Uri?) {
        val field = fileField ?: return
        fileField = null
        if (file == null) field.cancel() else scope.launch { field.answer(listOf(file)) }
    }

    /** Writes [fileToSave] to [target], or drops it when the person cancelled. */
    fun saveFile(target: Uri?) {
        val file = fileToSave ?: return
        fileToSave = null
        val body = (file.source as? DownloadSource.Response) ?: return
        val app = context
        if (target == null || app == null) return body.discard()
        thread {
            runCatching {
                body.body?.use { input -> app.contentResolver.openOutputStream(target)?.use { input.copyTo(it) } }
            }
        }
    }

    private fun close(session: GeckoSession) {
        if (session == popup) {
            closePopup()
        } else if (pages.remove(session)) {
            session.close()
            endAsksWhenClosed()
        }
    }

    /** A session for one of its pages. It isn't any Profile's: the page is uBlock Origin's own. */
    private fun newSession(): GeckoSession = GeckoSession().apply {
        navigationDelegate = Navigation
        contentDelegate = Content
        promptDelegate = Prompts
        open(checkNotNull(runtime))
        extension?.let { webExtensionController.setTabDelegate(it, PageTabs) }
    }

    private object Actions : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            // The default button; one for a single Tab only changes its badge.
            if (session == null) this@ContentBlocker.action = action
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
            if (popup != null) {
                closePopup()
                return null
            }
            return onOpenPopup(extension, action)
        }

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> {
            popup?.close()
            val session = newSession()
            popup = session
            return GeckoResult.fromValue(session)
        }
    }

    private object NewPages : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession> {
            // GeckoView hands over the address but leaves loading it to the app.
            val session = newSession()
            createDetails.url?.let(session::loadUri)
            pages += session
            return GeckoResult.fromValue(session)
        }

        override fun onOpenOptionsPage(source: WebExtension) = openDashboard()
    }

    private object PageTabs : WebExtension.SessionTabDelegate {
        override fun onCloseTab(source: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
            close(session)
            return GeckoResult.allow()
        }
    }

    /** Its pages stay its own: a link to a web page opens in a Tab instead, and nothing else loads. */
    private object Navigation : NavigationDelegate {
        override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
            val uri = request.uri
            if (uri.startsWith(extension?.metaData?.baseUrl ?: return GeckoResult.deny())) return GeckoResult.allow()
            if (WebAddress.isWebScheme(WebAddress.schemeOf(uri))) {
                openWeb(uri)
                closePopup()
                closePages()
            }
            return GeckoResult.deny()
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null
    }

    private object Content : ContentDelegate {
        override fun onCloseRequest(session: GeckoSession) = close(session)

        /** A file it made, such as a backup or exported filters. Okapi asks where to save it. */
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            (fileToSave?.source as? DownloadSource.Response)?.discard()
            fileToSave = DownloadRequest.of(response, null)
        }

        override fun onCrash(session: GeckoSession) = close(session)

        override fun onKill(session: GeckoSession) = close(session)
    }

    /** Its dialogs and file fields, shown by Okapi as a page's are, one at a time. */
    private object Prompts : PromptDelegate {
        private fun <P : PromptDelegate.BasePrompt> show(prompt: P, make: (PromptAnswer<P>) -> PagePrompt): GeckoResult<PromptResponse> {
            val answer = PromptAnswer(prompt)
            if (this@ContentBlocker.prompt != null || fileField != null) {
                answer.dismiss()
                return answer.result
            }
            val shown = make(answer)
            prompt.delegate = object : PromptDelegate.PromptInstanceDelegate {
                override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                    if (this@ContentBlocker.prompt === shown) this@ContentBlocker.prompt = null
                }
            }
            this@ContentBlocker.prompt = shown
            return answer.result
        }

        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) = show(prompt) { PagePrompt.Alert(it) }

        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) = show(prompt) { PagePrompt.Confirm(it) }

        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) = show(prompt) { PagePrompt.Text(it) }

        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) = show(prompt) { PagePrompt.Choice(it) }

        override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) = show(prompt) { PagePrompt.Color(it) }

        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
            val answer = PromptAnswer(prompt)
            val app = context
            if (this@ContentBlocker.prompt != null || fileField != null || app == null || prompt.type != PromptDelegate.FilePrompt.Type.SINGLE) {
                answer.dismiss()
            } else {
                fileField = FileChooserAsk(app, answer)
            }
            return answer.result
        }
    }
}
