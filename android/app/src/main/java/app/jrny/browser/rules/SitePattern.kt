package app.jrny.browser.rules

import app.jrny.browser.address.WebAddress
import java.net.IDN

/**
 * The addresses a Site Rule covers (GLOSSARY, "Site Rule"): a site, and optionally a path on it.
 *
 * - `example.com` covers that site and every site under it (`www.example.com`, `mail.example.com`).
 * - `example.com/work` covers that path and everything under it (`/work/today`, not `/workshop`).
 * - `*` stands for any run of characters: `*.example.com` covers only the sites under
 *   `example.com`, and `example.com/` + `*` + `/issues` covers every `issues` path one or more
 *   levels down.
 *
 * Only the site and the path count. A link's scheme, port, credentials, query and fragment are
 * ignored, and so is the case of the site's name. Plain Kotlin, unit-tested on the JVM.
 */
class SitePattern private constructor(val host: String, val path: String) {

    /** How the pattern is written, shown and stored. Two patterns with the same text are the same. */
    val text: String get() = host + path

    /** True when the pattern covers the web address [url]. Never true for anything but `http` and `https`. */
    fun matches(url: String): Boolean {
        val address = asABrowserReadsIt(url)
        val linkHost = asciiHost(WebAddress.hostOf(address).lowercase().trimEnd('.')) ?: return false
        if (linkHost.isEmpty()) return false
        val hostMatches = if ('*' in host) glob(host, linkHost) else linkHost == host || linkHost.endsWith(".$host")
        if (!hostMatches) return false
        if (path.isEmpty()) return true
        val linkPath = pathOf(address)
        // The path itself, or anything under it.
        return glob(path, linkPath) || glob(path.trimEnd('/') + "/*", linkPath)
    }

    /**
     * How narrow the pattern is: one that names more of the site wins over one that names less,
     * and between two that name the same site, the one that names more of the path. So a rule for
     * `mail.example.com` wins over one for `example.com`, and `example.com/work` over both for
     * the addresses under that path of `example.com` itself.
     */
    val weight: Weight get() = Weight(host.count { it != '*' }, path.count { it != '*' })

    data class Weight(val host: Int, val path: Int) : Comparable<Weight> {
        override fun compareTo(other: Weight): Int = compareValuesBy(this, other, Weight::host, Weight::path)
    }

    override fun equals(other: Any?): Boolean = other is SitePattern && other.text == text
    override fun hashCode(): Int = text.hashCode()
    override fun toString(): String = text

