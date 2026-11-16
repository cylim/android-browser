package app.jrny.browser.address

/**
 * Small facts about web addresses, shared by the address field, the Tab and the Sidebar so they
 * all agree on what a web page is. Plain Kotlin, no Android types, so it is unit-tested on the JVM.
 */
object WebAddress {

    /** Only `http` and `https` pages load in a Tab. Every other scheme is dropped. */
    fun isWebScheme(scheme: String?): Boolean =
        scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)

    /** The scheme of `scheme://…`, or null when [address] doesn't start with one. */
    fun schemeOf(address: String): String? = SCHEME.find(address)?.groupValues?.get(1)

    /** True for an `http://` or `https://` address. */
    fun isWebAddress(address: String?): Boolean = address != null && isWebScheme(schemeOf(address))

    /**
     * The host of a web address, without credentials, port or brackets' contents changed, for
     * example `en.wikipedia.org` or `[::1]`. Empty when [address] isn't a web address.
     */
    fun hostOf(address: String?): String {
        if (!isWebAddress(address)) return ""
        return authorityHost(address!!.substringAfter("://"))
    }

    /**
     * The host part of `[user[:password]@]host[:port][/path][?query][#fragment]`. Empty when the
     * port isn't a number.
     */
    internal fun authorityHost(afterScheme: String): String {
        val authority = afterScheme
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@')
        if (authority.startsWith("[")) return authority.substringBefore(']') + "]"
        val port = authority.substringAfter(':', missingDelimiterValue = "")
        if (authority.contains(':') && (port.isEmpty() || !port.all(Char::isDigit))) return ""
        return authority.substringBefore(':')
    }

    /** True when [afterScheme] starts with `user:password@`. */
    internal fun hasCredentials(afterScheme: String): Boolean {
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.contains('@') && authority.substringBeforeLast('@').contains(':')
    }

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")
}
