package app.jrny.browser.tab

/**
 * The user agent a Tab sends for Desktop site (SPEC, "Desktop site"): the one Chrome on a Linux
 * desktop sends, with the same Chrome major version as the device's WebView. Plain Kotlin, so it
 * is unit-tested on the JVM.
 */
object UserAgents {
    private val CHROME = Regex("""Chrome/(\d+)""")

    /** The desktop user agent for a WebView whose own user agent is [mobile]. */
    fun desktop(mobile: String): String {
        val major = CHROME.find(mobile)?.groupValues?.get(1) ?: FALLBACK_MAJOR
        // Chrome reduces its user agent: only the major version, then zeros.
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    /** True for a user agent [desktop] made. */
    fun isDesktop(userAgent: String?): Boolean = userAgent != null && "X11; Linux x86_64" in userAgent && "Android" !in userAgent

    private const val FALLBACK_MAJOR = "145"

    /**
     * Run at the start of every page: while the Tab asks for desktop sites, a page that lays
     * itself out at the device's width gets a desktop width instead, as Chrome's desktop mode
     * does. In a mobile Tab it does nothing.
     */
    const val DESKTOP_VIEWPORT_SCRIPT =
        "(function(){if(navigator.userAgent.indexOf('X11; Linux x86_64')<0)return;" +
            "function fix(){var m=document.querySelector('meta[name=viewport]');" +
            "if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m)}" +
            "if(m.content!=='width=1280')m.content='width=1280'}" +
            "fix();new MutationObserver(fix).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['content']})})()"
}
