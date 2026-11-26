package app.jrny.browser.engine

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * uBlock Origin, built into the engine as Okapi's content blocker (ADR 0008). It ships unchanged
 * in the APK's assets (app/build.gradle.kts) and blocks in every Profile and in Private Tabs.
 * The person controls it with its own popup and dashboard; it keeps one list of trusted sites for
 * every Profile, because it can't tell which Profile a request comes from.
 */
object ContentBlocker {
    /** Where the build puts uBlock Origin in the app's assets. */
    const val LOCATION = "resource://android/assets/ublock/"
    const val ID = "uBlock0@raymondhill.net"

    /** uBlock Origin once it is installed, else null. */
    var extension: WebExtension? = null
        private set

    /** Installs uBlock Origin, or updates it to the version in the APK, and lets it work in Private Tabs. */
    fun install(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, ID).accept({ installed ->
            if (installed == null) return@accept
            extension = installed
            if (!installed.metaData.allowedInPrivateBrowsing) {
                controller.setAllowedInPrivateBrowsing(installed, true).accept({ allowed -> if (allowed != null) extension = allowed }, {})
            }
        }, {
            // Pages still load, unblocked. Nothing else depends on it.
        })
    }
}
