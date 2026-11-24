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
                    // jar for what it embeds (Total Cookie Protection). Sign-in pop-ups still work.
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    // Blocking trackers and ads comes in 0.2, with a switch per site (ROADMAP).
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
        return created
    }

    /**
     * Applies the app's settings to every page, open or not: they belong to the whole engine.
     * [textZoom] is in percent; [pagesDark] asks pages for their dark theme (SPEC, "Dark pages").
     */
    fun apply(context: Context, textZoom: Int, safeBrowsing: Boolean, pagesDark: Boolean) {
        val settings = runtime(context).settings
        val factor = textZoom / 100f
        if (settings.fontSizeFactor != factor) settings.setFontSizeFactor(factor)
        val categories = if (safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE
        if (settings.contentBlocking.safeBrowsingCategories != categories) settings.contentBlocking.setSafeBrowsing(categories)
        val scheme = if (pagesDark) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
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
            if (extension == null) return@accept
            bridge = extension
            val waiting = waitingForBridge.toList()
            waitingForBridge.clear()
            waiting.forEach { it(extension) }
        }, { /* Pages still load; only icons, dark pages and files from the page wait for it. */ })
    }

    /**
     * Deletes everything the session context [contextId] keeps: cookies, site storage, cache.
     * GeckoView gives no word when the wipe is done, and one asked for before Gecko has started
     * might not reach it, so it is handed over only once Gecko has started ([whenStarted]).
     * [then] runs right after.
     */
    fun clearContext(context: Context, contextId: String, then: () -> Unit = {}) {
        val storage = runtime(context).storageController
        whenStarted {
            storage.clearDataForSessionContext(contextId)
            then()
        }
    }

    /**
     * Runs [then] once Gecko has started, now if it has. GeckoView has no call for that; the
     * built-in extension is installed only once it has. If the extension can't be installed,
     * [then] never runs.
     */
    private fun whenStarted(then: () -> Unit) = withBridge { then() }

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
