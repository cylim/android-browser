package app.jrny.browser.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkWrappersTest {

    private val defaults = LinkWrappers(LinkWrappers.DEFAULT)

    @Test
    fun `an Outlook Safe Link is read as the address it carries`() {
        assertEquals(
            "https://github.com/jrny/okapi/pull/28",
            defaults.unwrap(
                "https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fgithub.com%2Fjrny%2Fokapi%2Fpull%2F28" +
                    "&data=05%7C02%7C&sdata=abc%3D&reserved=0",
            ),
        )
    }

    @Test
    fun `the default list reads the wrappers of the common mail and chat apps`() {
        val inner = "https://example.com/page"
        val encoded = "https%3A%2F%2Fexample.com%2Fpage"
        listOf(
            "https://safelinks.protection.office365.us/?url=$encoded&data=05",
            "https://www.google.com/url?q=$encoded&sa=D&source=editors",
            "https://www.google.com/url?url=$encoded&sa=t",
            "https://google.com/url?q=$encoded",
            "https://l.facebook.com/l.php?u=$encoded&h=AT0",
            "https://lm.facebook.com/l.php?u=$encoded&h=AT0",
            "https://l.messenger.com/l.php?u=$encoded&h=AT0",
            "https://l.instagram.com/?u=$encoded&e=AT0",
            "https://www.youtube.com/redirect?event=video_description&q=$encoded",
            "https://slack-redir.net/link?url=$encoded",
            "https://www.linkedin.com/redir/redirect?url=$encoded&urlhash=abc",
        ).forEach { assertEquals(it, inner, defaults.unwrap(it)) }
    }

    @Test
    fun `a wrapper's own pages are not wrapped links`() {
        listOf(
            "https://www.google.com/search?q=https%3A%2F%2Fexample.com%2F",
            "https://www.youtube.com/results?q=https%3A%2F%2Fexample.com%2F",
            "https://www.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F",
        ).forEach { assertNull(it, defaults.unwrap(it)) }
    }

    @Test
    fun `a wrapped wrapper is read to the address inside both`() {
        val safeLink = "https://eur01.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fpage&data=05"
        assertEquals(
            "https://example.com/page",
            defaults.unwrap("https://www.google.com/url?q=" + java.net.URLEncoder.encode(safeLink, Charsets.UTF_8)),
        )
    }

    @Test
    fun `a site that only looks like a wrapper is not one`() {
        listOf(
            "https://safelinks.protection.outlook.com.evil.test/?url=https%3A%2F%2Fgithub.com%2F",
            "https://evil.test/safelinks.protection.outlook.com/?url=https%3A%2F%2Fgithub.com%2F",
            "https://evil.test/?next=https://safelinks.protection.outlook.com/&url=https%3A%2F%2Fgithub.com%2F",
            "https://evil.test\\@safelinks.protection.outlook.com/?url=https%3A%2F%2Fgithub.com%2F",
        ).forEach { assertNull(it, defaults.unwrap(it)) }
    }

    @Test
    fun `only a web address is taken from a wrapper`() {
        listOf(
            "https://eur01.safelinks.protection.outlook.com/?url=javascript%3Aalert(1)",
            "https://eur01.safelinks.protection.outlook.com/?url=intent%3A%2F%2Fgithub.com%2F%23Intent%3Bend",
            "https://eur01.safelinks.protection.outlook.com/?url=github.com%2Fjrny",
            "https://eur01.safelinks.protection.outlook.com/?url=",
            "https://eur01.safelinks.protection.outlook.com/?url=https%3A%2F%2F",
            "https://eur01.safelinks.protection.outlook.com/?url=https%3A%2F%2Fgithub.com%2F%E0%A4%A",
            "https://eur01.safelinks.protection.outlook.com/?data=05",
        ).forEach { assertNull(it, defaults.unwrap(it)) }
    }

    @Test
    fun `a wrapper that names two addresses is not read, since either could be the one it opens`() {
        assertNull(
            defaults.unwrap(
                "https://eur01.safelinks.protection.outlook.com/?url=https%3A%2F%2Fgithub.com%2F&url=https%3A%2F%2Fevil.test%2F",
            ),
        )
    }

    @Test
    fun `a wrapper whose parameters name different addresses is not read`() {
        assertNull(defaults.unwrap("https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2F&url=https%3A%2F%2Fevil.test%2F"))
        // A parameter given twice spoils the others too: the site may read either of its two.
        assertNull(
            defaults.unwrap(
                "https://www.google.com/url?q=https%3A%2F%2Fevil.test%2F&q=https%3A%2F%2Fgithub.com%2F&url=https%3A%2F%2Fgithub.com%2F",
            ),
        )
        // So does one that isn't a web address, which the site may still read first.
        assertNull(defaults.unwrap("https://www.google.com/url?q=javascript%3Aalert(1)&url=https%3A%2F%2Fgithub.com%2F"))
        // The same address in both is still one address.
        assertEquals(
            "https://github.com/",
            defaults.unwrap("https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2F&url=https%3A%2F%2Fgithub.com%2F"),
        )
    }

    @Test
    fun `the default list has one wrapper per site`() {
        assertEquals(LinkWrappers.DEFAULT.size, LinkWrappers.DEFAULT.distinctBy { it.pattern }.size)
    }

    @Test
    fun `only the query is read, never the fragment`() {
        // The fragment never reaches the wrapper's site, so it can't say where the link goes.
        assertNull(defaults.unwrap("https://www.google.com/url#?q=https%3A%2F%2Fgithub.com%2F"))
        assertNull(defaults.unwrap("https://www.google.com/url?sa=D#&q=https%3A%2F%2Fgithub.com%2F"))
        assertEquals("https://github.com/", defaults.unwrap("https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2F#top"))
    }

    @Test
    fun `an escape is a percent sign and exactly two hexadecimal digits`() {
        listOf("%+A", "%-1", "%2", "%G0", "% 2").forEach { escape ->
            assertNull(escape, defaults.unwrap("https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2F$escape"))
        }
        assertEquals("https://github.com/a%", LinkWrappers.percentDecode("https%3A%2F%2Fgithub.com%2Fa%25"))
    }

    @Test
    fun `the carried address is decoded only where it was percent-encoded`() {
        assertEquals(
            "https://example.com/search?q=a+b&lang=en",
            defaults.unwrap("https://eur01.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fsearch%3Fq%3Da+b%26lang%3Den"),
        )
    }
}
