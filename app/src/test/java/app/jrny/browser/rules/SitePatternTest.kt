package app.jrny.browser.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePatternTest {

    private fun pattern(text: String) = requireNotNull(SitePattern.parse(text)) { "$text is not a pattern" }

    private fun assertCovers(pattern: String, vararg urls: String) =
        urls.forEach { assertTrue("$pattern should cover $it", pattern(pattern).matches(it)) }

    private fun assertLeavesOut(pattern: String, vararg urls: String) =
        urls.forEach { assertFalse("$pattern should not cover $it", pattern(pattern).matches(it)) }

    @Test
    fun `a site covers itself and every site under it`() {
        assertCovers(
            "example.com",
            "https://example.com",
            "https://example.com/",
            "http://example.com/any/path?q=1#top",
            "https://www.example.com/",
            "https://mail.example.com/inbox",
            "https://a.b.example.com/",
            "https://EXAMPLE.com/",
            "https://example.com:8443/",
            "https://user:secret@example.com/",
            "https://example.com./",
        )
    }

    @Test
    fun `a site does not cover another site that only ends or starts the same`() {
        assertLeavesOut(
            "example.com",
            "https://notexample.com/",
            "https://example.com.evil.test/",
            "https://example.org/",
            "https://evil.test/example.com",
            "https://evil.test/?next=https://example.com/",
            "https://example.com@evil.test/",
        )
    }

    @Test
    fun `a link is judged by the site a browser would load it from`() {
        // A browser reads "\" as "/", so these load from evil.test.
        assertLeavesOut(
            "example.com",
            "https://evil.test\\@example.com/",
            "https://evil.test\\.example.com/",
            "https://evil.test\\example.com",
            "https://evil.test#@example.com/",
            "https://evil.test?@example.com/",
            "https:///evil.test/@example.com",
        )
        assertCovers("evil.test", "https://evil.test\\@example.com/")
        // And it drops tabs and line breaks.
        assertCovers("example.com", "https://exam\tple.com/", "https://example.com\n/")
        assertLeavesOut("example.com", "https://example.com\n.evil.test/")
        assertCovers("example.com/a/b", "https://example.com\\a\\b")
    }

    @Test
    fun `only web addresses are covered`() {
        assertLeavesOut("example.com", "ftp://example.com/", "intent://example.com/#Intent;end", "example.com", "about:blank", "")
    }

    @Test
    fun `a path covers itself and everything under it`() {
        assertCovers(
            "example.com/work",
            "https://example.com/work",
            "https://example.com/work/",
            "https://example.com/work/today?x=1",
            "https://www.example.com/work#top",
        )
        assertLeavesOut(
            "example.com/work",
            "https://example.com/",
            "https://example.com/workshop",
            "https://example.com/Work",
            "https://example.com/home/work",
        )
        assertCovers("example.com/work/", "https://example.com/work/", "https://example.com/work/today")
        assertLeavesOut("example.com/work/", "https://example.com/work", "https://example.com/workshop")
    }

    @Test
    fun `a star stands for any run of characters`() {
        assertCovers("*.example.com", "https://mail.example.com/", "https://a.b.example.com/x")
        assertLeavesOut("*.example.com", "https://example.com/", "https://notexample.com/")

        assertCovers("mail.*", "https://mail.example.com/", "https://mail.proton.me/u/0")
        assertLeavesOut("mail.*", "https://webmail.example.com/", "https://example.com/mail.")

        assertCovers("example.com/*/issues", "https://example.com/a/issues", "https://example.com/a/b/issues/12")
        assertLeavesOut("example.com/*/issues", "https://example.com/issues", "https://example.com/a/pulls")

        assertCovers("example.com/docs*", "https://example.com/docs", "https://example.com/docs-old/x")
    }

    @Test
    fun `a whole address is read as its site and path`() {
        assertEquals("example.com", pattern("https://www.example.com/").text)
        assertEquals("example.com", pattern("  Example.COM  ").text)
        assertEquals("example.com", pattern("example.com/*").text)
        assertEquals("example.com/a/b", pattern("http://user:pw@example.com:8080/a/b?q=1#frag").text)
        assertEquals("mail.example.com", pattern("mail.example.com").text)
        assertEquals("*.example.com/x*", pattern("*.example.com/x**").text)
        // A name in its own script is kept the way links spell it.
        assertEquals("xn--mnchen-3ya.de", pattern("münchen.de").text)
        assertTrue(pattern("münchen.de").matches("https://xn--mnchen-3ya.de/"))
    }

    @Test
    fun `what names no site is not a pattern`() {
        listOf(
            "", "   ", "*", "*.*", "/path", "https://", "ftp://example.com", "mailto:someone@example.com",
            "two words.com", "exa mple.com", "someone@example.com", "example.com:port", "example..com", ".example.com", "[::1]", "exam\"ple.com",
            "a".repeat(SitePattern.MAX_LENGTH + 1),
        ).forEach { assertNull("\"$it\" should not be a pattern", SitePattern.parse(it)) }
    }

    @Test
    fun `the pattern that names more of the site is the narrower one`() {
        fun weight(text: String) = pattern(text).weight
        assertTrue(weight("mail.example.com") > weight("example.com"))
        assertTrue(weight("example.com/work") > weight("example.com"))
        assertTrue(weight("mail.example.com") > weight("example.com/a/long/path"))
        assertTrue(weight("example.com") > weight("*.com"))
        assertEquals(weight("*.example.com"), weight("**.example.com"))
    }

    @Test
    fun `patterns with the same text are the same`() {
        assertEquals(pattern("Example.com/"), pattern("https://www.example.com"))
        assertEquals(pattern("example.com").hashCode(), pattern("EXAMPLE.COM").hashCode())
    }

    @Test
    fun `a long pattern of stars is still matched quickly`() {
        val pattern = pattern("example.com/" + "*a".repeat(60))
        val started = System.nanoTime()
        assertFalse(pattern.matches("https://example.com/" + "a".repeat(5_000) + "b"))
        assertTrue("took too long", System.nanoTime() - started < 2_000_000_000L)
    }

    @Test
    fun `what is typed to check a link is read as a web address or not at all`() {
        assertEquals("https://example.com/a?b", SitePattern.linkFrom(" https://example.com/a?b "))
        assertEquals("http://example.com", SitePattern.linkFrom("http://example.com"))
        assertEquals("https://example.com/pulls", SitePattern.linkFrom("example.com/pulls"))
        assertEquals("https://example.com:8080/x", SitePattern.linkFrom("example.com:8080/x"))
        listOf("", "mailto:someone@example.com", "someone@example.com", "ftp://example.com/", "https://", "two words", "*.example.com")
            .forEach { assertNull("\"$it\" is not a link", SitePattern.linkFrom(it)) }
    }

    @Test
    fun `the site of an address makes a pattern`() {
        assertEquals("docs.example.com", SitePattern.forSiteOf("https://docs.example.com/a?b")?.text)
        assertEquals("example.com", SitePattern.forSiteOf("https://www.example.com/")?.text)
        assertNull(SitePattern.forSiteOf("about:blank"))
        assertNull(SitePattern.forSiteOf(null))
    }
}
