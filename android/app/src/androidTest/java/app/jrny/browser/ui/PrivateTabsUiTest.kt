package app.jrny.browser.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How Private Tabs show: the lock marks, what the Tab list reveals, and the window that can't be captured. */
@RunWith(AndroidJUnit4::class)
class PrivateTabsUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var tab: Tab
    private val openedPrivate = mutableListOf<Unit>()
    private val selectedIds = mutableListOf<TabId>()

    private val news = TestTabs.tab(1, "News", "https://news.example.com/")
    private val secret = SavedTab(TabId(-1), TestProfiles.personal.id, "https://secret.example.com/", "Secret plans", 0, selected = false, isPrivate = true)
    private var shown by mutableStateOf(news)

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(compose.activity, WebProfile.of(ProfileId("test-private-ui")))
            tab.load(titled("one"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "one navigate" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun show(size: DpSize) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                val all = listOf(news, secret).map { it.copy(selected = it.id == shown.id) }
                BrowserWindow(
                    tab,
                    TabsUi(
                        all = all,
                        selected = all.first { it.selected },
                        select = { selectedIds += it },
                        close = {},
                        open = {},
                        openPrivate = { openedPrivate += Unit },
                    ),
                    TestProfiles.ui(),
                )
            }
        }
    }

    private fun secure(): Boolean {
        var flags = 0
        compose.runOnUiThread { flags = compose.activity.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    @Test
    fun whileASavedTabShowsAPrivateTabsPageIsNotNamedAndTheWindowCanBeCaptured() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("tab-row--1").assertExists()
        compose.onNodeWithText("Private Tab").assertExists()
        compose.onNodeWithText("Secret plans").assertDoesNotExist()
        compose.onNodeWithText("secret.example.com").assertDoesNotExist()
        compose.onNodeWithTag("private-mark", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(false, secure())

        compose.onNodeWithTag("tab-row--1").performClick()
        assertEquals(listOf(TabId(-1)), selectedIds)
    }

    @Test
    fun whileAPrivateTabShowsItIsNamedMarkedAndTheWindowCantBeCaptured() {
        shown = secret
        show(TABLET_LANDSCAPE)
        compose.onNodeWithText("Secret plans").assertExists()
        compose.onNodeWithText("secret.example.com").assertExists()
        compose.onNodeWithTag("tab-row--1").assertIsSelected()
        compose.onNodeWithTag("private-mark", useUnmergedTree = true).assertExists()
        assertEquals(true, secure())

        // Back on a saved Tab, the protection ends and the name is hidden again.
        shown = news
        compose.onNodeWithText("Secret plans").assertDoesNotExist()
        assertEquals(false, secure())
    }

    @Test
    fun theBottomBarMarksAPrivateTabToo() {
        shown = secret
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("private-mark", useUnmergedTree = true).assertExists()
        assertEquals(true, secure())
        compose.onNodeWithTag("tab-count").performClick()
        compose.onNodeWithText("Secret plans").assertExists()
    }

    @Test
    fun theMenuOpensAPrivateTabAndAPrivateTabCantBeMoved() {
        shown = secret
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-move").assertIsNotEnabled()
        compose.onNodeWithTag("menu-open-in").assertIsNotEnabled()
        compose.onNodeWithTag("menu-new-private-tab").performClick()
        assertEquals(1, openedPrivate.size)
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
