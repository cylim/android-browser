package app.jrny.browser.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkWrapperListTest {

    private fun wrapper(pattern: String, vararg parameters: String) =
        requireNotNull(LinkWrapper.parse(pattern, parameters.joinToString(", "))) { "$pattern ${parameters.toList()}" }

    private val safeLinks = wrapper("safelinks.protection.outlook.com", "url")
    private val google = wrapper("google.com/url", "q", "url")
    private val googleQ = wrapper("google.com/url", "q")
    private val slack = wrapper("slack-redir.net/link", "url")
    private val defaults = listOf(safeLinks, google, slack)
    private val start = LinkWrapperList(defaults = defaults)

    private val proofpoint = wrapper("urldefense.example", "u")
    private val mimecast = wrapper("protect.example", "url")

    @Test
    fun `a wrapper is a site and the names of the parameters that can carry the address`() {
        assertEquals(LinkWrapper(SitePattern.parse("google.com/url")!!, listOf("q", "url")), LinkWrapper.parse(" https://www.google.com/url?q=x ", " q, url "))
        // Spaces separate names as commas do, and a name given twice is one name.
        assertEquals(listOf("q", "url"), LinkWrapper.parse("google.com/url", "q url,q")?.parameters)
        assertEquals(listOf("redirect_uri"), LinkWrapper.parse("example.com", "redirect_uri")?.parameters)
        listOf(
            "",
            " , ",
            "u=",
            "u=1",
            "u&v",
            "ü",
            "x".repeat(LinkWrapper.MAX_PARAMETER_LENGTH + 1),
            (1..LinkWrapper.MAX_PARAMETERS + 1).joinToString(",") { "p$it" },
        ).forEach { assertNull("parameters '$it'", LinkWrapper.parse("example.com", it)) }
        assertNull(LinkWrapper.parse("not a site", "url"))
    }

    @Test
    fun `a wrapper names its own site, and a star stands only for what comes before that name`() {
        listOf("*.safelinks.protection.outlook.com", "a*.example.com", "example.com/*/out", "*.slack-redir.net")
            .forEach { assertNotNull(it, LinkWrapper.parse(it, "url")) }
        // Each of these would read the parameter on sites that belong to anyone.
        listOf("*", "*.com", "*.co.uk", "google.*", "*example.com", "example.*.com")
            .forEach { assertNull(it, LinkWrapper.parse(it, "url")) }
        // So would a part that a registry hands out, named without a star: it covers every site under it.
        listOf("com", "co.uk", "github.io", "com/url")
            .forEach { assertNull(it, LinkWrapper.parse(it, "url")) }
        listOf("example.com", "user.github.io", "intranet")
            .forEach { assertNotNull(it, LinkWrapper.parse(it, "url")) }
    }

    @Test
    fun `until the person changes it, the list is the default list`() {
        assertTrue(start.isDefault)
        assertEquals(defaults, start.all)
        assertEquals(LinkWrappers.DEFAULT, LinkWrapperList.DEFAULT.all)
    }

    @Test
    fun `an added wrapper comes after the default ones`() {
        val added = start.save(null, proofpoint)
        assertFalse(added.isDefault)
        assertEquals(defaults + proofpoint, added.all)
    }

    @Test
    fun `a changed default keeps its place`() {
        assertEquals(listOf(safeLinks, googleQ, slack), start.save(google, googleQ).all)
    }

    @Test
    fun `a changed wrapper of ones own keeps its place`() {
        val two = start.save(null, proofpoint).save(null, mimecast)
        val changed = wrapper("urldefense.example", "u", "url")
        assertEquals(defaults + changed + mimecast, two.save(proofpoint, changed).all)
        // Moved to another site, it keeps its place too.
        val moved = wrapper("urldefense.example.org", "u")
        assertEquals(defaults + moved + mimecast, two.save(proofpoint, moved).all)
    }

    @Test
    fun `changed back to the default, the list is the default list again`() {
        assertEquals(start, start.save(google, googleQ).save(googleQ, google))
        assertEquals(start, start.delete(slack).save(null, slack))
    }

    @Test
    fun `defaults that a later version adds reach a list of ones own, and what the person changed stays`() {
        val own = start.save(null, proofpoint).delete(slack).save(google, googleQ)
        // The same changes, read by a later version with a longer default list.
        val later = own.copy(defaults = defaults + mimecast)
        assertEquals(listOf(safeLinks, googleQ, mimecast, proofpoint), later.all)
    }

    @Test
    fun `a site has one wrapper`() {
        val twice = start.save(null, proofpoint).save(null, wrapper("urldefense.example", "url"))
        assertEquals(defaults + wrapper("urldefense.example", "url"), twice.all)
        // A wrapper changed to another's site takes that one's place.
        assertEquals(listOf(safeLinks, google, wrapper("urldefense.example", "target")), twice.save(slack, wrapper("urldefense.example", "target")).all)
    }

    @Test
    fun `an emptied list stays empty, and is not the default list`() {
        val emptied = defaults.fold(start) { list, it -> list.delete(it) }
        assertEquals(emptyList<LinkWrapper>(), emptied.all)
        assertFalse(emptied.isDefault)
    }

    @Test
    fun `the default list can be taken back at any time`() {
        assertEquals(start, start.save(null, proofpoint).delete(slack).save(google, googleQ).useDefault())
    }

    @Test
    fun `merging adds only the wrappers for sites that are missing here`() {
        val here = start.delete(slack).save(null, mimecast)
        val incoming = start.save(null, proofpoint).save(google, googleQ)
        // Proofpoint is missing here. Google is here as it is, and Slack stays removed.
        assertEquals(listOf(safeLinks, google, mimecast, proofpoint), here.merge(incoming).all)
        assertEquals(start, start.merge(start))
    }
}
