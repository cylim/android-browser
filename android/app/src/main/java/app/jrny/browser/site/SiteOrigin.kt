package app.jrny.browser.site

import java.net.URI
import java.net.URISyntaxException

/**
 * A site as Site settings know it: its scheme, host and port, such as `https://meet.google.com`
 * (SPEC, "Site permissions and Site settings"). A default port is left out, so the same site is
 * always written the same way. Plain Kotlin, so it is unit-tested on the JVM.
 */
@JvmInline
value class SiteOrigin private constructor(val text: String) {

    /** The host alone, for display. */
    val host: String get() = URI(text).host.orEmpty()

    companion object {
        /**
         * The origin of [address], an `http` or `https` address or an origin as the engine reports
         * one (`https://example.com/`). Null for any other scheme, or an address without a host.
         */
        fun of(address: String?): SiteOrigin? {
            if (address == null) return null
            val uri = try {
                URI(address.trim())
            } catch (_: URISyntaxException) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            val defaultPort = if (scheme == "https") 443 else 80
            val port = uri.port.takeIf { it != -1 && it != defaultPort }
            return SiteOrigin("$scheme://$host" + (port?.let { ":$it" } ?: ""))
        }
    }
}
