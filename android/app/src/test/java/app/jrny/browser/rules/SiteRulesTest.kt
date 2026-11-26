package app.jrny.browser.rules

import app.jrny.browser.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteRulesTest {

    private val work = ProfileId("1")
    private val personal = ProfileId("2")
    private val wrappers = LinkWrappers(LinkWrappers.DEFAULT)

    private fun rule(id: Long, pattern: String, profile: ProfileId) = SiteRule(id, requireNotNull(SitePattern.parse(pattern)), profile)

    private fun safeLink(inner: String) =
        "https://nam12.safelinks.protection.outlook.com/?url=" + java.net.URLEncoder.encode(inner, Charsets.UTF_8) + "&data=05"

    @Test
    fun `a wrapped link opens where the address it carries would`() {
        val rules = listOf(rule(1, "github.com", work))
        assertEquals(work, rules.ruleFor(safeLink("https://github.com/jrny/okapi"), wrappers)?.profileId)
    }

    @Test
    fun `the carried address decides before a rule for the wrapper itself`() {
        val rules = listOf(rule(1, "safelinks.protection.outlook.com", work), rule(2, "example.com", personal))
        assertEquals(personal, rules.ruleFor(safeLink("https://example.com/"), wrappers)?.profileId)
    }

    @Test
    fun `without a rule for the carried address, a rule for the wrapper decides`() {
        val rules = listOf(rule(1, "safelinks.protection.outlook.com", work), rule(2, "example.com", personal))
        assertEquals(work, rules.ruleFor(safeLink("https://github.com/"), wrappers)?.profileId)
    }

    @Test
    fun `in a wrapped wrapper, the innermost address decides first`() {
        val rules = listOf(rule(1, "safelinks.protection.outlook.com", work), rule(2, "example.com", personal))
        val wrapped = "https://www.google.com/url?q=" + java.net.URLEncoder.encode(safeLink("https://example.com/"), Charsets.UTF_8)
        assertEquals(personal, rules.ruleFor(wrapped, wrappers)?.profileId)
        val toGithub = "https://www.google.com/url?q=" + java.net.URLEncoder.encode(safeLink("https://github.com/"), Charsets.UTF_8)
        assertEquals(work, rules.ruleFor(toGithub, wrappers)?.profileId)
    }

    @Test
    fun `without wrappers, a wrapped link is judged as it is`() {
        val rules = listOf(rule(1, "github.com", work))
        assertNull(rules.ruleFor(safeLink("https://github.com/"), LinkWrappers(emptyList())))
    }
}
