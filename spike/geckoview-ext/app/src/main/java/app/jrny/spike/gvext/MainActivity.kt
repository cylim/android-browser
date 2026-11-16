package app.jrny.spike.gvext

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Throwaway spike: can a GeckoView app install and run Firefox extensions?
 * The extension wiring follows Fenix / Android Components (GeckoProvider.kt, GeckoEngine.kt,
 * GeckoWebExtension.kt in mozilla-firefox/firefox main), cut down to raw GeckoView calls.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "JRNYGV"
        private const val HOME = "https://example.com/"
        private const val UBO_XPI =
            "https://addons.mozilla.org/firefox/downloads/latest/uBlock0@raymondhill.net/latest.xpi"
        private const val AMO_ANDROID = "https://addons.mozilla.org/android/"
        private const val AD_SITE = "https://edition.cnn.com/"
        private const val BRIDGE_URI = "resource://android/assets/extensions/jrny-bridge/"
        private const val BRIDGE_ID = "bridge@spike.jrny.app"

        // One runtime per process, as GeckoView requires (Fenix: GeckoProvider.getOrCreateRuntime).
        private var runtime: GeckoRuntime? = null

        /**
         * A JRNY Profile modelled as a GeckoView session context: every session with the same
         * contextId shares cookies and storage, and sessions with different IDs are isolated
         * (GeckoSessionSettings.Builder.contextId). Private Tabs add usePrivateMode on top.
         */
        data class Profile(val name: String, val contextId: String, val private: Boolean)

        // Index = base + (private ? 2 : 0), base 0 = A, 1 = B.
        val PROFILES = listOf(
            Profile("A", "jrny-a", false),
            Profile("B", "jrny-b", false),
            Profile("A private", "jrny-a", true),
            Profile("B private", "jrny-b", true),
        )
        // Same colors as the WebView spike (spike/multi-profile-webview).
        val PROFILE_COLORS = listOf(Color.rgb(0x1E, 0x5A, 0xC8), Color.rgb(0xC8, 0x5A, 0x1E))
        private const val COOKIE_SITE = "https://httpbin.org"
        // The bridge extension's content script runs on this page and reports the page's own
        // jrny_iso_* cookies and localStorage keys in document.title (see content.js).
        private const val ISO_PAGE = "https://httpbin.org/html"
        private const val KEY_PROFILE = "profile"

        val SIGN_IN_SHORTCUTS = listOf(
            "Google" to "https://accounts.google.com/",
            "Microsoft (personal)" to "https://account.microsoft.com/",
            "Microsoft (work)" to "https://login.microsoftonline.com/",
            "GitHub" to "https://github.com/login",
            "Apple" to "https://account.apple.com/",
            "Google via Stack Overflow" to "https://stackoverflow.com/users/login",
            "Headers" to "https://httpbin.org/headers",
            // Passkeys: webauthn.io registers and signs in with a throwaway username; GitHub's
            // "Sign in with a passkey" is the real-site check the WebView spike used.
            "Passkey probe" to "https://httpbin.org/html?op=webauthn",
            "Passkey: webauthn.io" to "https://webauthn.io/",
            "Passkey: GitHub" to "https://github.com/login",
        )
    }

    private lateinit var geckoView: GeckoView
    private lateinit var urlField: EditText
    private lateinit var logView: TextView
    private lateinit var status: TextView
    private lateinit var profileRadios: List<RadioButton>
    private lateinit var privateBox: CheckBox
    private lateinit var prefs: SharedPreferences
    private lateinit var session: GeckoSession
    private var canGoBack = false

    /** One live Tab per Profile, keyed by index into [PROFILES]. */
    private val tabs = mutableMapOf<Int, GeckoSession>()
    private var activeProfile = 0

    private val browserActions = mutableMapOf<String, WebExtension.Action>()
    /** Per-Tab actions (extension id -> action for the visible Tab), like Fenix's session action handler. */
    private val tabActions = mutableMapOf<String, WebExtension.Action>()
    private val extensions = mutableMapOf<String, WebExtension>()
    private var bridgePort: WebExtension.Port? = null
    private var popupDialog: AlertDialog? = null
    /** The Tab behind an open window.open popup (sign-in popups need their opener alive). */
    private var popupOpener: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("spike", MODE_PRIVATE)
        // Normal Profiles only: a Private Tab never survives a restart.
        activeProfile = prefs.getInt(KEY_PROFILE, 0).coerceIn(0, 1)
        setContentView(buildUi())
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            when {
                canGoBack -> session.goBack()
                popupOpener != null -> closePopupTab()
                else -> finish()
            }
        }

        val rt = runtime ?: createRuntime().also { runtime = it }
        log("GeckoView ${org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION}, " +
            "channel ${org.mozilla.geckoview.BuildConfig.MOZ_UPDATE_CHANNEL}")
        setUpExtensionController(rt.webExtensionController)

        session = newSession(rt, profile = PROFILES[activeProfile])
        tabs[activeProfile] = session
        geckoView.setSession(session)
        rt.webExtensionController.setTabActive(session, true)
        // The first page loads only after the built-in extension is ready, so its
        // passkeys-unsupported content script is in place before any site runs.
        val firstUrl = incomingUrl(intent) ?: HOME
        updateStatus()

        // Installed extensions persist across restarts; re-attach delegates to each one (Fenix does
        // the same in GeckoEngine.listInstalledWebExtensions).
        rt.webExtensionController.list().accept({ list ->
            log("Startup: ${list?.size ?: 0} extension(s) already installed")
            list?.forEach { attach(it, "startup") }
            // Like Fenix's built-ins: ensureBuiltIn at every start (reinstalls only on a version change).
            installBridge { session.loadUri(firstUrl) }
        }, { e -> log("list() failed: $e"); session.loadUri(firstUrl) })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUrl(intent)?.let(session::loadUri)
    }

    private fun incomingUrl(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

    // ---------------------------------------------------------------- runtime

    private fun createRuntime(): GeckoRuntime {
        // Extension-related settings copied from Fenix's GeckoProvider.createRuntimeSettings.
        val settings = GeckoRuntimeSettings.Builder()
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true) // lets addons.mozilla.org's own install button work
            .consoleOutput(true)
            .build()
        return GeckoRuntime.create(applicationContext, settings)
    }

    private fun setUpExtensionController(controller: WebExtensionController) {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                val md = extension.metaData
                log("Install prompt: ${md.name} ${md.version} signedState=${signed(md.signedState)}")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Add ${md.name}?")
                    .setMessage(
                        "Permissions: ${permissions.joinToString().ifEmpty { "none" }}\n\n" +
                            "Sites: ${origins.joinToString().ifEmpty { "none" }}\n\n" +
                            "Data collection: ${dataCollectionPermissions.joinToString().ifEmpty { "none" }}"
                    )
                    .setPositiveButton("Add") { _, _ ->
                        result.complete(WebExtension.PermissionPromptResponse(true, false, false))
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    }
                    .setCancelable(false)
                    .show()
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>,
            ): GeckoResult<AllowOrDeny> {
                log("Update prompt for ${extension.id}: ${newPermissions.joinToString()} ${newOrigins.joinToString()}")
                return GeckoResult.allow()
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<AllowOrDeny> {
                log("Optional permission prompt for ${extension.id}: ${permissions.joinToString()} ${origins.joinToString()}")
                return GeckoResult.allow()
            }
        }

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                // Fires for every install, including ones started from a web page (AMO's button),
                // which never pass through our install() calls.
                log("onInstalled ${extension.id}")
                attach(extension, "onInstalled")
            }
            override fun onReady(extension: WebExtension) = log("onReady ${extension.id}")
            override fun onUninstalled(extension: WebExtension) {
                log("onUninstalled ${extension.id}")
                extensions.remove(extension.id)
                browserActions.remove(extension.id)
            }
            override fun onEnabled(extension: WebExtension) = log("onEnabled ${extension.id}")
            override fun onDisabled(extension: WebExtension) = log("onDisabled ${extension.id}")
            override fun onInstallationFailed(extension: WebExtension?, installException: WebExtension.InstallException) =
                log("onInstallationFailed ${extension?.id}: ${describe(installException)}")
        })
    }

    /** Hooks one extension up to the app: browser action, new tabs, options page, native messaging. */
    private fun attach(ext: WebExtension, why: String) {
        extensions[ext.id] = ext
        val md = ext.metaData
        log("[$why] ${ext.id} ${md.version} enabled=${md.enabled} builtIn=${ext.isBuiltIn} " +
            "signed=${signed(md.signedState)} blocklist=${md.blocklistState} baseUrl=${md.baseUrl}")

        ext.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                browserActions[extension.id] = action
                log("browserAction ${extension.id}: title=${action.title} badge=${action.badgeText}")
            }
            override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action) = showPopup(extension)
            override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action) = showPopup(extension)
        })

        (tabs.values + listOfNotNull(popupOpener)).forEach { attachToSession(ext, it) }

        ext.tabDelegate = object : WebExtension.TabDelegate {
            override fun onNewTab(extension: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<GeckoSession> {
                // Single-tab spike: the extension's new tab replaces the current one.
                // GeckoView opens the returned session itself and throws if it is already open
                // ("Must use an unopened GeckoSession instance"), so Fenix passes
                // openGeckoSession = false here. Show it only once GeckoView has opened it.
                log("onNewTab from ${extension.id}: ${details.url}")
                popupDialog?.dismiss() // Fenix closes the popup when an extension opens a tab
                // The new tab belongs to the Active Profile, like any other Tab.
                log("  cookieStoreId requested: ${details.cookieStoreId}")
                val rt = runtime!!
                val tab = newSession(rt, open = false, profile = PROFILES[activeProfile])
                showWhenOpen(tab, rt)
                return GeckoResult.fromValue(tab)
            }
            override fun onOpenOptionsPage(extension: WebExtension) {
                log("onOpenOptionsPage ${extension.id}")
                extension.metaData.optionsPageUrl?.let(session::loadUri)
            }
        }

        if (ext.id == BRIDGE_ID && !md.allowedInPrivateBrowsing) {
            // The isolation test also runs the bridge's content script in Private Tabs.
            runtime!!.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                .accept({ log("bridge: allowed in private browsing") }, { e -> log("setAllowedInPrivateBrowsing failed: $e") })
        }
        if (ext.id == BRIDGE_ID) {
            ext.setMessageDelegate(object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    log("bridge: port connected (${port.name})")
                    bridgePort = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) =
                            log("bridge ← $message")
                        override fun onDisconnect(port: WebExtension.Port) {
                            log("bridge: port disconnected")
                            if (bridgePort == port) bridgePort = null
                        }
                    })
                }
            }, "jrny")
        }
    }

    private fun showPopup(ext: WebExtension): GeckoResult<GeckoSession> {
        log("popup requested by ${ext.id}")
        popupDialog?.dismiss()
        val popupSession = GeckoSession()
        popupSession.open(runtime!!)
        val view = GeckoView(this).apply {
            setSession(popupSession)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(480))
        }
        popupDialog = AlertDialog.Builder(this)
            .setTitle(ext.metaData.name)
            .setView(view)
            .setOnDismissListener { popupSession.close() }
            .show()
        return GeckoResult.fromValue(popupSession)
    }

    /**
     * Shows a session that GeckoView has just opened for us. With [replace] (extension tabs) it
     * replaces the Profile's Tab. Without it (window.open popups, e.g. "Sign in with Google") the
     * opener stays alive behind the popup, because OAuth popups post their result back to it.
     */
    private fun showWhenOpen(tab: GeckoSession, rt: GeckoRuntime, replace: Boolean = true) {
        if (!tab.isOpen) {
            geckoView.postDelayed({ showWhenOpen(tab, rt, replace) }, 16)
            return
        }
        if (!replace) closePopupTab() // one popup at a time
        rt.webExtensionController.setTabActive(session, false)
        val old = geckoView.releaseSession()
        if (replace) old?.close() else popupOpener = old
        session = tab
        if (replace) tabs[activeProfile] = tab
        geckoView.setSession(tab)
        rt.webExtensionController.setTabActive(tab, true)
        updateStatus()
        log(if (replace) "new tab shown" else "popup shown over its opener")
    }

    /** Closes a window.open popup and returns to the page that opened it. */
    private fun closePopupTab() {
        val opener = popupOpener ?: return
        val rt = runtime!!
        rt.webExtensionController.setTabActive(session, false)
        geckoView.releaseSession()?.close()
        popupOpener = null
        session = opener
        geckoView.setSession(opener)
        rt.webExtensionController.setTabActive(opener, true)
        updateStatus()
        log("popup closed, back to opener")
    }

    // ---------------------------------------------------------------- Profiles

    private fun profileIndex(base: Int, private: Boolean) = base + if (private) 2 else 0

    /**
     * Switches the visible Tab to another Profile. Each Profile keeps its own live Tab, the way
     * background Tabs stay alive in the real app; a Profile's first Tab opens the current address
     * (the same behaviour as a Move).
     */
    private fun switchProfile(index: Int) {
        if (index == activeProfile) return
        closePopupTab()
        val rt = runtime!!
        val url = urlField.text.toString().takeIf { it.startsWith("http") } ?: HOME
        rt.webExtensionController.setTabActive(session, false)
        geckoView.releaseSession()
        activeProfile = index
        if (!PROFILES[index].private) prefs.edit().putInt(KEY_PROFILE, index).apply()
        val p = PROFILES[index]
        session = tabs.getOrPut(index) { newSession(rt, profile = p).also { it.loadUri(url) } }
        geckoView.setSession(session)
        rt.webExtensionController.setTabActive(session, true)
        updateStatus()
        log("Active Profile: ${p.name} (contextId=${p.contextId}, private=${p.private})")
    }

    private fun updateStatus() {
        val p = PROFILES[activeProfile]
        val base = activeProfile % 2
        profileRadios.forEachIndexed { i, r -> r.isChecked = i == base }
        privateBox.isChecked = p.private
        status.text = "Profile ${p.name}  ·  contextId=${p.contextId}  ·  ${tabs.size} live Tab(s)" +
            if (popupOpener != null) "  ·  POPUP open (back closes it)" else ""
        status.setBackgroundColor(if (p.private) Color.rgb(0x4A, 0x2A, 0x6A) else PROFILE_COLORS[base])
    }

    /**
     * Deletes the other normal Profile while the app keeps running: close its Tabs (normal and
     * private), then StorageController.clearDataForSessionContext. WebView's ProfileStore refuses
     * to delete a loaded profile, which is why SPEC defers the wipe to the next cold start.
     */
    private fun deleteOtherProfile() {
        val rt = runtime!!
        val other = PROFILES[1 - activeProfile % 2]
        PROFILES.indices.filter { PROFILES[it].contextId == other.contextId }.forEach { i ->
            tabs.remove(i)?.let { rt.webExtensionController.setTabActive(it, false); it.close() }
        }
        rt.storageController.clearDataForSessionContext(other.contextId)
        updateStatus()
        log("Delete ${other.name}: Tabs closed, clearDataForSessionContext(${other.contextId}) called. Run Read storage.")
    }

    /** Asks the built-in extension what it can see across Profiles: cookie stores, cookies, tabs. */
    private fun extensionProbe() {
        val port = bridgePort ?: run { log("Bridge not connected; tap Built-in bridge first"); return }
        port.postMessage(JSONObject().put("type", "probe"))
    }

    // ---------------------------------------------------------------- isolation test

    /** The contexts the isolation test checks: the four Profile variants plus GeckoView's default. */
    private val isoContexts: List<Pair<String, Profile?>> =
        PROFILES.map { it.name.replace(" ", "_") to it } + ("default" to null)

    /**
     * Cookie and localStorage isolation check, the GeckoView counterpart of the WebView spike's
     * "Isolation test". GeckoView has no cookie API for apps, so a content script of the bundled
     * extension does the reading and writing inside the page (see content.js).
     *
     * With [write], every Profile context first stores jrny_iso_<its name>. Then every context,
     * including the default one, reports which jrny_iso_* cookies and localStorage keys it sees.
     * Pass: each Profile context sees exactly its own, and the default context sees none.
     * Without [write] it only reads, to check persistence after a restart or a Profile deletion.
     */
    private fun runIsolationTest(write: Boolean) {
        if (!extensions.containsKey(BRIDGE_ID)) { log("Tap Built-in bridge first"); return }
        val rt = runtime!!
        val sessions = isoContexts.associate { (tag, p) ->
            tag to GeckoSession(
                GeckoSessionSettings.Builder().apply {
                    if (p != null) contextId(p.contextId).usePrivateMode(p.private)
                }.build()
            ).also { it.open(rt) }
        }
        val steps = buildList {
            if (write) isoContexts.filter { it.second != null }.forEach { add(it.first to "set") }
            isoContexts.forEach { add(it.first to "read") }
        }
        val seen = mutableMapOf<String, JSONObject>()
        log("Isolation test (${if (write) "write + read" else "read only"}): ${steps.size} steps…")

        fun finish() {
            sessions.values.forEach { it.close() }
            val text = buildString {
                var pass = true
                isoContexts.forEach { (tag, p) ->
                    val r = seen[tag]
                    if (r == null) { pass = false; appendLine("✗ $tag: no answer (timeout)"); return@forEach }
                    val cookies = r.getJSONArray("cookies").let { a -> List(a.length()) { a.getString(it) } }
                    val ls = r.getJSONArray("ls").let { a -> List(a.length()) { a.getString(it) } }
                    val expected = if (p == null) emptyList() else listOf("jrny_iso_$tag")
                    val ok = !write || (cookies.sorted() == expected && ls.sorted() == expected)
                    if (!ok) pass = false
                    appendLine("${if (ok) "✓" else "✗"} $tag sees cookies=$cookies localStorage=$ls" +
                        if (write) "  (expected $expected)" else "")
                }
                if (write) insert(0, if (pass) "PASS: Profiles are isolated\n\n" else "FAIL: isolation broken\n\n")
            }
            log(text.trim().replace("\n\n", "\n"))
            AlertDialog.Builder(this).setTitle(if (write) "Isolation test" else "Read storage")
                .setMessage(text).setPositiveButton("OK", null).show()
        }

        fun step(i: Int) {
            if (i == steps.size) { finish(); return }
            val (tag, op) = steps[i]
            val s = sessions.getValue(tag)
            var done = false
            val timeout = Runnable { if (!done) { done = true; log("  $tag $op: timeout"); step(i + 1) } }
            s.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    if (done || title?.startsWith("ISO ") != true) return
                    val r = JSONObject(title.removePrefix("ISO "))
                    if (r.optString("op") != op) return
                    done = true
                    geckoView.removeCallbacks(timeout)
                    if (op == "read") seen[tag] = r
                    step(i + 1)
                }
            }
            geckoView.postDelayed(timeout, 20_000)
            s.loadUri("$ISO_PAGE?op=$op&tag=$tag&n=${System.nanoTime()}")
        }
        step(0)
    }

    private fun newSession(
        rt: GeckoRuntime,
        open: Boolean = true,
        profile: Profile = PROFILES[activeProfile],
    ): GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .contextId(profile.contextId)
            .usePrivateMode(profile.private)
            .build()
    ).apply {
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (session == this@MainActivity.session && url != null && !url.startsWith("data:")) {
                    urlField.setText(url)
                }
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (session == this@MainActivity.session) this@MainActivity.canGoBack = canGoBack
            }
            // window.open: the popup must live in the opener's Profile, like the WebView spike's
            // onCreateWindow. GeckoView opens the returned (unopened) session itself.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val opener = PROFILES.indexOfFirst {
                    it.contextId == session.settings.contextId && it.private == session.settings.usePrivateMode
                }.takeIf { it >= 0 } ?: activeProfile
                log("onNewSession from ${PROFILES[opener].name}: $uri")
                val popup = newSession(rt, open = false, profile = PROFILES[opener])
                showWhenOpen(popup, rt, replace = false)
                return GeckoResult.fromValue(popup)
            }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            // window.close() from a sign-in popup.
            override fun onCloseRequest(session: GeckoSession) {
                if (session == this@MainActivity.session && popupOpener != null) closePopupTab()
            }
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (title?.startsWith("ISO ") == true) log("[${profile.name}] $title")
                if (title?.startsWith("WA ") == true) log("[${profile.name}] passkey: ${title.removePrefix("WA ")}")
            }
        }
        if (open) open(rt)
        extensions.values.forEach { attachToSession(it, this) }
    }

    /**
     * Fenix registers an action handler per session as well as the global one: extensions such as
     * 1Password change their action per Tab, and Action.click() must target the Tab's action for
     * browserAction.onClicked to fire for that Tab.
     */
    private fun attachToSession(ext: WebExtension, s: GeckoSession) {
        s.webExtensionController.setActionDelegate(ext, object : WebExtension.ActionDelegate {
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                if (session == this@MainActivity.session) tabActions[extension.id] = action
            }
        })
    }

    // ---------------------------------------------------------------- test actions

    /** Installs whatever .xpi URL is in the address bar, the way an add-on screen fed by the AMO API would. */
    private fun installFromUrl(url: String) {
        if (!url.startsWith("https://")) { log("Install URL: put an https .xpi URL in the address bar"); return }
        log("install($url)…")
        runtime!!.webExtensionController.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            .accept({ ext -> log("install OK: ${ext?.id}") }, { e -> log("install FAILED: ${describe(e)}") })
    }

    private fun installUbo() {
        log("install(uBO from AMO)…")
        runtime!!.webExtensionController.install(UBO_XPI, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            .accept({ ext -> log("install OK: ${ext?.id}") }, { e -> log("install FAILED: ${describe(e)}") })
    }

    /** Builds an unsigned .xpi on the fly and installs it from file://. Release GeckoView should refuse it. */
    private fun installUnsigned() {
        val xpi = File(cacheDir, "unsigned-test.xpi")
        ZipOutputStream(xpi.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                """{"manifest_version":2,"name":"Unsigned test","version":"1.0",
                   "browser_specific_settings":{"gecko":{"id":"unsigned@spike.jrny.app"}},
                   "background":{"scripts":["bg.js"]}}""".toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("bg.js"))
            zip.write("console.log('unsigned test running');".toByteArray())
            zip.closeEntry()
        }
        log("install(unsigned file://)…")
        runtime!!.webExtensionController
            .install(xpi.toURI().toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
            .accept({ ext -> log("unsigned install SUCCEEDED (unexpected): ${ext?.id}") },
                { e -> log("unsigned install refused: ${describe(e)}") })
    }

    private fun installBridge(then: (() -> Unit)? = null) {
        log("ensureBuiltIn(bridge)…")
        runtime!!.webExtensionController.ensureBuiltIn(BRIDGE_URI, BRIDGE_ID)
            .accept({ ext ->
                log("built-in OK: ${ext?.id}")
                ext?.let { attach(it, "builtIn") }
                bridgePort?.postMessage(JSONObject().put("type", "list"))
                then?.invoke()
            }, { e -> log("built-in FAILED: ${describe(e)}"); then?.invoke() })
    }

    private fun openUboPopup() {
        // Prefer the visible Tab's action, then the extension's default action.
        val id = browserActions.keys.firstOrNull { it != BRIDGE_ID }
        val action = id?.let { tabActions[it] ?: browserActions[it] }
        if (action == null) { log("No browser action registered yet"); return }
        log("clicking browser action…")
        action.click()
    }

    private fun showExtensions() {
        val rt = runtime!!
        rt.webExtensionController.list().accept({ list ->
            val exts = list.orEmpty()
            if (exts.isEmpty()) { log("No extensions installed"); return@accept }
            val labels = exts.map { e ->
                val md = e.metaData
                "${md.name} ${md.version}\n${e.id}\n" +
                    "${if (md.enabled) "enabled" else "disabled"} · signed=${signed(md.signedState)} · builtIn=${e.isBuiltIn}"
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Installed extensions")
                .setItems(labels) { _, i -> extensionActions(exts[i]) }
                .show()
        }, { e -> log("list() failed: $e") })
    }

    private fun extensionActions(ext: WebExtension) {
        val c = runtime!!.webExtensionController
        val enabled = ext.metaData.enabled
        val actions = arrayOf(
            "Open popup", "Open options page", if (enabled) "Disable" else "Enable", "Check for update", "Uninstall",
        )
        AlertDialog.Builder(this).setTitle(ext.metaData.name).setItems(actions) { _, i ->
            when (i) {
                0 -> browserActions[ext.id]?.click() ?: log("No browser action for ${ext.id}")
                1 -> ext.metaData.optionsPageUrl?.let(session::loadUri) ?: log("No options page for ${ext.id}")
                2 -> (if (enabled) c.disable(ext, WebExtensionController.EnableSource.USER)
                      else c.enable(ext, WebExtensionController.EnableSource.USER))
                    .accept({ log("${actions[i]} OK: ${ext.id}"); it?.let { e -> attach(e, actions[i]) } },
                        { e -> log("${actions[i]} FAILED: $e") })
                3 -> c.update(ext).accept({ log(if (it == null) "No update for ${ext.id}" else "Updated to ${it.metaData.version}") },
                    { e -> log("update FAILED: ${describe(e)}") })
                4 -> c.uninstall(ext).accept({ log("uninstall OK: ${ext.id}") }, { e -> log("uninstall FAILED: $e") })
            }
        }.show()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }

        // Row 1: Profiles, as in the WebView spike, plus the multi-Profile tests.
        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        profileRadios = listOf("A", "B").mapIndexed { i, label ->
            RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                setOnClickListener { switchProfile(profileIndex(i, privateBox.isChecked)) }
            }.also(group::addView)
        }
        profileRow.addView(group)
        privateBox = CheckBox(this).apply {
            text = "Private"
            setOnClickListener { switchProfile(profileIndex(activeProfile % 2, isChecked)) }
        }
        profileRow.addView(privateBox)
        fun profileButton(label: String, onClick: () -> Unit) = profileRow.addView(
            Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } })
        profileButton("Isolation test") { runIsolationTest(write = true) }
        profileButton("Read storage") { runIsolationTest(write = false) }
        profileButton("Set probe cookie") {
            session.loadUri("$COOKIE_SITE/cookies/set?jrny_probe=profile_${PROFILES[activeProfile].name.replace(" ", "_")}")
        }
        profileButton("Cookies") { session.loadUri("$COOKIE_SITE/cookies") }
        profileButton("Popup test") { session.loadUri("$ISO_PAGE?op=popup&tag=page") }
        profileButton("Ext probe") { extensionProbe() }
        profileButton("Delete other profile") { deleteOtherProfile() }
        root.addView(HorizontalScrollView(this).apply { addView(profileRow) })

        urlField = EditText(this).apply {
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    val t = text.toString().trim()
                    session.loadUri(if (t.contains("://")) t else "https://$t"); true
                } else false
            }
        }
        root.addView(urlField, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // Row 2: extensions.
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, onClick: () -> Unit) =
            buttons.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } })
        button("Install uBO") { installUbo() }
        button("Install URL") { installFromUrl(urlField.text.toString().trim()) }
        button("Unsigned .xpi") { installUnsigned() }
        button("Built-in bridge") { installBridge() }
        button("Ad site (CNN)") { session.loadUri(AD_SITE) }
        button("AMO") { session.loadUri(AMO_ANDROID) }
        button("Popup") { openUboPopup() }
        button("Extensions") { showExtensions() }
        root.addView(HorizontalScrollView(this).apply { addView(buttons) })

        // Row 3: accounts and passkeys, the same shortcuts as the WebView spike.
        val accounts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        SIGN_IN_SHORTCUTS.forEach { (label, url) ->
            accounts.addView(Button(this).apply {
                text = label; isAllCaps = false
                setOnClickListener { closePopupTab(); session.loadUri(url) }
            })
        }
        root.addView(HorizontalScrollView(this).apply { addView(accounts) })

        geckoView = GeckoView(this)
        root.addView(geckoView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        status = TextView(this).apply {
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setTextColor(Color.WHITE)
        }
        root.addView(status)

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            movementMethod = ScrollingMovementMethod()
        }
        root.addView(logView, LinearLayout.LayoutParams(MATCH_PARENT, dp(170)))
        return root
    }

    // ---------------------------------------------------------------- helpers

    private fun log(msg: String) {
        Log.i(TAG, msg)
        // Some vendor builds (HONOR) drop app log lines from logcat, so also keep a file:
        // adb shell run-as app.jrny.spike.gvext cat files/spike.log
        runCatching { File(filesDir, "spike.log").appendText("${System.currentTimeMillis()} $msg\n") }
        runOnUiThread {
            logView.append(msg + "\n")
            val layout = logView.layout ?: return@runOnUiThread
            val scroll = layout.getLineTop(logView.lineCount) - logView.height
            logView.scrollTo(0, scroll.coerceAtLeast(0))
        }
    }

    private fun describe(e: Throwable?): String = when (e) {
        is WebExtension.InstallException -> "InstallException code=${e.code} (${installCode(e.code)}) id=${e.extensionId}"
        null -> "null"
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }

    private fun installCode(code: Int) = WebExtension.InstallException.ErrorCodes::class.java.fields
        .firstOrNull { it.getInt(null) == code }?.name ?: "?"

    private fun signed(state: Int) = WebExtension.SignedStateFlags::class.java.fields
        .firstOrNull { it.getInt(null) == state }?.name ?: state.toString()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
