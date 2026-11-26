package app.jrny.browser.engine

import android.app.Application
import android.content.Context
import android.os.Looper
import app.jrny.browser.BuildConfig
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

/**
 * The browser engine: one GeckoView runtime for the whole app (ADR 0006). Every Tab's
 * GeckoSession runs in it, and each Profile is a session context inside it (ADR 0004).
 *
 * Create and use it on the main thread, and only in the app's main process: GeckoView's content
 * processes start the Application too, and must not start a second runtime.
 */
object Engine {
    private var runtime: GeckoRuntime? = null

    /** The built-in extension that lets Okapi talk to pages ([PageBridge]), once it is installed. */
    private var bridge: WebExtension? = null

    private val waitingForBridge = mutableListOf<(WebExtension) -> Unit>()

    /** True once Gecko has started and answered the bridge's install, whether it worked or not. */
    private var started = false

    private val waitingForStart = mutableListOf<() -> Unit>()

    /** The runtime, made on first use with the app's settings for every page. */
    fun runtime(context: Context): GeckoRuntime {
        check(Looper.myLooper() == Looper.getMainLooper()) { "GeckoRuntime on the main thread only" }
        runtime?.let { return it }
        val app = context.applicationContext
        val settings = GeckoRuntimeSettings.Builder()
            // Page console output, and GeckoView's own messages with page addresses in them, stay
            // out of the system log (SPEC, "Data protection").
            .consoleOutput(false)
            .debugLogging(false)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .aboutConfigEnabled(false)
            .extensionsWebAPIEnabled(false)
            // Okapi keeps no passwords itself; Android Autofill fills them (SPEC, "Browsing features").
            .loginAutofillEnabled(false)
            .automaticFontSizeAdjustment(false)
            .doubleTapZoomingEnabled(true)
            .inputAutoZoomEnabled(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    // Sites can't follow the person across sites with cookies: each site gets its own
                    // jar for what it embeds (Total Cookie Protection). Sign-in popups still work.
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    // uBlock Origin blocks ads and trackers (ContentBlocker); the engine's own
                    // tracking protection stays off, so one list decides what a page loads.
                    .antiTracking(ContentBlocking.AntiTracking.NONE)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .build(),
            )
            .build()
        val created = GeckoRuntime.create(app, settings)
        runtime = created
        setPrefs()
        installBridge(created)
        ContentBlocker.install(created, app)
        return created
    }

    /**
     * Applies [pages] to every page, open or not: these settings belong to the whole engine.
     */
    fun applyPageSettings(context: Context, pages: PageSettings) {
        val settings = runtime(context).settings
        val factor = pages.textZoom / 100f
        if (settings.fontSizeFactor != factor) settings.setFontSizeFactor(factor)
        val categories = if (pages.safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE
        if (settings.contentBlocking.safeBrowsingCategories != categories) settings.contentBlocking.setSafeBrowsing(categories)
        val scheme = if (pages.dark) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        if (settings.preferredColorScheme != scheme) settings.setPreferredColorScheme(scheme)
    }

    /** True while Safe Browsing checks pages. */
    fun safeBrowsing(context: Context): Boolean =
        runtime(context).settings.contentBlocking.safeBrowsingCategories != ContentBlocking.SafeBrowsing.NONE

    /**
     * Engine preferences GeckoView has no setting for. A PDF the site sends as a file to save
     * (an attachment) is offered as a download, as any file is; one it sends as a page still
     * shows in the engine's PDF viewer (SPEC, "Browsing features").
     */
    @androidx.annotation.OptIn(markerClass = [ExperimentalGeckoViewApi::class])
    private fun setPrefs() {
        for (name in PDF_ATTACHMENT_PREFS) {
            GeckoPreferenceController.setGeckoPref(name, false, GeckoPreferenceController.PREF_BRANCH_DEFAULT)
        }
    }

    /** The engine's preferences that open a PDF attachment in its viewer, all turned off. */
    internal val PDF_ATTACHMENT_PREFS = listOf("browser.download.open_pdf_attachments_inline", "pdfjs.handleOctetStream", "pdfjs.openAttachmentsInNewTab")

    /** Runs [then] with the bridge extension, now if it is installed, else once it is. */
    fun withBridge(then: (WebExtension) -> Unit) {
        val ready = bridge
        if (ready != null) then(ready) else waitingForBridge += then
    }

    private fun installBridge(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(PageBridge.LOCATION, PageBridge.ID).accept({ extension ->
            if (extension != null) {
                bridge = extension
                val waiting = waitingForBridge.toList()
                waitingForBridge.clear()
                waiting.forEach { it(extension) }
            }
            markStarted()
        }, {
            // Pages still load; only icons, dark pages and files from the page wait for it.
            markStarted()
        })
    }

    /** Gecko answers the install only once it has started, so either answer means it has. */
    private fun markStarted() {
        if (started) return
        started = true
        val waiting = waitingForStart.toList()
        waitingForStart.clear()
        waiting.forEach { it() }
    }

    /**
     * Deletes everything the session context [contextId] keeps: cookies, site storage, cache.
     * One asked for before Gecko has started might not reach it, so it is handed over only once
     * Gecko has started ([whenStarted]). GeckoView gives no word when the wipe is done; [handedOver]
     * runs right after it is handed over.
     */
    fun clearContext(context: Context, contextId: String, handedOver: () -> Unit = {}) {
        val storage = runtime(context).storageController
        whenStarted {
            storage.clearDataForSessionContext(contextId)
            handedOver()
        }
    }

    /**
     * Runs [then] once Gecko has started, now if it has. GeckoView has no call for that; it
     * answers the built-in extension's install once it has, whether the install worked or not.
     */
    private fun whenStarted(then: () -> Unit) {
        if (started) then() else waitingForStart += then
    }

    /**
     * Clears the engine's cache of pages and images. The cache is kept apart for each Profile, so no
     * Profile can read another's, but it can only be cleared for all of them at once.
     */
    fun clearCaches(context: Context) {
        runtime(context).storageController.clearData(StorageController.ClearFlags.ALL_CACHES)
    }

    /** True in the app's own process, where the runtime may live; false in GeckoView's content processes. */
    fun isMainProcess(app: Application): Boolean = Application.getProcessName() == app.packageName
}

/** The app's settings that belong to the engine, so to every page at once. */
data class PageSettings(
    /** How large pages' text is, in percent (SPEC, "Text size"). */
    val textZoom: Int,
    /** Whether Safe Browsing checks pages. */
    val safeBrowsing: Boolean,
    /** Whether pages are asked for their dark theme (SPEC, "Dark pages"). */
    val dark: Boolean,
)
