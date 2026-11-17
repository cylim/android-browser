package app.jrny.browser.profile

import android.annotation.SuppressLint
import android.webkit.CookieManager
import androidx.webkit.ProfileStore

/**
 * The web data store of one Profile: an androidx.webkit profile holding its cookies, site
 * storage, cache and service workers (ADR 0001). Nothing in one store is visible from another.
 *
 * Call only on the main thread, and only when `WebViewFeature.MULTI_PROFILE` is supported.
 */
@SuppressLint("RequiresFeature") // Callers check MULTI_PROFILE first (see the class comment).
class WebProfile private constructor(val name: String) {

    /** This Profile's cookies. Separate from `CookieManager.getInstance()`, which is WebView's default profile. */
    val cookieManager: CookieManager
        get() = ProfileStore.getInstance().getOrCreateProfile(name).cookieManager

    /**
     * Writes cookies to disk now. WebView otherwise flushes only periodically, and the
     * Milestone 0 spike lost a cookie set seconds before a force-stop.
     */
    fun flush() = cookieManager.flush()

    /**
     * Empties the store as far as WebView allows while the app runs: cookies, site storage and
     * location permissions. WebView can't delete a store this process has loaded (ADR 0001), so
     * the rest (its cache and folder) goes at the next cold start ([wipePrivateStores]).
     */
    fun clear() {
        val store = ProfileStore.getInstance().getOrCreateProfile(name)
        store.cookieManager.removeAllCookies(null)
        store.webStorage.deleteAllData()
        store.geolocationPermissions.clearAll()
        store.cookieManager.flush()
    }

    companion object {
        /**
         * The store for the Profile [id], named `jrny-profile-<id>` (ADR 0004). The name never
         * uses WebView's own "Default" profile, so a page loaded outside any Profile can't see
         * a Profile's data.
         */
        fun of(id: ProfileId): WebProfile = WebProfile("jrny-profile-${id.value}")

        /** The store called [name], which one of the functions here gave out before. */
        fun named(name: String): WebProfile = WebProfile(name)

        /**
         * The temporary store for one private session of the Profile [id] (SPEC, "Private Tabs").
         * Each session has a store of its own, so a new session never meets what an earlier one
         * left behind, whatever [clear] could not remove.
         */
        fun privateOf(id: ProfileId, session: Int): WebProfile = WebProfile("$PRIVATE_PREFIX${id.value}-$session")

        /**
         * Deletes every private store left on disk. Call it at cold start, before any WebView
         * loads a store: only then can WebView delete them.
         */
        fun wipePrivateStores() {
            val stores = ProfileStore.getInstance()
            stores.allProfileNames.filter { it.startsWith(PRIVATE_PREFIX) }.forEach { name ->
                // A store that is somehow in use can't be deleted now. It is tried again at the
                // next cold start, and no session ever reuses its name within one run.
                runCatching { stores.deleteProfile(name) }
            }
        }

        private const val PRIVATE_PREFIX = "jrny-private-"
    }
}
