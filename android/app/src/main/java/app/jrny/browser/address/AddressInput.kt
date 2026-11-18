package app.jrny.browser.address

import java.net.URLEncoder

/**
 * Turns what a person typed into the address field into the address to load: either the web
 * address they meant, or a search for the text.
 *
 * Two rules keep private text away from the search engine:
 * - Anything that starts with `http://` or `https://` is never searched for. It loads, or,
 *   when it has no host at all, nothing happens.
 * - Anything shaped like credentials (`name:secret@host`) is never searched for either. It loads
 *   over https at that host, unless `name` is a scheme such as `mailto`: then nothing happens,
 *   because it isn't a web address and could still hide a password.
 *
 * Only `http` and `https` addresses are ever produced. Text with another scheme, such as
 * `javascript:` or `file:`, is searched for instead of being loaded, unless it carries
 * credentials (`ftp://user:secret@host`): then nothing happens.
 */
object AddressInput {

    /** The default search engine (SPEC, "Browsing features"). `%s` is the encoded query. */
    const val GOOGLE_SEARCH = "https://www.google.com/search?q=%s"

    /**
     * True when [input] would be searched for, not loaded as an address, and typing on can't
     * turn it into credentials. Only such text may be sent to the search engine for
     * suggestions: an address, or anything shaped like credentials, stays on the device while
     * it is typed, as it does when it is submitted.
     *
     * Suggestions are asked for at every pause in typing, so `admin:hunter2` is checked before
     * the `@nas.local` that makes it credentials arrives. Any colon before the first `/`, `?` or
     * `#` (after the scheme, if there is one) keeps the text on the device: an `@` typed later
     * would make what follows the colon a password.
     */
    fun isSearch(input: String): Boolean =
        !couldBecomeCredentials(input.trim()) && toAddress(input, SEARCH_MARKER)?.startsWith(SEARCH_MARKER) == true

    /** True when appending `@host` to [text] would make it `name:secret@host`. */
    private fun couldBecomeCredentials(text: String): Boolean {
        val afterScheme = WebAddress.schemeOf(text)?.let { text.substring(it.length + 3) } ?: text
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').contains(':')
    }

    private const val SEARCH_MARKER = "search:"

    /** Returns the address to load, or null when there is nothing to load. */
    fun toAddress(input: String, searchTemplate: String = GOOGLE_SEARCH): String? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val scheme = WebAddress.schemeOf(text)
        if (WebAddress.isWebScheme(scheme)) {
            val afterScheme = text.substring(scheme!!.length + 3)
            return text.takeIf { isAnyHost(WebAddress.authorityHost(afterScheme)) }
        }
        // Checked before anything that could search: text shaped like credentials may hold a
        // password, even with spaces in it ("user:two words@example.com").
        if (scheme == null && WebAddress.hasCredentials(text)) return credentialAddress(text)
        // Another scheme with credentials ("ftp://user:secret@example.com") isn't loaded, and its
        // password must not reach the search engine either: nothing happens.
        if (scheme != null && WebAddress.hasCredentials(text.substring(scheme.length + 3))) return null
        if (scheme == null && text.none(Char::isWhitespace)) {
            webAddressWithoutScheme(text)?.let { return it }
        }
        return searchTemplate.replace("%s", URLEncoder.encode(text, Charsets.UTF_8))
    }

    /**
     * Text shaped like `name:secret@host`, which never goes to the search engine. It loads over
     * https when it can be an address. Otherwise nothing happens:
     * - a space before the path means it can't be an address;
     * - "mailto:secret@example.com" is ambiguous (a mail link, or user "mailto" with a password).
     *   Handing mail links to apps comes with Incoming Links (Milestone 4).
     */
    private fun credentialAddress(text: String): String? {
        val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.any(Char::isWhitespace)) return null
        if (text.substringBefore(':').lowercase() in NON_WEB_SCHEMES) return null
        return "https://$text"
    }

    private fun webAddressWithoutScheme(text: String): String? {
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

    /** Schemes written without `//`, which are never loaded as `user:password@host`. */
    private val NON_WEB_SCHEMES = setOf(
        "about", "bitcoin", "blob", "chrome", "content", "data", "file", "geo", "intent", "javascript",
        "magnet", "mailto", "market", "mms", "news", "sms", "smsto", "tel", "urn", "webcal",
    )

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6 = Regex("^\\[[0-9a-fA-F:.]+]$")
    private val SINGLE_LABEL = Regex("^[\\p{L}\\p{N}](?:[\\p{L}\\p{N}_-]{0,61}[\\p{L}\\p{N}])?$")
    private val ANY_HOST = Regex("^[\\p{L}\\p{N}_.~%-]+$")

    /** Labels of letters, digits and hyphens, ending in a top-level domain of letters or punycode. */
    private val DOMAIN = Regex(
        "^(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?\\.)+(?:\\p{L}{2,63}|xn--[a-zA-Z0-9-]{1,59})\\.?$",
    )
}
