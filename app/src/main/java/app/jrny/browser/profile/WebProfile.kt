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

    companion object {
        /**
         * The store for the Profile [id], named `jrny-profile-<id>` (ADR 0004). The name never
         * uses WebView's own "Default" profile, so a page loaded outside any Profile can't see
         * a Profile's data.
         */
        fun of(id: ProfileId): WebProfile = WebProfile("jrny-profile-${id.value}")
    }
}
