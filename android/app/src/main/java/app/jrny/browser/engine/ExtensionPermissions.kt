package app.jrny.browser.engine

/**
 * What an extension asks for, as Okapi tells the person: whether it reads every site or which
 * sites, its other permissions, and what it says it sends to its developer.
 */
data class ExtensionPermissions(
    /** True when it can read and change every site. */
    val allSites: Boolean,
    /** The sites it can read and change, by name, when not every site. */
    val sites: List<String>,
    /** Its other permissions, by their names in the manifest, such as `tabs` or `history`. */
    val permissions: List<String>,
    /** What it says it sends its developer, by Firefox's names, such as `browsingActivity`. */
    val dataCollection: List<String>,
) {
    companion object {
        fun of(permissions: Array<out String>, origins: Array<out String>, dataCollection: Array<out String>): ExtensionPermissions {
            val all = origins.any { it in ALL_SITES || isEveryHost(it) }
            return ExtensionPermissions(
                allSites = all,
                sites = if (all) emptyList() else origins.mapNotNull(::siteOf).distinct(),
                permissions = permissions.distinct(),
                dataCollection = dataCollection.filter { it != NO_DATA }.distinct(),
            )
        }

        /** The site a web match pattern names, without scheme, leading wildcard, port or path; null for a pattern that names no one web site. */
        internal fun siteOf(pattern: String): String? {
            val scheme = pattern.substringBefore("://", missingDelimiterValue = "")
            if (scheme !in WEB_SCHEMES) return null
            val host = pattern.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("*.")
            return host.takeIf { it.isNotEmpty() && it != "*" }
        }

        /** A web pattern whose host is `*`, such as one for a path on every site. */
        private fun isEveryHost(pattern: String): Boolean {
            val scheme = pattern.substringBefore("://", missingDelimiterValue = "")
            return scheme in WEB_SCHEMES && pattern.substringAfter("://").substringBefore('/') == "*"
        }

        /** Patterns for every web page. */
        private val ALL_SITES = setOf("<all_urls>", "*://*/*", "http://*/*", "https://*/*")
        private val WEB_SCHEMES = setOf("*", "http", "https")

        /** Firefox's word for an extension that sends nothing. */
        private const val NO_DATA = "none"
    }
}
