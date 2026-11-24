package app.jrny.browser

import android.annotation.SuppressLint
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.TestPages.page
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // Waited for here, not on the UI thread: there the wait would block the very thread that
        // has to deliver the Tabs.
        val saved = selected()
        var tab: Tab? = null
        compose.runOnUiThread { tab = browser().tabFor(saved) }
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
        // Pages whose title never changes: a title that changes while the page loads would make
        // the Tab's row, which the test finds by name, come and go.
        val firstPage = page("<title>first page</title>")
        val secondPage = page("<title>second page</title>")
        load(firstPage, "first page")
        val first = selected().id
        val before = tabs().size

        openTabFromTheUi()
        compose.waitUntil(10_000) { tabs().size == before + 1 }
        val added = selected()
        assertNull(added.url)
        // A new Tab opens with the keyboard up for an address. The test types none.
        Espresso.closeSoftKeyboard()
        load(secondPage, "second page")

        compose.activityRule.scenario.recreate()

        compose.waitUntil(10_000) { tabs().size == before + 1 }
        assertEquals(added.id, selected().id)
        assertEquals(firstPage, tabs().single { it.id == first }.url)
        val restored = shownTab()
        compose.waitUntil(15_000) { !restored.isLoading && restored.url == secondPage && restored.title == "second page" }

        // Close it from the UI: the Tab shown before it comes back.
        if (compose.onAllNodesWithTag("tab-count").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("tab-count").performClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Close second page").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Close second page").performClick()
        compose.waitUntil(10_000) { tabs().size == before }
        assertEquals(first, selected().id)
    }
}
