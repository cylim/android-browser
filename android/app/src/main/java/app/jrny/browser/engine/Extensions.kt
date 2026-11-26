package app.jrny.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.jrny.browser.address.WebAddress
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadSource
import app.jrny.browser.tab.FileChooserAsk
import app.jrny.browser.tab.PagePrompt
import app.jrny.browser.tab.PromptAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse
import java.io.File

/**
 * The extensions the person installed, and the ones Okapi installs by default (ADR 0009). They
 * run in the engine for every Profile at once: GeckoView has one set of extensions per runtime.
 *
 * Okapi shows each extension's own pages: its popup for the shown Tab, in a sheet, and its other
 * pages, such as its settings, in the page's place. It asks the person before an extension is
 * added, before an update that wants more, and before an extension takes an optional permission.
 */
// It holds the application's context, which lives as long as the process: no leak.
@SuppressLint("StaticFieldLeak")
object Extensions {
    /**
     * Installed on the first start, from a signed copy in the APK. After that each is an extension
     * like any other: the person can turn it off or remove it, and a removed one stays removed.
     */
    val DEFAULTS = listOf(
        // uBlock Origin blocks ads and trackers (ADR 0008), in Private Tabs too.
        DefaultExtension("uBlock0@raymondhill.net", "resource://android/assets/extensions/uBlock0@raymondhill.net.xpi", privateTabs = true),
    )

    /** Where people find more: addons.mozilla.org's extensions for Firefox for Android. */
    const val FIND_MORE = "https://addons.mozilla.org/android/"

    private var runtime: GeckoRuntime? = null

    /** The application's context, which lives as long as the process. */
    private var context: Context? = null

    private var prefs: SharedPreferences? = null

    private val scope = MainScope()

    /** Every installed extension except Okapi's own built-in one, by name. Empty until the engine has listed them. */
    val installed = mutableStateListOf<WebExtension>()

    /** Each extension's button, by its id, once it has said what it is. Only these have a popup. */
    val actions = mutableStateMapOf<String, WebExtension.Action>()

    /** An extension's popup for the shown Tab, while it is open. */
    var popup: ExtensionPage? by mutableStateOf(null)
        private set

    /** Extensions' other pages open in Okapi, the shown one last. */
    val pages = mutableStateListOf<ExtensionPage>()

    /** What Okapi asks about an extension: to add it, to let an update or the extension have more. */
    var ask: ExtensionAsk? by mutableStateOf(null)
        private set

    /** What happened that the person should hear about, such as an extension added or refused. */
    var notice: ExtensionNotice? by mutableStateOf(null)
        private set

    /** True while updates are being checked for. */
    var checkingUpdates by mutableStateOf(false)
        private set

    /** Opens a web address that an extension asks for, in a new Tab. The app sets it. */
    var openWeb: (String) -> Unit = {}

    /** What one of the extensions' pages asks in a dialog. One at a time. */
    var prompt: PagePrompt? by mutableStateOf(null)
        private set

    /** One of their file fields, waiting for the person to choose a file to import. */
    var fileField: FileChooserAsk? by mutableStateOf(null)
        private set

    /** A file one of their pages made, such as a backup, while the person chooses where it goes. */
    var fileToSave: DownloadRequest? by mutableStateOf(null)
        private set

    /** The ids of extensions Okapi is adding by default, which need no question. */
    private val addingByDefault = mutableSetOf<String>()

    /**
     * Starts following the engine's extensions: lists them, adds the defaults not added before, and
     * checks for updates once a day.
     */
    fun start(runtime: GeckoRuntime, context: Context) {
        this.runtime = runtime
        this.context = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val controller = runtime.webExtensionController
        controller.promptDelegate = Asks
        controller.setAddonManagerDelegate(Changes)
        controller.list().accept({ all ->
            show(all.orEmpty())
            addDefaults(all.orEmpty())
            if (dueForUpdates()) checkUpdates(quiet = true)
        }, {})
    }

    // ---- What the person does ----

    /** Adds the extension at [url], a web address, after the person agrees to what it asks for. */
    fun install(url: String) = install(url, null)

