package app.jrny.spike.signin

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray

/**
 * Milestone 0 throwaway: can a System WebView browser sign in to Google, Microsoft, GitHub and
 * Apple in two isolated androidx.webkit profiles, and which user-agent changes does that need?
 *
 * Rules for this spike: never log or display cookie values, and never persist query strings
 * (OAuth flows put codes and tokens there).
 */
class MainActivity : Activity() {

    private enum class UaMode(val label: String) {
        DEFAULT("1 · WebView default"),
        UA_ONLY("2 · Chrome-like UA string"),
        UA_AND_HINTS("3 · + hints without WebView brand"),
        UA_AND_CHROME_HINTS("4 · + hints with Google Chrome brand"),
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var container: FrameLayout
    private lateinit var urlField: EditText
    private lateinit var status: TextView

    private var webView: WebView? = null
    private var popup: WebView? = null
    private var profileIndex = 0
    private var uaMode = UaMode.DEFAULT
    private var passkeysForBrowser = false
    private val loadedProfiles = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            setContentView(TextView(this).apply {
                text = "This WebView (${webViewVersion()}) does not support MULTI_PROFILE. The spike cannot run."
                setPadding(48, 160, 48, 48)
            })
            return
        }
        prefs = getSharedPreferences("spike", MODE_PRIVATE)
        profileIndex = prefs.getInt(KEY_PROFILE, 0)
        uaMode = UaMode.entries.getOrElse(prefs.getInt(KEY_UA_MODE, 0)) { UaMode.DEFAULT }
        passkeysForBrowser = prefs.getBoolean(KEY_PASSKEYS, false)

        // Must run before any profile is loaded in this process (ADR 0001: delete on cold start).
        deletePendingProfiles()

