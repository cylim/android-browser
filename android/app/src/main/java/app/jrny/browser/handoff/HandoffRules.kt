package app.jrny.browser.handoff

/**
 * When a link a page opens goes to another app (GLOSSARY, "Handoff"; SPEC, "Links to other
 * apps"). Plain Kotlin, so it is unit-tested on the JVM.
 */
object HandoffRules {

    /** Phone, mail, message and map links: what they do is plain from the link itself. */
    private val AT_ONCE = setOf("tel", "mailto", "sms", "smsto", "geo")

    /**
     * True when a link with [scheme] goes to its app straight away: one of [AT_ONCE], [tapped]
     * by the person, and not from a Private Tab. Every other handoff waits for "Open".
     */
    fun goesAtOnce(scheme: String?, tapped: Boolean, private: Boolean): Boolean =
        !private && tapped && scheme?.lowercase() in AT_ONCE

    /** Schemes that never leave the page: they load in it, or nowhere. */
    private val PAGE_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript", "file", "content", "chrome")

    /** True when a link with [scheme] is for another app, not for the page. */
    fun isForAnotherApp(scheme: String?): Boolean = scheme != null && scheme.lowercase() !in PAGE_SCHEMES
}
