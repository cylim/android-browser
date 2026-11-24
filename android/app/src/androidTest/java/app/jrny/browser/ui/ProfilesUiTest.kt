package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileLook
import app.jrny.browser.profile.ProfileTint
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Switching, creating and editing Profiles in both layouts, without a database. */
@RunWith(AndroidJUnit4::class)
class ProfilesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val switched = mutableListOf<Profile>()
    private val edited = mutableListOf<Profile?>()

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-profiles-ui")))
            tab.load(titled("one"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "one navigate" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun show(size: DpSize, active: Profile = TestProfiles.personal) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme { BrowserWindow(tab, TestTabs.ui(), TestProfiles.ui(active = active, switched = switched, edited = edited)) }
        }
    }

    @Test
    fun theSidebarSwitcherListsTheProfilesAndSwitches() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("profile-switcher").assertExists()
        compose.onNodeWithText("Personal").assertExists()
        compose.onNodeWithTag("profile-switcher").performClick()
        compose.onNodeWithTag("profile-row-test-1").assertIsSelected()
        compose.onNodeWithTag("profile-row-test-2").performClick()
        assertEquals(listOf(TestProfiles.work), switched)
        compose.onNodeWithTag("profile-list").assertDoesNotExist()
    }

    @Test
    fun theRailsAvatarOpensTheSameMenu() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithContentDescription("Hide sidebar").performClick()
        compose.onNodeWithTag("sidebar-rail").assertExists()
        compose.onNodeWithTag("profile-avatar").performClick()
        compose.onNodeWithTag("profile-row-test-2").performClick()
        assertEquals(listOf(TestProfiles.work), switched)
    }

    @Test
    fun theBottomBarsAvatarOpensTheProfileSheet() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("profile-avatar").performClick()
        compose.onNodeWithTag("profile-sheet").assertExists()
        compose.onNodeWithTag("profile-row-test-2").performClick()
        assertEquals(listOf(TestProfiles.work), switched)
    }

    @Test
    fun newProfileAndEditOpenTheEditor() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("profile-avatar").performClick()
        compose.onNodeWithTag("new-profile").performClick()
        compose.onNodeWithTag("profile-avatar").performClick()
        compose.onNodeWithContentDescription("Edit Work").performClick()
        assertEquals(listOf(null, TestProfiles.work), edited)
    }

    @Test
    fun swipingAcrossTheTabAreaSwitchesProfiles() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("sidebar-tabs").performTouchInput { swipeLeft() }
        assertEquals(listOf(TestProfiles.work), switched)
    }

    @Test
    fun swipingPastTheEndDoesNothing() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("sidebar-tabs").performTouchInput { swipeRight() }
        assertEquals(emptyList<Profile>(), switched)
    }

    @Test
    fun theEditorsButtonsStayAboveTheKeyboard() {
        compose.setContent {
            JrnyTheme {
                ProfileEditorDialog(null, canDelete = false, onSave = { _, _ -> }, onDelete = {}, onDismiss = {})
            }
        }
        // Typing a name brings up the keyboard, which on a landscape tablet takes half the screen.
        compose.onNodeWithTag("profile-name-field").performClick()
        compose.waitForIdle()
        val button = compose.onNodeWithTag("save-profile").fetchSemanticsNode()
        val window = (button.root as ViewRootForTest).view.rootView
        var keyboard = 0
        // Not every test device shows a keyboard. Where one shows, give it time to come up.
        runCatching {
            compose.waitUntil(3_000) {
                keyboard = ViewCompat.getRootWindowInsets(window)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                keyboard > 0
            }
        }
        assumeTrue("no on-screen keyboard on this device", keyboard > 0)

        // The keyboard slides in, and the dialog follows it up.
        fun createEndsAt() = compose.onNodeWithTag("save-profile").fetchSemanticsNode().boundsInWindow.bottom
        fun keyboardStartsAt() = window.height - (ViewCompat.getRootWindowInsets(window)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
        runCatching { compose.waitUntil(5_000) { createEndsAt() <= keyboardStartsAt() } }
        assertTrue("Create ends at ${createEndsAt()}, the keyboard starts at ${keyboardStartsAt()}", createEndsAt() <= keyboardStartsAt())
        // The dialog made room without giving up what matters: the button and the name being
        // typed are both still on screen, inside the window.
        compose.onNodeWithTag("save-profile").assertIsDisplayed()
        compose.onNodeWithTag("profile-name-field").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("profile-name-field").fetchSemanticsNode().boundsInWindow.top >= 0f)
        // And it didn't collapse: counting the keyboard's height twice would leave it no room at all.
        val dialog = compose.onNodeWithTag("profile-editor").fetchSemanticsNode().boundsInWindow
        assertTrue("the dialog is only ${dialog.height}px tall", dialog.height >= 160 * window.resources.displayMetrics.density)
    }

    @Test
    fun theEditorCreatesAProfileWithItsNameColorAndIcon() {
        var saved: Pair<String, ProfileLook>? = null
        compose.setContent {
            JrnyTheme {
                ProfileEditorDialog(null, canDelete = false, onSave = { name, look -> saved = name to look }, onDelete = {}, onDismiss = {})
            }
        }
        compose.onNodeWithTag("save-profile").assertIsNotEnabled()
        compose.onNodeWithTag("profile-name-field").performTextReplacement("  School ")
        compose.onNodeWithTag("color-Amber").performScrollTo().performClick()
        compose.onNodeWithTag("icon-Star").performScrollTo().performClick()
        compose.onNodeWithTag("save-profile").assertIsEnabled().performClick()
        assertEquals("School" to ProfileLook(ProfileColor.Amber, ProfileIcon.Star), saved)
    }

    @Test
    fun theEditorSetsAnEmojiAndHowStronglyTheColorShows() {
        var saved: Pair<String, ProfileLook>? = null
        compose.setContent {
            JrnyTheme {
                ProfileEditorDialog(TestProfiles.work, canDelete = true, onSave = { name, look -> saved = name to look }, onDelete = {}, onDismiss = {})
            }
        }
        // As it is: its icon, and the middle strength.
        compose.onNodeWithTag("icon-Build").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("tint-Medium").performScrollTo().assertIsSelected()

        compose.onNodeWithTag("tint-Strong").performClick()
        compose.onNodeWithTag("emoji-🚀").performScrollTo().performClick()
        compose.onNodeWithTag("emoji-🚀").assertIsSelected()
        // An emoji takes the icon's place, so no icon is the chosen one.
        compose.onNodeWithTag("icon-Build").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("save-profile").performClick()
        assertEquals("Work" to ProfileLook(ProfileColor.Teal, ProfileIcon.Build, "🚀", ProfileTint.Strong), saved)

        // Any emoji can be typed. Only the first counts, and plain text is not one.
        compose.onNodeWithTag("profile-emoji-field").performScrollTo().performTextReplacement("🦊🐻")
        compose.onNodeWithTag("emoji-🚀").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("save-profile").performClick()
        assertEquals("🦊", saved?.second?.emoji)
        compose.onNodeWithTag("profile-emoji-field").performScrollTo().performTextReplacement("fox")
        compose.onNodeWithText("That isn't an emoji.", substring = true).assertExists()
        compose.onNodeWithTag("save-profile").performClick()
        assertEquals("🦊", saved?.second?.emoji)

        // Choosing an icon gives the emoji up, and clears what was typed for one.
        compose.onNodeWithTag("icon-Star").performScrollTo().performClick()
        compose.onNodeWithTag("save-profile").performClick()
        assertEquals("Work" to ProfileLook(ProfileColor.Teal, ProfileIcon.Star, null, ProfileTint.Strong), saved)
    }

    @Test
    fun deletingAsksFirstAndTheLastProfileCantBeDeleted() {
        var deleted = 0
        var canDelete by mutableStateOf(false)
        compose.setContent {
            JrnyTheme {
                ProfileEditorDialog(TestProfiles.work, canDelete = canDelete, onSave = { _, _ -> }, onDelete = { deleted++ }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("delete-profile").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("The last Profile can't be deleted.").assertExists()

        canDelete = true
        compose.onNodeWithTag("delete-profile").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Delete Work?").assertExists()
        assertEquals(0, deleted)
        compose.onNodeWithTag("confirm-delete").performClick()
        assertEquals(1, deleted)
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
