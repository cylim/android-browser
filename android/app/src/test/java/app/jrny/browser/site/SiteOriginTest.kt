package app.jrny.browser.site

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteOriginTest {

    @Test
    fun anAddressGivesItsSchemeHostAndPort() {
        assertEquals("https://meet.google.com", SiteOrigin.of("https://meet.google.com/abc-defg-hij?pli=1#x")?.text)
        assertEquals("http://10.0.2.15:8080", SiteOrigin.of("http://10.0.2.15:8080/page")?.text)
    }

    @Test
    fun aDefaultPortAndCaseDontMakeAnotherSite() {
        assertEquals("https://example.com", SiteOrigin.of("HTTPS://Example.COM:443/")?.text)
        assertEquals("http://example.com", SiteOrigin.of("http://example.com:80")?.text)
        assertEquals("https://example.com:8443", SiteOrigin.of("https://example.com:8443")?.text)
    }

    @Test
    fun anOriginAsWebViewReportsItIsTheSameSite() {
        assertEquals(SiteOrigin.of("https://example.com/page"), SiteOrigin.of("https://example.com/"))
    }

    @Test
    fun theHostIsShownWithoutSchemeOrPort() {
        assertEquals("example.com", SiteOrigin.of("https://example.com:8443/x")?.host)
    }

    @Test
    fun onlyWebAddressesWithAHostAreSites() {
        assertNull(SiteOrigin.of("data:text/html,hi"))
        assertNull(SiteOrigin.of("file:///sdcard/a.html"))
        assertNull(SiteOrigin.of("about:blank"))
        assertNull(SiteOrigin.of("https://"))
        assertNull(SiteOrigin.of("not an address"))
        assertNull(SiteOrigin.of(null))
    }
}