    /** Adds the extension in the file at [file], which the person chose, after they agree. */
    fun installFile(file: Uri) {
        val app = context ?: return
        scope.launch {
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = File(app.cacheDir, "extension-install").apply { mkdirs() }
                    File(folder, "extension.xpi").also { target ->
                        app.contentResolver.openInputStream(file)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    }
                }.getOrNull()
            }
            if (copy == null || copy.length() == 0L) {
                notice = ExtensionNotice.Failed(null, WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS)
                return@launch
            }
            install(Uri.fromFile(copy).toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE) { copy.delete() }
        }
    }

    fun setEnabled(extension: WebExtension, on: Boolean) {
        val controller = runtime?.webExtensionController ?: return
        val change = if (on) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        change.accept({ refresh() }, { refresh() })
    }

    /** Lets [extension] work in Private Tabs, or not. */
    fun setInPrivateTabs(extension: WebExtension, allowed: Boolean) {
        runtime?.webExtensionController?.setAllowedInPrivateBrowsing(extension, allowed)?.accept({ refresh() }, { refresh() })
    }

    /** Removes [extension] with everything it kept. A default one isn't added again. */
    fun remove(extension: WebExtension) {
        closePagesOf(extension.id)
        runtime?.webExtensionController?.uninstall(extension)?.accept({ refresh() }, { refresh() })
    }

    /** Opens [extension]'s popup for the shown Tab. */
    fun openPopup(extension: WebExtension) {
        actions[extension.id]?.click()
    }

    fun closePopup() {
        popup?.session?.close()
        popup = null
        endAsksWhenClosed()
    }

    /** Opens [extension]'s settings page, if it has one. */
    fun openSettings(extension: WebExtension) {
        val url = extension.metaData.optionsPageUrl ?: return
        pages += ExtensionPage(extension, newSession(extension).apply { loadUri(url) })
    }

    /** Closes the shown page, which shows the one under it, if any. */
    fun closePage() {
        pages.removeLastOrNull()?.session?.close()
        endAsksWhenClosed()
    }

    fun closePages() {
        pages.forEach { it.session.close() }
        pages.clear()
        endAsksWhenClosed()
    }

    /** Checks addons.mozilla.org for a newer version of each extension, and installs it. */
    fun checkUpdates(quiet: Boolean = false) {
        val controller = runtime?.webExtensionController ?: return
        if (checkingUpdates) return
        checkingUpdates = true
        prefs?.edit { putLong(LAST_UPDATE_CHECK, System.currentTimeMillis()) }
        val all = installed.toList()
        var left = all.size
        var updated = 0
        var waiting = 0
        var failed = 0
        fun done() {
            if (--left > 0) return
            checkingUpdates = false
            refresh()
            if (!quiet) notice = if (failed > 0) ExtensionNotice.UpdateCheckFailed else ExtensionNotice.Updated(updated, waiting)
        }
        if (all.isEmpty()) {
            left = 1
            return done()
        }
        all.forEach { extension ->
            controller.update(extension).accept({ newer ->
                if (newer != null && newer.metaData.version != extension.metaData.version) updated++
                done()
            }, { error ->
                // An extension may hold its update until the next start, as uBlock Origin does.
                if ((error as? WebExtension.InstallException)?.code == WebExtension.InstallException.ErrorCodes.ERROR_POSTPONED) waiting++ else failed++
                done()
            })
        }
    }

    fun dismissNotice() {
        notice = null
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
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val input = checkNotNull(body.body)
                    input.use { checkNotNull(app.contentResolver.openOutputStream(target)).use { output -> input.copyTo(output) } }
                }.isSuccess
            }
            // The page's file was read once and is gone: the person makes it again to retry.
            if (!saved) notice = ExtensionNotice.SaveFailed(file.fileName)
        }
    }

    /** True for a response that is an extension to install, such as addons.mozilla.org's "Add to Firefox". */
    fun isExtension(response: WebResponse): Boolean {
        val type = response.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        return type?.substringBefore(';')?.trim().equals(XPI_TYPE, ignoreCase = true)
    }

    // ---- Following the engine ----

    private fun install(url: String, method: String?, then: () -> Unit = {}) {
        val controller = runtime?.webExtensionController ?: return
        controller.install(url, method).accept({ added ->
            then()
            refresh()
            if (added != null) notice = ExtensionNotice.Added(added)
        }, { error ->
            then()
            val code = (error as? WebExtension.InstallException)?.code
            // The person said no: nothing to tell them.
            if (code != WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED) {
                notice = ExtensionNotice.Failed((error as? WebExtension.InstallException)?.extensionName, code)
            }
        })
    }

    private fun addDefaults(present: List<WebExtension>) {
        val controller = runtime?.webExtensionController ?: return
        for (default in DEFAULTS) {
            if (prefs?.getBoolean(OFFERED + default.id, false) == true) continue
            if (present.any { it.id == default.id }) {
                prefs?.edit { putBoolean(OFFERED + default.id, true) }
                continue
            }
            addingByDefault += default.id
            controller.install(default.location, null).accept({ added ->
                addingByDefault -= default.id
                prefs?.edit { putBoolean(OFFERED + default.id, true) }
                if (added != null && default.privateTabs && !added.metaData.allowedInPrivateBrowsing) {
                    controller.setAllowedInPrivateBrowsing(added, true).accept({ refresh() }, { refresh() })
                }
                refresh()
            }, {
                // Tried again at the next start.
                addingByDefault -= default.id
            })
        }
    }

    private fun dueForUpdates(): Boolean =
        System.currentTimeMillis() - (prefs?.getLong(LAST_UPDATE_CHECK, 0) ?: 0) > UPDATE_EVERY_MS

    private fun refresh() {
        runtime?.webExtensionController?.list()?.accept({ show(it.orEmpty()) }, {})
    }

    private fun show(all: List<WebExtension>) {
        val shown = all.filter { !it.isBuiltIn }.sortedBy { it.metaData.name?.lowercase() ?: it.id }
        shown.forEach { extension ->
            extension.setActionDelegate(Actions)
            extension.setTabDelegate(NewPages)
        }
        installed.clear()
        installed += shown
        // An extension that is gone or off has no button.
        actions.keys.retainAll(shown.filter { it.metaData.enabled }.map { it.id }.toSet())
        pages.filter { page -> shown.none { it.id == page.extension.id && it.metaData.enabled } }.forEach { closePagesOf(it.extension.id) }
    }

    private fun closePagesOf(id: String) {
        if (popup?.extension?.id == id) closePopup()
        pages.filter { it.extension.id == id }.forEach {
            it.session.close()
            pages.remove(it)
        }
        endAsksWhenClosed()
    }

    private fun close(session: GeckoSession) {
        if (session == popup?.session) {
            closePopup()
        } else {
            pages.firstOrNull { it.session == session }?.let {
                pages.remove(it)
                session.close()
                endAsksWhenClosed()
            }
        }
    }

    /** A dialog or file field outlives no extension page: with none open, it goes. */
    private fun endAsksWhenClosed() {
        if (popup != null || pages.isNotEmpty()) return
        prompt?.dismiss()
        prompt = null
        fileField?.cancel()
        fileField = null
    }

    /** A session for one of [extension]'s pages. It isn't any Profile's: the page is the extension's own. */
    private fun newSession(extension: WebExtension): GeckoSession = GeckoSession().apply {
        navigationDelegate = Navigation
        contentDelegate = Content
        promptDelegate = Prompts
        open(checkNotNull(runtime))
        webExtensionController.setTabDelegate(extension, PageTabs)
    }

    private object Actions : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            // The default button; one for a single Tab only changes its badge.
            if (session == null) actions[extension.id] = action
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
            if (popup?.extension?.id == extension.id) {
                closePopup()
                return null
            }
            return onOpenPopup(extension, action)
        }

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> {
            popup?.session?.close()
            val page = ExtensionPage(extension, newSession(extension))
            popup = page
            return GeckoResult.fromValue(page.session)
        }
    }

    private object NewPages : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            val url = createDetails.url ?: return null
            // A web page opens as a Tab of the Active Profile, where the person browses.
            if (WebAddress.isWebScheme(WebAddress.schemeOf(url))) {
                openWeb(url)
                closePopup()
                return null
            }
            // GeckoView hands over the address but leaves loading it to the app.
            val session = newSession(source)
            session.loadUri(url)
            pages += ExtensionPage(source, session)
            return GeckoResult.fromValue(session)
        }

        override fun onOpenOptionsPage(source: WebExtension) = openSettings(source)
    }

    private object PageTabs : WebExtension.SessionTabDelegate {
        override fun onCloseTab(source: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
            close(session)
            return GeckoResult.allow()
        }
    }

    /** Their pages stay theirs: a link to a web page opens in a Tab instead, and nothing else loads. */
    private object Navigation : NavigationDelegate {
        override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
            val scheme = WebAddress.schemeOf(request.uri)
            if (scheme.equals(EXTENSION_SCHEME, ignoreCase = true)) return GeckoResult.allow()
            if (WebAddress.isWebScheme(scheme)) {
                openWeb(request.uri)
                closePopup()
                closePages()
            }
            return GeckoResult.deny()
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null
    }

    private object Content : ContentDelegate {
        override fun onCloseRequest(session: GeckoSession) = close(session)

        /** A file it made, such as a backup or exported settings. Okapi asks where to save it. */
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            (fileToSave?.source as? DownloadSource.Response)?.discard()
            fileToSave = DownloadRequest.of(response, null)
        }

        override fun onCrash(session: GeckoSession) = close(session)

        override fun onKill(session: GeckoSession) = close(session)
    }

    /** Their dialogs and file fields, shown by Okapi as a page's are, one at a time. */
    private object Prompts : PromptDelegate {
        private fun <P : PromptDelegate.BasePrompt> show(prompt: P, make: (PromptAnswer<P>) -> PagePrompt): GeckoResult<PromptResponse> {
            val answer = PromptAnswer(prompt)
            if (this@Extensions.prompt != null || fileField != null) {
                answer.dismiss()
                return answer.result
            }
            val shown = make(answer)
            prompt.delegate = object : PromptDelegate.PromptInstanceDelegate {
                override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                    if (this@Extensions.prompt === shown) this@Extensions.prompt = null
                }
            }
            this@Extensions.prompt = shown
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
            if (this@Extensions.prompt != null || fileField != null || app == null || prompt.type != PromptDelegate.FilePrompt.Type.SINGLE) {
                answer.dismiss()
            } else {
                fileField = FileChooserAsk(app, answer)
            }
            return answer.result
        }
    }

    /** The engine asks before an extension gets permissions. Okapi asks the person, one question at a time. */
    private object Asks : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val default = DEFAULTS.firstOrNull { it.id == extension.id && it.id in addingByDefault }
            if (default != null) return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, default.privateTabs, false))
            val answer = GeckoResult<WebExtension.PermissionPromptResponse>()
            if (ask != null) {
                answer.complete(WebExtension.PermissionPromptResponse(false, false, false))
                return answer
            }
            ask = ExtensionAsk.Add(extension, ExtensionPermissions.of(permissions, origins, dataCollectionPermissions)) { add, privateTabs ->
                ask = null
                answer.complete(WebExtension.PermissionPromptResponse(add, privateTabs, false))
            }
            return answer
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<out String>,
            newOrigins: Array<out String>,
            newDataCollectionPermissions: Array<out String>,
        ): GeckoResult<AllowOrDeny> = askToAllow(extension, newPermissions, newOrigins, newDataCollectionPermissions, update = true)

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>,
        ): GeckoResult<AllowOrDeny> = askToAllow(extension, permissions, origins, dataCollectionPermissions, update = false)

        private fun askToAllow(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            data: Array<out String>,
            update: Boolean,
        ): GeckoResult<AllowOrDeny> {
            val answer = GeckoResult<AllowOrDeny>()
            if (ask != null) {
                answer.complete(AllowOrDeny.DENY)
                return answer
            }
            ask = ExtensionAsk.Allow(extension, ExtensionPermissions.of(permissions, origins, data), update) { allow ->
                ask = null
                answer.complete(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }
            return answer
        }
    }

    /** Any install, removal or change, from the person or from an update: the list is read again. */
    private object Changes : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) = refresh()

        override fun onUninstalled(extension: WebExtension) = refresh()

        override fun onEnabled(extension: WebExtension) = refresh()

        override fun onDisabled(extension: WebExtension) = refresh()

        override fun onOptionalPermissionsChanged(extension: WebExtension) = refresh()
    }

    private const val PREFS = "extensions"
    private const val OFFERED = "default-offered:"
    private const val LAST_UPDATE_CHECK = "last-update-check"
    private const val UPDATE_EVERY_MS = 24L * 60 * 60 * 1000
    private const val EXTENSION_SCHEME = "moz-extension"
    private const val XPI_TYPE = "application/x-xpinstall"
}

