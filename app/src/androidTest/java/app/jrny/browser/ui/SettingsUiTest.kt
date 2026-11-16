package app.jrny.browser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.settings.SearchEngine
import app.jrny.browser.settings.Settings
import app.jrny.browser.settings.Suggestion
import app.jrny.browser.settings.ThemeChoice
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

/** The Settings panel, the search engine and theme it sets, and what the address field offers while typing. */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private var settings by mutableStateOf(Settings())
    private val asked = mutableListOf<String>()
    private var offered = listOf(
        Suggestion("Kotlin docs", "https://kotlinlang.org/docs/", Suggestion.Kind.Bookmark),
        Suggestion("kotlin coroutines", null, Suggestion.Kind.Search),
    )

    @Before
    fun createTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-settings-ui")))
            // With a page, so the address field doesn't take focus and open the keyboard by itself.
            tab.load(titled("start"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "start navigate" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun show(size: DpSize, pendingDeletions: Int = 0) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme(settings.theme) {
                BrowserWindow(
                    tab,
                    TestTabs.ui(),
                    TestProfiles.ui(),
                    settings = SettingsUi(settings, update = { change -> settings = change(settings) }, pendingDeletions, version = "1.2.3"),
                    suggest = { text ->
                        asked += text
                        offered
                    },
                )
            }
        }
    }

    private fun openSettings() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-settings").performClick()
        compose.onNodeWithTag("panel-Settings").assertExists()
    }

    @Test
    fun theSettingsShowWhatIsSetAndChangeIt() {
        show(TABLET_LANDSCAPE)
        openSettings()
        compose.onNodeWithText("For all Profiles").assertExists()
        compose.onNodeWithTag("engine-Google").assertIsSelected()
        compose.onNodeWithTag("setting-suggestions").assertIsOn()
        compose.onNodeWithTag("setting-safe-browsing").assertIsOn()
        compose.onNodeWithTag("theme-System").assertIsSelected()

        compose.onNodeWithTag("engine-DuckDuckGo").performClick()
        compose.onNodeWithTag("setting-suggestions").performClick()
        compose.onNodeWithTag("setting-safe-browsing").performScrollTo().performClick()
        compose.onNodeWithTag("theme-Dark").performScrollTo().performClick()

        assertEquals(Settings(SearchEngine.DuckDuckGo, suggestions = false, safeBrowsing = false, theme = ThemeChoice.Dark), settings)
        compose.onNodeWithTag("engine-DuckDuckGo").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("setting-suggestions").assertIsOff()
        // The explanation names the engine the text would go to.
        compose.onNodeWithText("sent to DuckDuckGo", substring = true).assertExists()
    }

    @Test
    fun onAPhoneTheSettingsFillTheScreenAndBackClosesThem() {
        show(PHONE_PORTRAIT, pendingDeletions = 2)
        openSettings()
        compose.onNodeWithTag("bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("settings-pending-removal").performScrollTo().assertExists()
        compose.onNodeWithText("Version 1.2.3").performScrollTo().assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("panel-Settings").assertDoesNotExist()
        compose.onNodeWithTag("bottom-bar").assertExists()
    }

    @Test
    fun withNoDeletedProfilesNothingIsSaidAboutThem() {
        show(TABLET_LANDSCAPE)
        openSettings()
        compose.onNodeWithTag("settings-pending-removal").assertDoesNotExist()
    }

    @Test
    fun theThemeSettingDecidesLightOrDark() {
        var background = 0f
        var theme by mutableStateOf(ThemeChoice.Dark)
        compose.setContent {
            JrnyTheme(theme) { background = MaterialTheme.colorScheme.background.luminance() }
        }
        compose.waitForIdle()
        assertTrue("dark background: $background", background < 0.2f)
        theme = ThemeChoice.Light
        compose.waitForIdle()
        assertTrue("light background: $background", background > 0.8f)
    }

    @Test
    fun aSearchGoesToTheChosenEngine() {
        settings = settings.copy(searchEngine = SearchEngine.DuckDuckGo)
        offered = emptyList()
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").performTextReplacement("two words")
        compose.onNodeWithTag("address").performImeAction()
        assertEquals("https://duckduckgo.com/?q=two+words", tab.url)
    }

    @Test
    fun typingOffersPagesAndSearchesInTheSidebarAndChoosingOneOpensIt() {
        // Typing brings up the keyboard. Forced onto a phone's screen, a tablet-sized window
        // would have none of the Sidebar's list left above it.
        assumeTrue(fitsTheScreen(TABLET_LANDSCAPE))
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("tab-list").assertExists()
        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").performTextReplacement("kotlin")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("suggestion").fetchSemanticsNodes().size == 2 }
        assertEquals(listOf("kotlin"), asked)
        // The suggestions take the Tab list's place while typing.
        compose.onNodeWithTag("tab-list").assertDoesNotExist()
        compose.onNodeWithText("kotlinlang.org").assertExists()

        compose.onNodeWithText("Kotlin docs").performClick()
        assertEquals("https://kotlinlang.org/docs/", tab.url)
        compose.onNodeWithTag("suggestions").assertDoesNotExist()
        compose.onNodeWithTag("tab-list").assertExists()
    }

    @Test
    fun openingAPanelGivesUpTheAddressBeingTyped() {
        assumeTrue(fitsTheScreen(TABLET_LANDSCAPE))
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").performTextReplacement("kotlin")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("suggestion").fetchSemanticsNodes().size == 2 }

        openSettings()
        compose.onNodeWithTag("suggestions").assertDoesNotExist()
        compose.onNodeWithTag("tab-list").assertExists()
        // The field shows the page's own address again.
        compose.onNodeWithTag("address").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(tab.url!!)),
        )
    }

    @Test
    fun onAPhoneASuggestedSearchRunsOnTheChosenEngine() {
        settings = settings.copy(searchEngine = SearchEngine.Bing)
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").performTextReplacement("kotlin")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("suggestion").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("kotlin coroutines").performClick()
        assertEquals("https://www.bing.com/search?q=kotlin+coroutines", tab.url)
        compose.onNodeWithTag("suggestions").assertDoesNotExist()
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