        setContentView(buildUi())
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
        ) { handleBack() }
        recreateWebView(incomingUrl(intent) ?: prefs.getString(KEY_URL, null) ?: HOME)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUrl(intent)?.let(::load)
    }

    private fun incomingUrl(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    // WebView writes cookies to disk only periodically (a cookie set seconds before a force-stop
    // was lost in testing), so flush every profile used in this process when leaving the app.
    override fun onStop() {
        super.onStop()
        loadedProfiles.forEach { cookiesFor(it).flush() }
    }

    override fun onDestroy() {
        popup?.destroy()
        webView?.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val profileGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        PROFILES.indices.forEach { i ->
            profileGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = PROFILE_LABELS[i]
                isChecked = i == profileIndex
                setOnClickListener { switchProfile(i) }
            })
        }
        controls.addView(profileGroup)

        val uaSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                UaMode.entries.map { it.label },
            )
            setSelection(uaMode.ordinal, false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    if (position == uaMode.ordinal) return
                    uaMode = UaMode.entries[position]
                    prefs.edit().putInt(KEY_UA_MODE, position).apply()
                    recreateWebView(currentUrl())
                }

                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }
        }
        controls.addView(uaSpinner, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        controls.addView(CheckBox(this).apply {
            text = "Passkeys"
            isChecked = passkeysForBrowser
            setOnCheckedChangeListener { _, checked ->
                passkeysForBrowser = checked
                prefs.edit().putBoolean(KEY_PASSKEYS, checked).apply()
                recreateWebView(currentUrl())
            }
        })
        root.addView(controls)

        val urlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlField = EditText(this).apply {
            isSingleLine = true
            hint = "https://…"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(); true }
        }
        urlRow.addView(urlField, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        urlRow.addView(Button(this).apply { text = "Go"; setOnClickListener { go() } })
        root.addView(urlRow)

        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        SHORTCUTS.forEach { (label, url) -> shortcuts.addView(button(label) { load(url) }) }
        shortcuts.addView(button("Set probe cookie") {
            load("https://httpbin.org/cookies/set?jrny_probe=profile_${PROFILE_LABELS[profileIndex]}")
        })
        shortcuts.addView(button("Isolation test") { runIsolationTest() })
        shortcuts.addView(button("Diagnostics") { showDiagnostics() })
        shortcuts.addView(button("Delete other profile") { deleteOtherProfile() })
        root.addView(HorizontalScrollView(this).apply { addView(shortcuts) })

        container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        status = TextView(this).apply {
            setPadding(24, 8, 24, 8)
            setTextColor(Color.WHITE)
        }
        root.addView(status)
        return root
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun updateStatus() {
        val popupNote = if (popup != null) "  ·  POPUP open (back closes it)" else ""
        status.text = "Profile ${PROFILE_LABELS[profileIndex]}  ·  ${uaMode.label}  ·  " +
            "passkeys ${if (passkeysForBrowser) "browser" else "off"}$popupNote"
        status.setBackgroundColor(PROFILE_COLORS[profileIndex])
    }

    // ---------------------------------------------------------------- WebView lifecycle

    private fun recreateWebView(url: String) {
        closePopup()
        webView?.let {
            container.removeView(it)
            it.destroy()
        }
        val wv = newWebView()
        webView = wv
        container.addView(wv, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wv.loadUrl(url)
        updateStatus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        val wv = WebView(this)
        // Must be the first call on a new WebView: the profile is fixed once the WebView is used.
        WebViewCompat.setProfile(wv, PROFILES[profileIndex])

        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        applyUaMode(s)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                s,
                if (passkeysForBrowser) {
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
                } else {
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
                },
            )
        }

        val cookies = currentProfileCookies()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = client
        wv.webChromeClient = chrome
        return wv
    }

    private fun applyUaMode(s: WebSettings) {
        val default = WebSettings.getDefaultUserAgent(this)
        s.userAgentString = if (uaMode == UaMode.DEFAULT) null else chromeLikeUserAgent(default)

        if (uaMode != UaMode.UA_AND_HINTS && uaMode != UaMode.UA_AND_CHROME_HINTS) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            toast("USER_AGENT_METADATA not supported; hints unchanged")
            return
        }
        val current = WebSettingsCompat.getUserAgentMetadata(s)
        val major = chromeMajor(default)
        val brands = current.brandVersionList
            .filterNot { it.brand.contains("WebView", ignoreCase = true) }
            .toMutableList()
        if (uaMode == UaMode.UA_AND_CHROME_HINTS) {
            brands += UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Google Chrome")
                .setMajorVersion(major)
                .setFullVersion(current.fullVersion ?: "$major.0.0.0")
                .build()
        }
        WebSettingsCompat.setUserAgentMetadata(
            s,
            UserAgentMetadata.Builder(current).setBrandVersionList(brands).build(),
        )
    }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme
            if (scheme == "http" || scheme == "https") return false
            // Do not show the full URL: app-handoff links can carry tokens.
            toast("Blocked non-web navigation ($scheme:)")
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view == webView) urlField.setText(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view == webView) prefs.edit().putString(KEY_URL, withoutQuery(url)).apply()
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            closePopup()
            // Popups (e.g. "Sign in with Google" buttons) must live in the same profile.
            val p = newWebView()
            popup = p
            container.addView(p, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            updateStatus()
            (resultMsg.obj as WebView.WebViewTransport).webView = p
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            if (window == popup) closePopup()
        }
    }

    private fun closePopup() {
        popup?.let {
            container.removeView(it)
            it.destroy()
        }
        popup = null
        if (::status.isInitialized) updateStatus()
    }

    private fun handleBack() {
        val wv = webView
        when {
            popup != null -> closePopup()
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> finish()
        }
    }

    // ---------------------------------------------------------------- actions

    private fun switchProfile(index: Int) {
        if (index == profileIndex) return
        profileIndex = index
        prefs.edit().putInt(KEY_PROFILE, index).apply()
        // Same as a Move: only the address crosses over, and the page reloads in the new profile.
        recreateWebView(currentUrl())
    }

    private fun go() {
        val raw = urlField.text.toString().trim()
        if (raw.isEmpty()) return
        load(if (raw.contains("://")) raw else "https://$raw")
    }

    private fun load(url: String) {
        closePopup()
        if (url.startsWith(POPUP_TEST_URL)) {
            // Local page that opens a popup with window.open, to check popups share the profile.
            webView?.loadDataWithBaseURL("https://example.com/", POPUP_TEST_HTML, "text/html", "utf-8", null)
            return
        }
        webView?.loadUrl(url)
    }

    // Full address, query included: stripping it breaks sign-in pages (Google answers 400).
    // Only the copy persisted to disk drops the query.
    private fun currentUrl(): String =
        webView?.url?.takeIf { it.startsWith("http") } ?: HOME

    private fun currentProfileCookies(): CookieManager = cookiesFor(PROFILES[profileIndex])

    private fun cookiesFor(name: String): CookieManager {
        loadedProfiles += name
        return ProfileStore.getInstance().getOrCreateProfile(name).cookieManager
    }

    /** Cookie-level isolation check through the API, independent of any website. */
    private fun runIsolationTest() {
        val a = cookiesFor(PROFILES[0])
        val b = cookiesFor(PROFILES[1])
        val d = CookieManager.getInstance()
        val attrs = "; Max-Age=120; Secure; Path=/"
        a.setCookie(PROBE_URL, "jrny_probe_a=1$attrs") { _ ->
            b.setCookie(PROBE_URL, "jrny_probe_b=1$attrs") { _ ->
                fun sees(cm: CookieManager, name: String) =
                    cm.getCookie(PROBE_URL)?.split(";")?.any { it.trim().startsWith("$name=") } == true

                val rows = listOf(
                    "A sees A's cookie" to (sees(a, "jrny_probe_a") to true),
                    "B sees B's cookie" to (sees(b, "jrny_probe_b") to true),
                    "A sees B's cookie" to (sees(a, "jrny_probe_b") to false),
                    "B sees A's cookie" to (sees(b, "jrny_probe_a") to false),
                    "Default profile sees A's" to (sees(d, "jrny_probe_a") to false),
                    "Default profile sees B's" to (sees(d, "jrny_probe_b") to false),
                )
                a.setCookie(PROBE_URL, "jrny_probe_a=; Max-Age=0; Secure; Path=/", null)
                b.setCookie(PROBE_URL, "jrny_probe_b=; Max-Age=0; Secure; Path=/", null)

                val pass = rows.all { (_, r) -> r.first == r.second }
                val text = buildString {
                    appendLine(if (pass) "PASS: profiles are isolated" else "FAIL: isolation broken")
                    appendLine()
                    rows.forEach { (label, r) ->
                        appendLine("${if (r.first == r.second) "✓" else "✗"} $label: ${r.first} (expected ${r.second})")
                    }
                }
                showText("Isolation test", text)
            }
        }
    }

    private fun showDiagnostics() {
        val wv = webView ?: return
        // WebAuthn capability checks are async, so start them first and read the results later.
        val probe = "(function(){var r=window.__jrnyWebAuthn={publicKeyCredential:typeof PublicKeyCredential};" +
            "if(typeof PublicKeyCredential==='undefined')return;" +
            "PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()" +
            ".then(function(v){r.platformAuthenticator=v},function(e){r.platformAuthenticator=String(e)});" +
            "if(PublicKeyCredential.isConditionalMediationAvailable)" +
            "PublicKeyCredential.isConditionalMediationAvailable()" +
            ".then(function(v){r.conditionalMediation=v},function(e){r.conditionalMediation=String(e)});" +
            "else r.conditionalMediation='not exposed';})()"
        wv.evaluateJavascript(probe, null)
        val js = "(function(){var d=navigator.userAgentData;return JSON.stringify({" +
            "userAgent:navigator.userAgent,brands:d?d.brands:null,mobile:d?d.mobile:null," +
            "platform:d?d.platform:null,webdriver:navigator.webdriver,webAuthn:window.__jrnyWebAuthn});})()"
        wv.postDelayed({ readDiagnostics(wv, js) }, 1000)
    }

    private fun readDiagnostics(wv: WebView, js: String) {
        wv.evaluateJavascript(js) { raw ->
            val navigatorJson = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault(raw)
            val cookies = currentProfileCookies()
            val text = buildString {
                appendLine("Profile: ${PROFILE_LABELS[profileIndex]} (${PROFILES[profileIndex]})")
                appendLine("UA mode: ${uaMode.label}")
                appendLine("Passkeys: ${if (passkeysForBrowser) "FOR_BROWSER" else "NONE"}")
                appendLine("WebView: ${webViewVersion()}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine(
                    "Features: MULTI_PROFILE=${feature(WebViewFeature.MULTI_PROFILE)}, " +
                        "USER_AGENT_METADATA=${feature(WebViewFeature.USER_AGENT_METADATA)}, " +
                        "WEB_AUTHENTICATION=${feature(WebViewFeature.WEB_AUTHENTICATION)}",
                )
                appendLine("Profiles on disk: ${ProfileStore.getInstance().allProfileNames}")
                appendLine()
                appendLine("navigator: $navigatorJson")
                appendLine()
                appendLine("Cookie names per site, current profile (values never shown):")
                COOKIE_SITES.forEach { site ->
                    val names = cookies.getCookie(site)
                        ?.split(";")
                        ?.map { it.substringBefore("=").trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()
                    appendLine("• $site → ${if (names.isEmpty()) "none" else "${names.size}: ${names.joinToString()}"}")
                }
            }
            showText("Diagnostics", text)
        }
    }

    /** Checks ADR 0001's claim that a loaded profile cannot be deleted while the app runs. */
    private fun deleteOtherProfile() {
        val other = PROFILES[1 - profileIndex]
        val result = runCatching { ProfileStore.getInstance().deleteProfile(other) }
        val immediate = result.fold(
            onSuccess = { "deleteProfile(\"$other\") returned $it" },
            onFailure = { "deleteProfile(\"$other\") threw ${it.javaClass.simpleName}: ${it.message}" },
        )
        if (result.isFailure) {
            val pending = prefs.getStringSet(KEY_PENDING_DELETE, emptySet()).orEmpty() + other
            prefs.edit().putStringSet(KEY_PENDING_DELETE, pending).apply()
        }
        showText(
            "Delete other profile",
            "$immediate\n\n" + if (result.isFailure) {
                "Marked for deletion. Force-stop and relaunch the app: it deletes the profile before loading any profile."
            } else {
                "Deleted immediately."
            },
        )
    }

    private fun deletePendingProfiles() {
        val pending = prefs.getStringSet(KEY_PENDING_DELETE, emptySet()).orEmpty()
        if (pending.isEmpty()) return
        val report = pending.map { name ->
            runCatching { ProfileStore.getInstance().deleteProfile(name) }.fold(
                onSuccess = { "$name: deleted=$it" },
                onFailure = { "$name: ${it.javaClass.simpleName}: ${it.message}" },
            )
        }
        prefs.edit().remove(KEY_PENDING_DELETE).apply()
        toast("Cold-start deletion → ${report.joinToString("; ")}")
    }

    // ---------------------------------------------------------------- helpers

    private fun showText(title: String, text: String) {
        val body = TextView(this).apply {
            this.text = text
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Copy") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(title, text))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun feature(name: String) = WebViewFeature.isFeatureSupported(name)

    private fun webViewVersion(): String =
        WebViewCompat.getCurrentWebViewPackage(this)
            ?.let { "${it.packageName} ${it.versionName}" } ?: "unknown"

    private companion object {
        val PROFILES = listOf("jrny-spike-a", "jrny-spike-b")
        val PROFILE_LABELS = listOf("A", "B")
        val PROFILE_COLORS = listOf(Color.rgb(0x1E, 0x5A, 0xC8), Color.rgb(0xC8, 0x5A, 0x1E))

        const val HOME = "https://accounts.google.com/"
        const val POPUP_TEST_URL = "https://jrny.test/popup"
        const val POPUP_TEST_HTML = "<!doctype html><meta name=viewport content='width=device-width'>" +
            "<title>JRNY popup test</title><h1>Popup test</h1>" +
            "<button id=open style='font-size:28px;padding:20px' " +
            "onclick=\"window.open('https://httpbin.org/cookies','jrnypopup','width=500,height=600')\">" +
            "Open popup</button>"
        const val PROBE_URL = "https://example.com"

        val SHORTCUTS = listOf(
            "Google" to "https://accounts.google.com/",
            "Microsoft (personal)" to "https://account.microsoft.com/",
            "Microsoft (work)" to "https://login.microsoftonline.com/",
            "GitHub" to "https://github.com/login",
            "Apple" to "https://account.apple.com/",
            "Google via Stack Overflow" to "https://stackoverflow.com/users/login",
            "Headers" to "https://httpbin.org/headers",
            "Cookies" to "https://httpbin.org/cookies",
        )

        val COOKIE_SITES = listOf(
            "https://accounts.google.com",
            "https://www.google.com",
            "https://login.microsoftonline.com",
            "https://login.live.com",
            "https://github.com",
            "https://account.apple.com",
            "https://idmsa.apple.com",
            "https://httpbin.org",
            "https://stackoverflow.com",
        )

        const val KEY_PROFILE = "profile"
        const val KEY_UA_MODE = "uaMode"
        const val KEY_PASSKEYS = "passkeys"
        const val KEY_URL = "url"
        const val KEY_PENDING_DELETE = "pendingDelete"

        fun chromeMajor(ua: String) = Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1) ?: "0"

        /** Chrome's reduced UA format, without WebView's "; wv" and "Version/4.0" markers. */
        fun chromeLikeUserAgent(defaultUa: String): String {
            val mobile = if (defaultUa.contains(" Mobile ")) "Mobile " else ""
            return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/${chromeMajor(defaultUa)}.0.0.0 ${mobile}Safari/537.36"
        }

        fun withoutQuery(url: String): String =
            Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString()
    }
}