/** An extension Okapi installs on the first start, from the signed file at [location] in the APK. */
data class DefaultExtension(val id: String, val location: String, val privateTabs: Boolean)

/** One of an extension's own pages, shown by Okapi: its popup, or a page such as its settings. */
class ExtensionPage(val extension: WebExtension, val session: GeckoSession)

/** A question about an extension that waits for the person. */
sealed class ExtensionAsk {
    abstract val extension: WebExtension
    abstract val permissions: ExtensionPermissions

    /** Add it? It works in Private Tabs only if the person says so. */
    class Add(
        override val extension: WebExtension,
        override val permissions: ExtensionPermissions,
        val answer: (add: Boolean, privateTabs: Boolean) -> Unit,
    ) : ExtensionAsk()

    /** An update ([update]) or the extension itself asks for more than it has. */
    class Allow(
        override val extension: WebExtension,
        override val permissions: ExtensionPermissions,
        val update: Boolean,
        val answer: (Boolean) -> Unit,
    ) : ExtensionAsk()
}

/** Something about extensions that the person should hear about. */
sealed class ExtensionNotice {
    class Added(val extension: WebExtension) : ExtensionNotice()

    /** An install that failed: [name] when the engine knows it, and GeckoView's error code. */
    class Failed(val name: String?, val code: Int?) : ExtensionNotice()

    /** Checking for updates is done: [count] updated now, and [waiting] that update at the next start. */
    class Updated(val count: Int, val waiting: Int) : ExtensionNotice()

    /** A file an extension made, [name], couldn't be written where the person chose. */
    class SaveFailed(val name: String) : ExtensionNotice()

    /** addons.mozilla.org couldn't be asked about at least one extension. */
    data object UpdateCheckFailed : ExtensionNotice()
}
