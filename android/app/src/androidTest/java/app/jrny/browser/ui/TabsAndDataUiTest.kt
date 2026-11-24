package app.jrny.browser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.BrowsingData
import app.jrny.browser.library.Visit
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.tab.ClosedTab
import app.jrny.browser.tab.TabId
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Recently closed" in the history, and Clear browsing data (SPEC, "Reopen closed Tab", "Clear browsing data"). */
@RunWith(AndroidJUnit4::class)
class TabsAndDataUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val closed = ClosedTab(1, ProfileId("1"), TabId(5), "https://example.com/closed", "Closed page")
    private val reopened = mutableListOf<ClosedTab>()
    private val cleared = mutableListOf<Set<BrowsingData>>()

    @Test
    fun recentlyClosedSitsAboveTheHistoryAndReopens() {
        compose.setContent {
            JrnyTheme {
                LibraryPanel(
                    panel = Panel.History,
                    library = LibraryUi(
                        visits = listOf(Visit(1, "https://example.com/seen", "Seen page", 5)),
                        recentlyClosed = listOf(closed),
                        reopenClosed = { reopened += it },
                    ),
                    profileName = "Personal",
                    onOpen = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("Recently closed").assertExists()
        // Both sections have a heading; the panel's own title says "History" too.
        assertEquals(2, compose.onAllNodesWithTag("history-heading").fetchSemanticsNodes().size)
        compose.onNodeWithText("Seen page").assertExists()
        compose.onNodeWithText("Closed page").performClick()
        assertEquals(listOf(closed), reopened)
    }

    @Test
    fun clearBrowsingDataAsksWhatAndWarnsAboutSignOutsAndTheSharedCache() {
        compose.setContent {
            JrnyTheme {
                SettingsPanel(SettingsUi(clearBrowsingData = { cleared += it }), onClose = {}, profileName = "Work")
            }
        }
        compose.onNodeWithTag("setting-clear-data").performScrollTo().performClick()
        compose.onNodeWithText("Clear browsing data of Work?").assertExists()
        // Cached files are checked at first, and the engine clears them for every Profile.
        compose.onNodeWithText("Cached images and files are cleared in every Profile.").assertExists()
        compose.onNodeWithTag("clear-cookies-warning").assertDoesNotExist()
        compose.onNodeWithTag("clear-Cookies").performClick()
        compose.onNodeWithTag("clear-cookies-warning").assertExists()
        compose.onNodeWithTag("confirm-clear-data").performClick()
        assertEquals(listOf(setOf(BrowsingData.History, BrowsingData.Cache, BrowsingData.Cookies)), cleared)
    }
}
