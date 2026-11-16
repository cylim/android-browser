package app.jrny.browser

import android.annotation.SuppressLint
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.titled
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app and database, through the real UI in whichever layout the device shows: open a
 * Tab, have both Tabs come back after the activity is recreated, and close one.
 */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class TabsEndToEndTest {

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

    private fun tabs(): List<SavedTab> {
        compose.waitUntil(10_000) { browser().active.value != null }
        return browser().active.value!!.tabs
    }

    private fun selected(): SavedTab = tabs().single { it.selected }

    /** The selected Tab, live. */
    private fun shownTab(): Tab {
        var tab: Tab? = null
        compose.runOnUiThread { tab = browser().tabFor(selected()) }
        return tab!!
    }

    private fun load(url: String, title: String) {
        val tab = shownTab()
        compose.runOnUiThread { tab.load(url) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == title }
        compose.waitUntil(10_000) { selected().url == url && selected().title == title }
    }

    /** Opens a Tab from the UI: the Sidebar's button, or the Tab Overview's. */
    private fun openTabFromTheUi() {
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("tab-count").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("tab-count").performClick()
        }
        compose.onNodeWithTag("new-tab").performClick()
    }

    @Test
    fun aNewTabComesBackAfterRecreationAndCanBeClosed() {
        load(titled("first"), "first navigate")
        val first = selected().id
        val before = tabs().size

        openTabFromTheUi()
        compose.waitUntil(10_000) { tabs().size == before + 1 }
        val added = selected()
        assertNull(added.url)
        // A new Tab opens with the keyboard up for an address. The test types none.
        Espresso.closeSoftKeyboard()
        load(titled("second"), "second navigate")

        compose.activityRule.scenario.recreate()

        compose.waitUntil(10_000) { tabs().size == before + 1 }
        assertEquals(added.id, selected().id)
        assertEquals(titled("first"), tabs().single { it.id == first }.url)
        val restored = shownTab()
        compose.waitUntil(15_000) { !restored.isLoading && restored.url == titled("second") && restored.title == "second navigate" }

        // Close it from the UI: the Tab shown before it comes back.
        if (compose.onAllNodesWithTag("tab-count").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("tab-count").performClick()
        }
        compose.onNodeWithContentDescription("Close second navigate").performClick()
        compose.waitUntil(10_000) { tabs().size == before }
        assertEquals(first, selected().id)
    }
}
