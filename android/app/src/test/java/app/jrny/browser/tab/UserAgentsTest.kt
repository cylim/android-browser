package app.jrny.browser.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentsTest {

    private val webView =
        "Mozilla/5.0 (Linux; Android 17; Pixel 7 Build/CP1A.260405.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/145.0.7632.218 Mobile Safari/537.36"

    @Test
    fun theDesktopAgentKeepsChromesMajorVersionAndDropsAndroid() {
        val desktop = UserAgents.desktop(webView)
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36", desktop)
        assertFalse("Mobile" in desktop)
        assertFalse("wv" in desktop)
    }

    @Test
    fun onlyTheDesktopAgentCountsAsDesktop() {
        assertTrue(UserAgents.isDesktop(UserAgents.desktop(webView)))
        assertFalse(UserAgents.isDesktop(webView))
        assertFalse(UserAgents.isDesktop(null))
    }
}
