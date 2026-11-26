package app.jrny.browser.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Site Rules against a throwaway database: one rule per pattern, and the narrowest rule decides. */
@RunWith(AndroidJUnit4::class)
class SiteRuleRepositoryTest {

    // The rules alone: what Link Wrappers change is SiteRulesTest's.
    private val noWrappers = LinkWrappers(emptyList())
    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val profiles = ProfileRepository(database.profiles())
    private val rules = SiteRuleRepository(database.siteRules())
    private val personal = ProfileId("1")
    private var work = ProfileId("0")

    @Before
    fun twoProfiles() = runBlocking {
        profiles.ensureFirstProfile("Personal")
        work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
    }

    @After
    fun close() = database.close()

    private fun pattern(text: String) = SitePattern.parse(text)!!
    private suspend fun listed() = rules.rules.first().map { it.pattern.text to it.profileId }

    @Test
    fun rulesAreListedOldestFirstAndSayWhereALinkOpens() = runBlocking {
        assertTrue(rules.save(null, pattern("example.com"), work))
        assertTrue(rules.save(null, pattern("example.org"), personal))
        assertEquals(listOf("example.com" to work, "example.org" to personal), listed())

        assertEquals(work, rules.ruleFor("https://www.example.com/page", noWrappers)?.profileId)
        assertEquals(personal, rules.ruleFor("http://example.org/", noWrappers)?.profileId)
        assertNull(rules.ruleFor("https://example.net/", noWrappers))
        assertNull(rules.ruleFor("intent://example.com/#Intent;end", noWrappers))
    }

    @Test
    fun theNarrowestRuleDecidesAndBetweenEquallyNarrowOnesTheOlder() = runBlocking {
        rules.save(null, pattern("example.com"), personal)
        rules.save(null, pattern("mail.example.com"), work)
        rules.save(null, pattern("example.com/work"), work)
        assertEquals(personal, rules.ruleFor("https://example.com/", noWrappers)?.profileId)
        assertEquals(work, rules.ruleFor("https://mail.example.com/inbox", noWrappers)?.profileId)
        assertEquals(work, rules.ruleFor("https://example.com/work/today", noWrappers)?.profileId)
        assertEquals(personal, rules.ruleFor("https://example.com/workshop", noWrappers)?.profileId)

        // Both cover the link and name as much of it: the one made first decides.
        rules.save(null, pattern("a*.example.net"), personal)
        rules.save(null, pattern("*a.example.net"), work)
        assertEquals("a*.example.net", rules.ruleFor("https://aa.example.net/", noWrappers)?.pattern?.text)
    }

    @Test
    fun aPatternHasOneRule() = runBlocking {
        rules.save(null, pattern("example.com"), personal)
        // Written another way, it is still the same pattern, and the newer rule replaces the older.
        rules.save(null, pattern("https://www.Example.com/"), work)
        assertEquals(listOf("example.com" to work), listed())
    }

    @Test
    fun aRuleCanBeChangedAndDeleted() = runBlocking {
        rules.save(null, pattern("example.com"), personal)
        rules.save(null, pattern("example.org"), work)
        val (first, second) = rules.rules.first()

        rules.save(first, pattern("docs.example.com"), work)
        assertEquals(listOf("docs.example.com" to work, "example.org" to work), listed())
        assertEquals(first.id, rules.rules.first().first().id)

        // Changed to a pattern another rule has, it takes that rule's place.
        rules.save(rules.rules.first().first(), pattern("example.org"), personal)
        assertEquals(listOf("example.org" to personal), listed())

        // A rule deleted while its editor was open is added again when saved.
        rules.save(second, pattern("example.net"), work)
        rules.delete(rules.rules.first().first())
        assertEquals(listOf("example.net" to work), listed())
    }

    @Test
    fun aDeletedProfileTakesItsRulesAlongAndGetsNoNewOnes() = runBlocking {
        rules.save(null, pattern("example.com"), work)
        rules.save(null, pattern("example.org"), personal)
        assertTrue(profiles.delete(work))

        // At once, not only when its row goes at the next cold start.
        assertEquals(listOf("example.org" to personal), listed())
        assertNull(rules.ruleFor("https://example.com/", noWrappers))
        assertFalse(rules.save(null, pattern("example.net"), work))
        assertEquals(listOf("example.org" to personal), listed())
    }
}
