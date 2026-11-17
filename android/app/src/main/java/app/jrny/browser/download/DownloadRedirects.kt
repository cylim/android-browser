package app.jrny.browser.download

import app.jrny.browser.address.WebAddress
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds where a download really comes from, before it is handed to Android's download service.
 *
 * That service sends the `Cookie` header it was given to every address a download is redirected
 * to, whatever site that is. A Profile's session cookies must not travel to another site that
 * way. So the redirects are followed here first, one hop at a time, each hop getting only the
 * cookies that belong to its own address. The download service then starts at the last address,
 * with that address's cookies and nothing to follow.
 *
 * Each hop is asked with `HEAD`, so nothing is downloaded twice and a single-use link isn't spent.
 */
object DownloadRedirects {
    private const val MAX_HOPS = 5
    private const val TIMEOUT_MS = 5_000

    /**
     * The address [url] ends up at after its redirects, or the furthest one reached when a hop
     * can't be asked. [cookiesFor] gives the cookies that belong to an address. Call it off the
     * main thread.
     */
    fun resolve(url: String, userAgent: String, cookiesFor: (String) -> String?): String {
        var current = url
        repeat(MAX_HOPS) {
            val next = runCatching { nextHop(current, userAgent, cookiesFor(current)) }.getOrNull() ?: return current
            current = next
        }
        return current
    }

    /** Where [url] redirects to, or null when it doesn't, or not to a web address. */
    private fun nextHop(url: String, userAgent: String, cookies: String?): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
            if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
        }
        try {
            if (connection.responseCode !in 300..399) return null
            val location = connection.getHeaderField("Location") ?: return null
            return URL(URL(url), location).toString().takeIf(WebAddress::isWebAddress)
        } finally {
            connection.disconnect()
        }
    }
}
