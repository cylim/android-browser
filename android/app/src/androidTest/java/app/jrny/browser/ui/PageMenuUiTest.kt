package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The page menu, the Profile picker for Move and Open in Profile, and the Incoming Link prompt. */
@RunWith(AndroidJUnit4::class)
class PageMenuUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val moved = mutableListOf<Profile>()
    private val openedIn = mutableListOf<Profile>()
    private val opened = mutableListOf<Unit>()
    private var defaultBrowserAsked = 0
    private var incomingLink by mutableStateOf(false)

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-page-menu")))
        }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun loadPage() {
        compose.runOnUiThread { tab.load(titled("one")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "one navigate" }
    }

    private fun show(
        size: DpSize,
        all: List<Profile> = listOf(TestProfiles.personal, TestProfiles.work),
        offerDefaultBrowser: Boolean = false,
    ) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TestTabs.ui(
                        opened = opened,
                        moved = moved,
                        openedIn = openedIn,
                        incomingLink = incomingLink,
                        dismissIncomingLink = { incomingLink = false },
                    ),
                    TestProfiles.ui(all = all),
                    onSetDefaultBrowser = if (offerDefaultBrowser) ({ defaultBrowserAsked++ }) else null,
                )
            }
        }
    }

    private fun openMenu() = compose.onNodeWithTag("page-menu").performClick()

    @Test
    fun moveWarnsFirstAndMovesToTheChosenProfile() {
        loadPage()
        show(TABLET_LANDSCAPE)
        openMenu()
        compose.onNodeWithTag("menu-move").assertIsEnabled().performClick()

        compose.onNodeWithTag("profile-picker").assertExists()
        compose.onNodeWithText("Unsaved form input will be lost.", substring = true).assertExists()
        // Only the other Profiles are offered.
        compose.onNodeWithTag("pick-profile-${TestProfiles.personal.id.value}").assertDoesNotExist()
        assertEquals(emptyList<Profile>(), moved)

        compose.onNodeWithTag("pick-profile-${TestProfiles.work.id.value}").performClick()
        assertEquals(listOf(TestProfiles.work), moved)
        assertEquals(emptyList<Profile>(), openedIn)
        compose.onNodeWithTag("profile-picker").assertDoesNotExist()
    }

    @Test
    fun openInProfileOpensThePageInTheChosenProfile() {
        loadPage()
        show(PHONE_PORTRAIT)
        openMenu()
        compose.onNodeWithTag("menu-open-in").assertIsEnabled().performClick()
        compose.onNodeWithText("This Tab stays open.", substring = true).assertExists()
        compose.onNodeWithTag("pick-profile-${TestProfiles.work.id.value}").performClick()
        assertEquals(listOf(TestProfiles.work), openedIn)
        assertEquals(emptyList<Profile>(), moved)
    }

    @Test
    fun cancellingThePickerDoesNothing() {
        loadPage()
        show(PHONE_PORTRAIT)
        openMenu()
        compose.onNodeWithTag("menu-move").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("profile-picker").assertDoesNotExist()
        assertEquals(emptyList<Profile>(), moved)
    }

    @Test
    fun thePickerClosesWhenAnotherTabIsShown() {
        loadPage()
        var selected by mutableStateOf(TestTabs.one)
        val other = TestTabs.tab(2, "other", "http://example.com/other", selected = true)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE_PORTRAIT)) {
                JrnyTheme {
                    BrowserWindow(tab, TestTabs.ui(all = listOf(selected), moved = moved), TestProfiles.ui())
                }
            }
        }
        openMenu()
        compose.onNodeWithTag("menu-move").performClick()
        compose.onNodeWithTag("profile-picker").assertExists()

        // An Incoming Link, say, selects another Tab while the picker is open.
        selected = other
        compose.onNodeWithTag("profile-picker").assertDoesNotExist()
        assertEquals(emptyList<Profile>(), moved)
    }

    @Test
    fun aTabWithoutAnAddressOrWithoutAnotherProfileCantBeMoved() {
        // No page loaded: there's no address to carry.
        show(TABLET_LANDSCAPE)
        openMenu()
        compose.onNodeWithTag("menu-move").assertIsNotEnabled()
        compose.onNodeWithTag("menu-open-in").assertIsNotEnabled()
    }

    @Test
    fun withOneProfileThereIsNowhereToMoveTo() {
        loadPage()
        incomingLink = true
        show(PHONE_PORTRAIT, all = listOf(TestProfiles.personal))
        compose.onNodeWithTag("incoming-link-prompt").assertDoesNotExist()
        openMenu()
        compose.onNodeWithTag("menu-move").assertIsNotEnabled()
        compose.onNodeWithTag("menu-open-in").assertIsNotEnabled()
    }

    @Test
    fun theMenuOpensANewTab() {
        loadPage()
        show(TABLET_LANDSCAPE)
        openMenu()
        compose.onNodeWithTag("menu-new-tab").performClick()
        assertEquals(1, opened.size)
    }

    @Test
    fun forwardIsInTheMenuOnlyWhereTheBarHasNoRoomForIt() {
        loadPage()
        show(PHONE_PORTRAIT)
        compose.onNodeWithContentDescription("Forward").assertDoesNotExist()
        openMenu()
        compose.onNodeWithTag("menu-forward").assertExists().assertIsNotEnabled()
    }

    @Test
    fun aTabletInPortraitKeepsTheForwardButtonInTheBar() {
        loadPage()
        show(TABLET_PORTRAIT)
        compose.onNodeWithTag("bottom-bar-layout").assertExists()
        compose.onNodeWithContentDescription("Forward").assertExists()
        openMenu()
        compose.onNodeWithTag("menu-forward").assertDoesNotExist()
    }

    @Test
    fun theSidebarAndTheRailHaveTheMenuWithoutForward() {
        loadPage()
        show(TABLET_LANDSCAPE)
        compose.onNodeWithContentDescription("Forward").assertExists()
        openMenu()
        compose.onNodeWithTag("menu-forward").assertDoesNotExist()
        compose.onNodeWithTag("menu-move").assertExists()
    }

    @Test
    fun theDefaultBrowserItemShowsOnlyWhenItCanBeAsked() {
        // With a page, so the address field doesn't open the keyboard over the menu.
        loadPage()
        show(PHONE_PORTRAIT, offerDefaultBrowser = true)
        openMenu()
        compose.onNodeWithTag("menu-default-browser").performScrollTo().performClick()
        assertEquals(1, defaultBrowserAsked)
    }

    @Test
    fun withoutTheOfferThereIsNoDefaultBrowserItem() {
        loadPage()
        show(PHONE_PORTRAIT)
        openMenu()
        compose.onNodeWithTag("menu-default-browser").assertDoesNotExist()
    }

    @Test
    fun anIncomingLinkOffersAMoveAndCanBeDismissed() {
        loadPage()
        incomingLink = true
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("incoming-link-prompt").assertExists()
        compose.onNodeWithText("Opened in Personal").assertExists()

        compose.onNodeWithTag("incoming-link-action").performClick()
        compose.onNodeWithTag("pick-profile-${TestProfiles.work.id.value}").performClick()
        assertEquals(listOf(TestProfiles.work), moved)

        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.onNodeWithTag("incoming-link-prompt").assertDoesNotExist()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_PORTRAIT = DpSize(800.dp, 1280.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
