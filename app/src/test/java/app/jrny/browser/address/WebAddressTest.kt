package app.jrny.browser.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAddressTest {

    @Test
    fun `only http and https are web schemes`() {
        assertTrue(WebAddress.isWebScheme("http"))
        assertTrue(WebAddress.isWebScheme("HTTPS"))
        listOf("intent", "market", "mailto", "tel", "javascript", "file", "content", "data", "about", null)
            .forEach { assertFalse("$it is not a web scheme", WebAddress.isWebScheme(it)) }
    }

    @Test
    fun `web addresses are recognised by their scheme, not a prefix`() {
        assertTrue(WebAddress.isWebAddress("https://example.com/"))
        assertFalse(WebAddress.isWebAddress("httpfoo://example.com/"))
        assertFalse(WebAddress.isWebAddress("http-not-a-scheme"))
        assertFalse(WebAddress.isWebAddress("data:text/html,x"))
        assertFalse(WebAddress.isWebAddress(null))
    }

    @Test
    fun `the host drops credentials and port`() {
        assertEquals("en.wikipedia.org", WebAddress.hostOf("https://en.wikipedia.org/wiki/Web_browser"))
        assertEquals("example.com", WebAddress.hostOf("https://user:pw@example.com:8443/x?y#z"))
        assertEquals("[::1]", WebAddress.hostOf("http://[::1]:8080/"))
        assertEquals("", WebAddress.hostOf("about:blank"))
        assertEquals("", WebAddress.hostOf(null))
    }
}
