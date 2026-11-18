package app.jrny.browser.download

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** An image's bytes and its MIME type. */
class FetchedImage(val bytes: ByteArray, val mimeType: String)

/**
 * Fetches an image the person held on a page, for "Copy image" and "Share image" (SPEC, "Link
 * and image menu"). It goes with the cookies of the Tab's own store, and like a download it
 * follows redirects one hop at a time, so each site gets only its own cookies
 * ([DownloadRedirects]). Call it off the main thread.
 */
object ImageFetch {
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 25 * 1024 * 1024

    fun fetch(url: String, userAgent: String, referrer: String?, cookiesFor: (String) -> String?): FetchedImage? = runCatching {
        val address = DownloadRedirects.resolve(url, userAgent, cookiesFor)
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
            if (!referrer.isNullOrBlank()) setRequestProperty("Referer", referrer)
            cookiesFor(address)?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (!type.startsWith("image/")) return null
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) return null
                }
            }
            FetchedImage(out.toByteArray(), type)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
