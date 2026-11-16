package app.jrny.browser.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Switching, creating and editing Profiles in both layouts, without a database. */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class ProfilesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val switched = mutableListOf<Profile>()
    private val edited = mutableListOf<Profile?>()

    @Before
    fun createTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
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
    fun theEditorCreatesAProfileWithItsNameColorAndIcon() {
        var saved: Triple<String, ProfileColor, ProfileIcon>? = null
        compose.setContent {
            JrnyTheme {
                ProfileEditorDialog(null, canDelete = false, onSave = { n, c, i -> saved = Triple(n, c, i) }, onDelete = {}, onDismiss = {})
            }
        }
        compose.onNodeWithTag("save-profile").assertIsNotEnabled()
        compose.onNodeWithTag("profile-name-field").performTextReplacement("  School ")
        compose.onNodeWithTag("color-Amber").performScrollTo().performClick()
        compose.onNodeWithTag("icon-Star").performScrollTo().performClick()
        compose.onNodeWithTag("save-profile").assertIsEnabled().performClick()
        assertEquals(Triple("School", ProfileColor.Amber, ProfileIcon.Star), saved)
    }

    @Test
    fun deletingAsksFirstAndTheLastProfileCantBeDeleted() {
        var deleted = 0
        var canDelete by mutableStateOf(false)
        compose.setContent {
            JrnyTheme {
                ProfileEditorDialog(TestProfiles.work, canDelete = canDelete, onSave = { _, _, _ -> }, onDelete = { deleted++ }, onDismiss = {})
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
