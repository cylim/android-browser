package app.jrny.browser.address

import java.net.URLEncoder

/**
 * Turns what a person typed into the address field into the address to load: either the web
 * address they meant, or a search for the text.
 *
 * Two rules keep private text away from the search engine:
 * - Anything that starts with `http://` or `https://` is never searched for. It loads, or,
 *   when it has no host at all, nothing happens.
 * - Anything with credentials (`user:password@host`) is never searched for either.
 *
 * Only `http` and `https` addresses are ever produced. Text with another scheme, such as
 * `javascript:` or `file:`, is searched for instead of being loaded.
 */
object AddressInput {

    /** The default search engine (SPEC, "Browsing features"). `%s` is the encoded query. */
    const val GOOGLE_SEARCH = "https://www.google.com/search?q=%s"

    /** Returns the address to load, or null when there is nothing to load. */
    fun toAddress(input: String, searchTemplate: String = GOOGLE_SEARCH): String? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val scheme = WebAddress.schemeOf(text)
        if (WebAddress.isWebScheme(scheme)) {
            val afterScheme = text.substring(scheme!!.length + 3)
            return text.takeIf { isAnyHost(WebAddress.authorityHost(afterScheme)) }
        }
        if (scheme == null && text.none(Char::isWhitespace)) {
            webAddressWithoutScheme(text)?.let { return it }
        }
        return searchTemplate.replace("%s", URLEncoder.encode(text, Charsets.UTF_8))
    }

    private fun webAddressWithoutScheme(text: String): String? {
        if (WebAddress.hasCredentials(text)) return "https://$text"
        // A bare email address, such as me@example.com, is a search, not an address.
        if (text.substringBefore('/').contains('@')) return null
        val host = WebAddress.authorityHost(text)
        if (isPublicHost(host)) return "https://$text"
        // A single-label host, such as a router or NAS, counts as an address when a port or a
        // slash makes it clear: "nas:8080", "nas/". A bare word is a search.
        if (SINGLE_LABEL.matches(host) && text.length > host.length) return "https://$text"
        return null
    }

    /** A host typed without a scheme: a domain with a top-level domain, localhost, or an IP. */
    private fun isPublicHost(host: String): Boolean = when {
        host.isEmpty() -> false
        host.equals("localhost", ignoreCase = true) -> true
        host.startsWith("[") -> IPV6.matches(host)
        IPV4.matches(host) -> host.split('.').all { it.toInt() <= 255 }
        else -> DOMAIN.matches(host)
    }

    /** With an explicit `http(s)://`, any well-formed host is accepted: intranet names, underscores. */
    private fun isAnyHost(host: String): Boolean = when {
        host.isEmpty() -> false
        host.startsWith("[") -> IPV6.matches(host)
        else -> ANY_HOST.matches(host)
    }

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6 = Regex("^\\[[0-9a-fA-F:.]+]$")
    private val SINGLE_LABEL = Regex("^[\\p{L}\\p{N}](?:[\\p{L}\\p{N}_-]{0,61}[\\p{L}\\p{N}])?$")
    private val ANY_HOST = Regex("^[\\p{L}\\p{N}_.~%-]+$")

    /** Labels of letters, digits and hyphens, ending in a top-level domain of letters or punycode. */
    private val DOMAIN = Regex(
        "^(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?\\.)+(?:\\p{L}{2,63}|xn--[a-zA-Z0-9-]{1,59})\\.?$",
    )
}
