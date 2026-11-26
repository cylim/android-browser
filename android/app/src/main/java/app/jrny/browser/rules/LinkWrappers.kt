package app.jrny.browser.rules

import app.jrny.browser.address.WebAddress
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * A Link Wrapper (GLOSSARY): a site, as a [SitePattern], whose links carry the address they pass
 * the person on to in one of the query [parameters], as Outlook's Safe Links do in `url`. A site
 * has one wrapper.
 */
data class LinkWrapper(val pattern: SitePattern, val parameters: List<String>) {
    companion object {
        /** A parameter's name can be this long. */
        const val MAX_PARAMETER_LENGTH = 64

        /** A wrapper can name this many parameters. */
        const val MAX_PARAMETERS = 5

        private val PARAMETER = Regex("[A-Za-z0-9._~-]+")

        /**
         * Reads a wrapper as a person types it: the site as for a Site Rule ([SitePattern.parse]),
         * which must name its own site ([namesItsSite]), and the parameters' names ([parametersOf]).
         * Null when either isn't one.
         */
        fun parse(pattern: String, parameters: String): LinkWrapper? {
            val site = SitePattern.parse(pattern)?.takeIf(::namesItsSite) ?: return null
            return LinkWrapper(site, parametersOf(parameters) ?: return null)
        }

        /**
         * A wrapper as it was stored, checked as [parse] checks a typed one: null when [parse]
         * wouldn't make the same wrapper of it.
         */
        fun of(pattern: String, parameters: List<String>): LinkWrapper? =
            parse(pattern, parameters.joinToString(","))?.takeIf { it.parameters == parameters }

        /**
         * The parameters' names in [text], separated by commas or spaces, each as it is written in a
         * link, without `=` or `&`, and each once. Null when there is none, too many, or one that
         * can't be a name.
         */
        fun parametersOf(text: String): List<String>? {
            val names = text.split(',', ' ').map(String::trim).filter(String::isNotEmpty).distinct()
            if (names.isEmpty() || names.size > MAX_PARAMETERS) return null
            return names.takeIf { all -> all.all { it.length <= MAX_PARAMETER_LENGTH && PARAMETER.matches(it) } }
        }

        /**
         * True when [pattern] names the site it covers: a `*` may stand for what comes before the
         * site's own name (`*.example.com`), but not for part of it (`*example.com`), nor for the
         * part a registry hands out (`*.com`, `example.*`), and that part alone (`com`, `co.uk`)
         * isn't a site. Otherwise the wrapper would read its parameters on sites that belong to
         * anyone.
         */
        fun namesItsSite(pattern: SitePattern): Boolean {
            val host = pattern.host
            if ('*' !in host) return !WebAddress.isPublicSuffix(host)
            // Read with a letter in each star's place; a star must stay out of the registrable domain.
            val (_, domain) = WebAddress.splitAtRegistrableDomain(host.replace('*', 'x'))
            return '*' !in host.takeLast(domain.length)
        }
    }
}

/**
 * The Link Wrappers in use: the [defaults] with the person's changes. [own] holds the wrappers
 * they added or changed, one per site, and [removed] the sites of the defaults they removed or
 * moved to another site. Only the changes are kept, so the defaults a later version adds still
 * reach a list the person changed.
 */
data class LinkWrapperList(
    val own: List<LinkWrapper> = emptyList(),
    val removed: Set<SitePattern> = emptySet(),
    val defaults: List<LinkWrapper> = LinkWrappers.DEFAULT,
) {
    /** True while the person hasn't changed the default list, or changed it back. */
    val isDefault: Boolean get() = own.isEmpty() && removed.isEmpty()

    /**
     * The wrappers in use, in the order they are shown: the defaults in their order, each as the
     * person changed it, without the ones removed, then the ones the person added.
     */
    val all: List<LinkWrapper>
        get() {
            val bySite = own.associateBy { it.pattern }
            val defaultSites = defaults.map { it.pattern }.toSet()
            return defaults.mapNotNull { bySite[it.pattern] ?: it.takeUnless { _ -> it.pattern in removed } } +
                own.filter { it.pattern !in defaultSites }
        }

    val wrappers: LinkWrappers get() = LinkWrappers(all)

    /**
     * Adds [wrapper], or puts it in [existing]'s place. A site has one wrapper, so one this
     * replaces for its site goes.
     */
    fun save(existing: LinkWrapper?, wrapper: LinkWrapper): LinkWrapperList {
        val target = existing?.pattern
        // One of the person's own takes [wrapper] in its place; any other for its site goes.
        val replaced = own.mapNotNull {
            when (it.pattern) {
                target -> wrapper
                wrapper.pattern -> null
                else -> it
            }
        }
        val changed = if (own.any { it.pattern == target }) replaced else replaced + wrapper
        val gone = if (target != null && target != wrapper.pattern) removed + target else removed
        return LinkWrapperList(changed, gone - wrapper.pattern, defaults).normalized()
    }

    fun delete(wrapper: LinkWrapper): LinkWrapperList =
        LinkWrapperList(own.filter { it.pattern != wrapper.pattern }, removed + wrapper.pattern, defaults).normalized()

    fun useDefault(): LinkWrapperList = LinkWrapperList(defaults = defaults)

    /**
     * Adds the wrappers [other] has of the person's own for the sites that have none here.
     * Nothing here is changed or removed.
     */
    fun merge(other: LinkWrapperList): LinkWrapperList {
        val here = all.map { it.pattern }.toSet()
        val missing = other.own.filter { it.pattern !in here }
        return if (missing.isEmpty()) this else LinkWrapperList(own + missing, removed - missing.map { it.pattern }.toSet(), defaults).normalized()
    }

    /**
     * The same list, kept as few changes as possible: a wrapper the same as its site's default
     * isn't one of the person's own, and only a default's site can be removed.
     */
    private fun normalized(): LinkWrapperList {
        val byDefaultSite = defaults.associateBy { it.pattern }
        val changed = own.distinctBy { it.pattern }.filter { byDefaultSite[it.pattern] != it }
        val gone = removed.filter { site -> site in byDefaultSite && changed.none { it.pattern == site } }.toSet()
        return LinkWrapperList(changed, gone, defaults)
    }

    companion object {
        val DEFAULT = LinkWrapperList()
    }
}

