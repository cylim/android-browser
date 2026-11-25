package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.LinkOffer
import app.jrny.browser.TestServer
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.rules.SitePattern
import app.jrny.browser.rules.SiteRule
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Site Rules panel and its editor, and what the window says about a link a rule sent
 * somewhere. The rules here are only a list: what a rule does to a link is BrowserStateTest's.
 */
@RunWith(AndroidJUnit4::class)
class SiteRulesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val server = TestServer(mapOf("/start" to "<title>start</title>"))
    private lateinit var tab: Tab
    private var rules by mutableStateOf(emptyList<SiteRule>())
    private var linkOffer by mutableStateOf<LinkOffer?>(null)
    private val saved = mutableListOf<Triple<SiteRule?, String, Profile>>()
    private val deleted = mutableListOf<SiteRule>()
    private var accepted = 0

    private fun rule(id: Long, pattern: String, profile: Profile) = SiteRule(id, SitePattern.parse(pattern)!!, profile.id)

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-site-rules-ui")))
            // With a page, so the address field doesn't take focus and open the keyboard by itself.
            tab.load(server.url("/start"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "start" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
        server.close()
    }

    private fun show(size: DpSize, all: List<Profile> = listOf(TestProfiles.personal, TestProfiles.work)) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TabsUi(
                        all = listOf(TestTabs.one),
                        selected = TestTabs.one,
                        select = {},
                        close = {},
                        open = {},
                        linkOffer = linkOffer,
                        dismissIncomingLink = { linkOffer = null },
                        acceptRuleOffer = { accepted++ },
                    ),
                    TestProfiles.ui(all = all),
                    siteRules = SiteRulesUi(
                        rules,
                        save = { existing, pattern, profile -> saved += Triple(existing, pattern.text, profile) },
                        delete = { deleted += it },
                    ),
                )
            }
        }
    }

    private fun openSiteRules() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-settings").performClick()
        compose.onNodeWithTag("setting-site-rules").performScrollTo().performClick()
        compose.onNodeWithTag("panel-SiteRules").assertExists()
    }

    private fun explanation() = compose.onNodeWithTag("rule-site-explanation", useUnmergedTree = true)

    @Test
    fun theSettingsLeadToTheSiteRulesAndBackLeadsToTheSettingsAgain() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-settings").performClick()
        compose.onNodeWithTag("setting-site-rules").performScrollTo().assertTextContains("None yet")
        compose.onNodeWithTag("setting-site-rules").performClick()

        compose.onNodeWithTag("panel-SiteRules").assertExists()
        compose.onNodeWithTag("panel-Settings").assertDoesNotExist()
        compose.onNodeWithText("No Site Rules yet").assertExists()
        // Nothing to check a link against yet.
        compose.onNodeWithTag("check-link").assertDoesNotExist()

        Espresso.pressBack()
        compose.onNodeWithTag("panel-Settings").assertExists()
        compose.onNodeWithTag("setting-site-rules").performScrollTo().performClick()
        compose.onNodeWithTag("panel-back").performClick()
        compose.onNodeWithTag("panel-Settings").assertExists()

        // With rules, the settings say how many.
        rules = listOf(rule(1, "example.com", TestProfiles.work), rule(2, "example.org", TestProfiles.personal))
        compose.onNodeWithTag("setting-site-rules").performScrollTo().assertTextContains("2 rules")
        compose.onNodeWithTag("setting-site-rules").performClick()
        compose.onNodeWithTag("close-panel").performClick()
        compose.onNodeWithTag("panel-SiteRules").assertDoesNotExist()
        compose.onNodeWithTag("panel-Settings").assertDoesNotExist()
    }

    @Test
    fun aRuleIsAddedForASiteAndAProfile() {
        show(PHONE_PORTRAIT)
        openSiteRules()
        compose.onNodeWithTag("add-first-site-rule").performClick()

        compose.onNodeWithTag("site-rule-editor").assertExists()
        compose.onNodeWithText("New Site Rule").assertExists()
        explanation().assertTextContains("A site, such as example.com.", substring = true)
        compose.onNodeWithTag("save-site-rule").assertIsNotEnabled()
        // The Active Profile is the one chosen to begin with.
        compose.onNodeWithTag("rule-profile-${TestProfiles.personal.id.value}").assertIsSelected()

        compose.onNodeWithTag("rule-site-field").performTextReplacement("not a site!")
        explanation().assertTextEquals("That isn't a site. Try something like example.com.")
        compose.onNodeWithTag("save-site-rule").assertIsNotEnabled()

        // A whole address is read as its site.
        compose.onNodeWithTag("rule-site-field").performTextReplacement("https://www.Example.com/")
        explanation().assertTextEquals("Covers example.com and every site under it.")
        compose.onNodeWithTag("rule-profile-${TestProfiles.work.id.value}").performScrollTo().performClick()
        compose.onNodeWithTag("save-site-rule").assertIsEnabled().performClick()

        compose.onNodeWithTag("site-rule-editor").assertDoesNotExist()
        assertEquals(listOf(Triple(null, "example.com", TestProfiles.work)), saved)
    }

    @Test
    fun theEditorSaysWhatAPatternCoversAndWhichRuleItReplaces() {
        rules = listOf(rule(1, "example.org", TestProfiles.work))
        show(PHONE_PORTRAIT)
        openSiteRules()
        compose.onNodeWithTag("add-site-rule").performClick()

        compose.onNodeWithTag("rule-site-field").performTextReplacement("example.com/work")
        explanation().assertTextEquals("Covers example.com/work and everything under it.")
        compose.onNodeWithTag("rule-site-field").performTextReplacement("*.example.com")
        explanation().assertTextEquals("Covers every address that fits *.example.com, where * stands for anything.")
        compose.onNodeWithTag("rule-site-field").performTextReplacement("www.example.org")
        explanation().assertTextEquals("Replaces your rule that opens example.org in Work.")
        compose.onNodeWithTag("save-site-rule").assertIsEnabled()

        // A pasted address that is too long is refused whole. Cut short, its rule would cover
        // more than the address names.
        val long = "https://example.com/" + "a".repeat(190) + "/private"
        compose.onNodeWithTag("rule-site-field").performTextReplacement(long)
        compose.onNodeWithTag("rule-site-field").assertTextContains(long)
        explanation().assertTextEquals("That is too long for a rule. A site and its path can take up to 200 characters.")
        compose.onNodeWithTag("save-site-rule").assertIsNotEnabled()

        // The same at any length: dot steps that would shrink to nothing if the end were cut off.
        val longer = "https://example.com/" + "./".repeat(1_200) + "private"
        compose.onNodeWithTag("rule-site-field").performTextReplacement(longer)
        compose.onNodeWithTag("rule-site-field").assertTextContains(longer)
        explanation().assertTextEquals("That is too long for a rule. A site and its path can take up to 200 characters.")
        compose.onNodeWithTag("save-site-rule").assertIsNotEnabled()
    }

    @Test
    fun theEditorTakesOverTheSiteOfThePageInTheShownTab() {
        show(PHONE_PORTRAIT)
        openSiteRules()
        compose.onNodeWithTag("add-first-site-rule").performClick()
        val site = SitePattern.forSiteOf(server.url("/"))!!.text

        compose.onNodeWithTag("rule-use-page-site").assertTextEquals("Use $site").performClick()
        explanation().assertTextEquals("Covers $site and every site under it.")
        // Once the field says that site, there is nothing left to offer.
        compose.onNodeWithTag("rule-use-page-site").assertDoesNotExist()
        compose.onNodeWithTag("save-site-rule").performClick()
        assertEquals(listOf(Triple(null, site, TestProfiles.personal)), saved)
    }

    @Test
    fun eachRuleShowsItsProfileAndCanBeChangedOrDeleted() {
        val first = rule(1, "example.com", TestProfiles.work)
        val second = rule(2, "*.example.org/docs", TestProfiles.personal)
        // A rule whose Profile is gone isn't listed.
        rules = listOf(first, second, SiteRule(3, SitePattern.parse("example.net")!!, ProfileId("gone")))
        show(TABLET_LANDSCAPE)
        openSiteRules()

        compose.onAllNodesWithTag("site-rule").assertCountEquals(2)
        compose.onAllNodesWithTag("site-rule").onFirst().assertTextContains("example.com").assertTextContains("Opens in Work")
        compose.onNodeWithText("*.example.org/docs").assertTextContains("Opens in Personal")

        // A tap opens the rule in the editor, as it is.
        compose.onAllNodesWithTag("site-rule").onFirst().performClick()
        compose.onNodeWithText("Edit Site Rule").assertExists()
        compose.onNodeWithTag("rule-site-field").assertTextContains("example.com")
        compose.onNodeWithTag("rule-profile-${TestProfiles.work.id.value}").assertIsSelected()
        compose.onNodeWithTag("rule-profile-${TestProfiles.personal.id.value}").performClick()
        compose.onNodeWithTag("save-site-rule").performClick()
        assertEquals(listOf(Triple(first, "example.com", TestProfiles.personal)), saved)

        compose.onNodeWithContentDescription("Delete the rule for *.example.org/docs").performClick()
        assertEquals(listOf(second), deleted)
    }

    @Test
    fun checkingALinkSaysWhereItOpens() {
        rules = listOf(rule(1, "example.com", TestProfiles.work), rule(2, "docs.example.com", TestProfiles.personal))
        show(PHONE_PORTRAIT)
        openSiteRules()
        fun answer() = compose.onNodeWithTag("check-link-answer", useUnmergedTree = true)
        answer().assertDoesNotExist()

        // Without "https://" in front, it is read as if it had it.
        compose.onNodeWithTag("check-link").performTextReplacement("example.com/pulls")
        answer().assertTextEquals("Opens in Work, by the rule example.com")
        compose.onNodeWithTag("check-link").performTextReplacement("https://docs.example.com/guide?page=2")
        answer().assertTextEquals("Opens in Personal, by the rule docs.example.com")
        compose.onNodeWithTag("check-link").performTextReplacement("https://example.net/")
        answer().assertTextEquals("No rule covers this link. It opens in the Active Profile, now Personal.")
        compose.onNodeWithTag("check-link").performTextReplacement("mailto:someone@example.com")
        answer().assertTextEquals("That isn't a web link.")

        compose.onNodeWithContentDescription("Clear").performClick()
        answer().assertDoesNotExist()
    }

    @Test
    fun withOneProfileThePanelSaysWhenRulesStartToMatter() {
        show(PHONE_PORTRAIT, all = listOf(TestProfiles.personal))
        openSiteRules()
        compose.onNodeWithTag("site-rules-one-profile").assertExists()
        compose.onNodeWithTag("add-first-site-rule").assertExists()
    }

    @Test
    fun aRulesAvatarStartsOnTheTextLineOnAPhoneAndATablet() {
        rules = listOf(rule(1, "example.com", TestProfiles.work))
        for (size in listOf(PHONE_PORTRAIT, TABLET_LANDSCAPE).filter(::fitsTheScreen).take(1)) {
            show(size)
            openSiteRules()
            val field = compose.onNodeWithTag("check-link").getUnclippedBoundsInRoot()
            val avatar = compose
                .onNode(hasTestTag("profile-avatar") and hasAnyAncestor(hasTestTag("site-rule")), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val row = compose.onNodeWithTag("site-rule").getUnclippedBoundsInRoot()
            val delete = compose.onNodeWithContentDescription("Delete the rule for example.com").getUnclippedBoundsInRoot()
            // The avatar's 32dp circle sits 8dp inside its 48dp slot: it starts where the field does.
            assertEquals(field.left.value, avatar.left.value + 8f, 0.5f)
            assertEquals(48f, (avatar.right - avatar.left).value, 0.5f)
            assertEquals(64f, (row.bottom - row.top).value, 0.5f)
            // The delete icon ends where the field does: the 24dp icon sits 8dp inside its 40dp button.
            assertEquals(field.right.value, delete.right.value - 8f, 0.5f)
        }
    }

    @Test
    fun aLinkARuleSentSaysSoAndStillOffersAMove() {
        linkOffer = LinkOffer.Opened(TestTabs.one.id, rule(1, "example.com", TestProfiles.personal))
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("incoming-link-prompt").assertExists()
        compose.onNodeWithText("Opened in Personal by a rule").assertExists()
        compose.onNodeWithTag("incoming-link-action").performClick()
        compose.onNodeWithTag("profile-picker").assertExists()
    }

    @Test
    fun aMovedLinkOffersARuleAndOnceMadeLeadsToTheSiteRules() {
        linkOffer = LinkOffer.Always(TestTabs.one.id, SitePattern.parse("docs.example.com")!!, TestProfiles.work)
        show(PHONE_PORTRAIT)
        // The whole question is there, on a second line if need be.
        compose.onNodeWithText("Always open docs.example.com links in Work?").assertExists()
        compose.onNodeWithTag("site-rule-offer-action").assertTextEquals("Always").performClick()
        assertEquals(1, accepted)

        linkOffer = LinkOffer.RuleSaved(TestTabs.one.id, SitePattern.parse("docs.example.com")!!, TestProfiles.work)
        compose.onNodeWithTag("site-rule-offer-prompt").assertDoesNotExist()
        compose.onNodeWithText("docs.example.com links now open in Work").assertExists()
        compose.onNodeWithTag("site-rule-saved-action").performClick()
        compose.onNodeWithTag("panel-SiteRules").assertExists()
        assertEquals(null, linkOffer)

        // Dismissing the offer ends it.
        compose.onNodeWithTag("close-panel").performClick()
        linkOffer = LinkOffer.Always(TestTabs.one.id, SitePattern.parse("docs.example.com")!!, TestProfiles.work)
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.onNodeWithTag("site-rule-offer-prompt").assertDoesNotExist()
        assertEquals(1, accepted)
    }

    private fun fitsTheScreen(size: DpSize): Boolean {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        return size.width.value <= metrics.widthPixels / metrics.density && size.height.value <= metrics.heightPixels / metrics.density
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
