package app.jrny.browser.download

import java.net.URLDecoder
import java.util.Base64

/**
 * A `data:` address, which holds its file in itself: `data:[type][;base64],content` (RFC 2397).
 * Plain Kotlin, so it is unit-tested on the JVM.
 */
class DataAddress private constructor(
    /** The MIME type it names, `text/plain` when it names none. */
    val mimeType: String,
    val bytes: ByteArray,
) {
    companion object {
        /** The file in [address], or null when it isn't a well-formed `data:` address. */
        fun decode(address: String): DataAddress? {
            if (!address.startsWith("data:", ignoreCase = true)) return null
            val comma = address.indexOf(',')
            if (comma < 0) return null
            val header = address.substring(5, comma)
            val content = address.substring(comma + 1)
            val parts = header.split(';').map { it.trim() }
            val base64 = parts.drop(1).any { it.equals("base64", ignoreCase = true) }
            val type = parts.first().lowercase().ifEmpty { "text/plain" }
            val bytes = try {
                if (base64) {
                    Base64.getDecoder().decode(URLDecoder.decode(content.replace("+", "%2B"), "UTF-8").filterNot { it.isWhitespace() })
                } else {
                    URLDecoder.decode(content.replace("+", "%2B"), "UTF-8").toByteArray()
                }
            } catch (_: IllegalArgumentException) {
                return null
            }
            return DataAddress(type, bytes)
        }
    }
}