/**
 * The Link Wrappers that Site Rules see through: a wrapped Incoming Link is judged by the address
 * it carries. Only what the link itself says is read; nothing is fetched. Plain Kotlin, unit-tested
 * on the JVM.
 */
class LinkWrappers(val all: List<LinkWrapper>) {

    /**
     * The address [url] carries when it is a wrapped link, or null when it is none. A wrapper
     * inside a wrapper is read through too, [MAX_DEPTH] deep.
     */
    fun unwrap(url: String): String? = layers(url).drop(1).lastOrNull()

    /** [url], then each address it carries, outermost first, as far as [unwrap] reads. */
    fun layers(url: String): List<String> {
        val layers = mutableListOf(url)
        while (layers.size <= MAX_DEPTH) layers += carried(layers.last()) ?: break
        return layers
    }

    /**
     * The web address [url] carries in the parameters of the wrappers that cover it. Every value
     * they give must be that one address, percent-decoded: the site may read any of them, so a
     * second address, one given twice, one badly encoded or one that isn't a web address, and the
     * link carries none that can be trusted.
     */
    private fun carried(url: String): String? {
        val names = all.filter { it.pattern.matches(url) }.flatMap { it.parameters }.map(String::lowercase).toSet()
        if (names.isEmpty()) return null
        // The fragment never reaches the site, so only the query before it counts.
        val values = url.substringBefore('#').substringAfter('?', "").split('&').mapNotNull { pair ->
            // The site may read a name in another case, percent-encoded, or with a tab or line
            // break in it as the same name, and a name that can't be decoded as any name.
            val name = percentDecode(pair.substringBefore('=').filterNot { it in "\t\r\n" }) ?: return null
            if (name.lowercase() in names) percentDecode(pair.substringAfter('=', "")) ?: return null else null
        }
        // An empty value names no address, as the empty q next to url in a copied Google result.
        return values.filter(String::isNotEmpty).distinct().singleOrNull()
            ?.takeIf { it.length <= MAX_URL && WebAddress.hostOf(it).isNotEmpty() }
    }

    companion object {
        /** How many wrappers deep [unwrap] reads: a link wrapped more often than this is read this far. */
        const val MAX_DEPTH = 4

        /** A carried address longer than any page gives isn't read. */
        private const val MAX_URL = 64 * 1024

        /**
         * The wrappers of the common mail and chat apps, each of which passes the person on to the
         * address in its parameter. Each one is a site whose links say where they go; trackers
         * whose links don't, such as `t.co`, can't be listed.
         */
        val DEFAULT: List<LinkWrapper> = listOf(
            "safelinks.protection.outlook.com" to "url",
            "safelinks.protection.office365.us" to "url",
            "google.com/url" to "q url",
            "l.facebook.com/l.php" to "u",
            "lm.facebook.com/l.php" to "u",
            "l.messenger.com/l.php" to "u",
            "l.instagram.com" to "u",
            "youtube.com/redirect" to "q",
            "slack-redir.net/link" to "url",
            "linkedin.com/redir/redirect" to "url",
        ).map { (pattern, parameters) -> requireNotNull(LinkWrapper.parse(pattern, parameters)) }

        /**
         * Decodes `%XX` as UTF-8, and nothing else: a `+` stays a `+`, since a wrapped address
         * that means a space encodes it. Null for a `%` without two hex digits after it, or bytes
         * that aren't UTF-8.
         */
        internal fun percentDecode(text: String): String? {
            if ('%' !in text) return text
            val bytes = ByteArrayOutputStream(text.length)
            var i = 0
            while (i < text.length) {
                if (text[i] == '%') {
                    if (i + 3 > text.length) return null
                    // Exactly two hexadecimal digits: toIntOrNull would also take a sign, as in "%+A".
                    val digits = text.substring(i + 1, i + 3)
                    if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
                    bytes.write(digits.toInt(16))
                    i += 3
                } else {
                    val next = text.indexOf('%', i).let { if (it < 0) text.length else it }
                    bytes.write(text.substring(i, next).toByteArray(Charsets.UTF_8))
                    i = next
                }
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }
    }
}
