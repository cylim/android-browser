package app.jrny.browser

import android.annotation.SuppressLint
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app, database and WebView profiles, through the real UI in whichever layout the device
 * shows (Bottom Bar on a phone, Sidebar on a landscape tablet): create a Profile, switch between
 * Profiles, check each keeps its own page and cookies, and delete it.
 */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class ProfilesEndToEndTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireMultiProfile() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    private fun browser(): BrowserState {
        var browser: BrowserState? = null
        compose.activityRule.scenario.onActivity { browser = it.browser }
        return browser!!
    }

    private fun active(): Profile = browser().activeProfile.value!!

    private fun tabOf(profile: Profile): Tab {
        var tab: Tab? = null
        compose.runOnUiThread { tab = browser().tabFor(profile) }
        return tab!!
    }

    private fun load(tab: Tab, url: String, title: (String) -> Boolean) {
        compose.runOnUiThread { tab.load(url) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.url == url && title(tab.title) }
    }

    /** Opens the Profile list: the Sidebar's switcher, or the Bottom Bar's avatar. */
    private fun openProfileList() {
        compose.waitForIdle()
        val switcher = compose.onAllNodesWithTag("profile-switcher").fetchSemanticsNodes()
        if (switcher.isNotEmpty()) {
            compose.onNodeWithTag("profile-switcher").performClick()
        } else {
            compose.onNodeWithTag("profile-avatar").performClick()
        }
        compose.onNodeWithTag("profile-list").assertExists()
    }

    @Test
    fun profilesKeepTheirOwnPageAndCookiesAndCanBeDeleted() {
        compose.waitUntil(10_000) { browser().activeProfile.value != null }
        val first = active()
        val firstTab = tabOf(first)
        load(firstTab, titled("first"), { it == "first navigate" })
        // The page focuses nothing, so the keyboard stays down while the test drives the UI.
        Espresso.closeSoftKeyboard()

        val nonce = System.nanoTime().toString()
        TestServer(mapOf("/set" to "set-cookie:jrny_e2e=$nonce", "/echo" to "echo-cookies")).use { server ->
            // Create a Profile through the UI. A new Profile becomes the Active Profile.
            val name = "E2E $nonce".take(40)
            openProfileList()
            compose.onNodeWithTag("new-profile").performClick()
            compose.onNodeWithTag("profile-name-field").performTextReplacement(name)
            compose.onNodeWithTag("color-Purple").performScrollTo().performClick()
            compose.onNodeWithTag("save-profile").performClick()
            compose.waitUntil(10_000) { active().name == name }
            val created = active()
            val createdTab = tabOf(created)
            assertNotEquals(first.id, created.id)

            // A cookie set in the new Profile...
            load(createdTab, server.url("/set")) { it == "cookie set" }
            load(createdTab, server.url("/echo")) { it.startsWith("cookies:") }
            assertTrue(createdTab.title, createdTab.title.contains("jrny_e2e=$nonce"))

            // ...is invisible in the first Profile, which kept its own page and Tab.
            Espresso.closeSoftKeyboard()
            openProfileList()
            compose.onNodeWithTag("profile-row-${first.id.value}").performClick()
            compose.waitUntil(10_000) { active().id == first.id }
            assertSame(firstTab, tabOf(first))
            assertEquals("first navigate", firstTab.title)
            load(firstTab, server.url("/echo")) { it.startsWith("cookies:") }
            assertFalse(firstTab.title, firstTab.title.contains("jrny_e2e=$nonce"))

            // Delete the new Profile: it disappears, and its data is erased at the next cold start.
            openProfileList()
            compose.onNodeWithContentDescription("Edit $name").performClick()
            compose.onNodeWithTag("delete-profile").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-delete").performClick()
            compose.waitUntil(10_000) { browser().profiles.value.none { it.id == created.id } }
            assertEquals(first.id, active().id)
            // The pending count is a separate query, so it can arrive after the list update.
            compose.waitUntil(10_000) { browser().pendingDeletionCount.value >= 1 }
            openProfileList()
            compose.onNodeWithTag("pending-removal").assertExists()
            compose.onNodeWithTag("profile-row-${created.id.value}").assertDoesNotExist()
        }
    }
}