    companion object {
        /** A pattern, as it is stored, can be this long. */
        const val MAX_LENGTH = 200

        /** What is typed or pasted is read up to this length: a whole address, query and all. */
        const val MAX_INPUT_LENGTH = 2_000

        /**
         * Reads what a person typed or pasted as a pattern: a site (`example.com`), a site with a
         * path, or a whole address, of which the site and path are kept. A leading `www.` is
         * dropped, since the site without it covers it too. Null when there is no site in it, the
         * address isn't a web address, or the pattern would be longer than [MAX_LENGTH].
         */
        fun parse(input: String): SitePattern? {
            var rest = input.trim()
            if (rest.isEmpty() || rest.length > MAX_INPUT_LENGTH || rest.any { it.isWhitespace() }) return null
            val scheme = WebAddress.schemeOf(rest)
            if (scheme != null) {
                if (!WebAddress.isWebScheme(scheme)) return null
                rest = rest.substringAfter("://")
            }
            rest = rest.substringBefore('?').substringBefore('#')
            val withCredentials = rest.substringBefore('/')
            // Without "http://" in front, "mailto:someone@example.com" is not an address of example.com.
            if (scheme == null && '@' in withCredentials) return null
            val authority = withCredentials.substringAfterLast('@')
            // A port doesn't count. Bracketed (IPv6) hosts aren't supported.
            if (!authority.substringAfter(':', "").all(Char::isDigit)) return null
            val typed = authority.substringBefore(':').lowercase().trimEnd('.').removePrefix("www.")
            val host = asciiHost(typed) ?: return null
            if (host.isEmpty() || host.startsWith(".") || ".." in host) return null
            if (!host.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' || it == '*' }) return null
            // "*" alone, or "*.*", would send every link to one Profile.
            if (host.none { it.isLetterOrDigit() }) return null
            val path = if ('/' in rest) resolveDots("/" + rest.substringAfter('/')) else ""
            // "example.com/" is the whole site, as "example.com" is.
            val pattern = SitePattern(host, collapseStars(path).takeIf { it != "/" && it != "/*" }.orEmpty())
            // Measured as it is stored: a name in its own script grows when spelled in ASCII.
            return pattern.takeIf { it.text.length <= MAX_LENGTH }
        }

        /**
         * The web address a person means by [typed], for checking it against the rules: itself
         * when it is one, and with `https://` in front when it starts with a site, as
         * `example.com/page` does. Null for anything else.
         */
        fun linkFrom(typed: String): String? {
            val text = typed.trim()
            return when {
                WebAddress.schemeOf(text) != null -> text.takeIf { WebAddress.hostOf(it).isNotEmpty() }
                '*' !in text && parse(text) != null -> "https://$text"
                else -> null
            }
        }

        /** The pattern for the site of the web address [url], or null if it has none. */
        fun forSiteOf(url: String?): SitePattern? = WebAddress.hostOf(url).takeIf { it.isNotEmpty() }?.let(::parse)

        /**
         * A site's name the way it is compared: in ASCII, where a name in its own script
         * (`münchen.de`) is spelled `xn--mnchen-3ya.de`. A link can come either way, and so can
         * what a person types. Null when it can't be a site's name.
         */
        private fun asciiHost(host: String): String? = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrNull()

        private fun collapseStars(text: String): String = text.replace(Regex("\\*+"), "*")

        /**
         * A browser drops tabs and line breaks from a web address and reads `\` as `/`. The rule
         * must judge the site the page will really load from: in `https://evil.test\@example.com/`
         * that is `evil.test`, and `example.com` is only part of its path.
         */
        private fun asABrowserReadsIt(url: String): String =
            url.filterNot { it == '\t' || it == '\n' || it == '\r' }.replace('\\', '/')

        /**
         * The path of a web address as the page is loaded from it: `/` when it has none, without
         * query or fragment, and with its `.` and `..` steps taken.
         */
        private fun pathOf(url: String): String {
            val afterAuthority = url.substringAfter("://").substringBefore('?').substringBefore('#')
            return if ('/' in afterAuthority) resolveDots("/" + afterAuthority.substringAfter('/')) else "/"
        }

        /**
         * Takes the `.` and `..` steps of [path], also when a dot is spelled `%2e`, as a browser
         * does before it loads the page: `/work/../home` is `/home`, and a rule for `/work`
         * doesn't cover it.
         */
        private fun resolveDots(path: String): String {
            val steps = path.removePrefix("/").split('/')
            val kept = ArrayList<String>()
            steps.forEachIndexed { index, step ->
                val last = index == steps.lastIndex
                when (step.replace("%2e", ".", ignoreCase = true)) {
                    "." -> if (last) kept += ""
                    ".." -> {
                        kept.removeLastOrNull()
                        if (last) kept += ""
                    }
                    else -> kept += step
                }
            }
            return "/" + kept.joinToString("/")
        }

        /**
         * True when [text] is [pattern] with each `*` replaced by any run of characters, an empty
         * one included. One pass with a single point to fall back to, so a long pattern can't be
         * made slow.
         */
        internal fun glob(pattern: String, text: String): Boolean {
            var p = 0
            var t = 0
            var star = -1
            var afterStar = 0
            while (t < text.length) {
                when {
                    p < pattern.length && pattern[p] == '*' -> {
                        star = p++
                        afterStar = t
                    }
                    p < pattern.length && pattern[p] == text[t] -> {
                        p++
                        t++
                    }
                    star >= 0 -> {
                        p = star + 1
                        t = ++afterStar
                    }
                    else -> return false
                }
            }
            while (p < pattern.length && pattern[p] == '*') p++
            return p == pattern.length
        }
    }
}
