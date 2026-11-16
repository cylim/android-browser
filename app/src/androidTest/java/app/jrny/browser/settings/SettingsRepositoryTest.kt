package app.jrny.browser.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The app's settings are kept on the device and come back as they were left. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = "test-settings-${System.nanoTime()}"
    private val preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    @After
    fun delete() {
        context.deleteSharedPreferences(file)
    }

    @Test
    fun theDefaultsAreGoogleWithSuggestionsAndSafeBrowsingOn() {
        assertEquals(
            Settings(SearchEngine.Google, suggestions = true, safeBrowsing = true, theme = ThemeChoice.System, compactTabs = false),
            SettingsRepository(preferences).settings.value,
        )
    }

    @Test
    fun changesAreKept() {
        val repository = SettingsRepository(preferences)
        repository.update { it.copy(searchEngine = SearchEngine.DuckDuckGo, suggestions = false) }
        repository.update { it.copy(safeBrowsing = false, theme = ThemeChoice.Dark, compactTabs = true) }
        val changed = Settings(SearchEngine.DuckDuckGo, suggestions = false, safeBrowsing = false, theme = ThemeChoice.Dark, compactTabs = true)
        assertEquals(changed, repository.settings.value)
        // As after the app restarts: read again from the same file.
        assertEquals(changed, SettingsRepository(preferences).settings.value)
    }

    @Test
    fun aStoredValueThisVersionDoesNotKnowFallsBackToTheDefault() {
        preferences.edit().putString("search_engine", "AltaVista").putString("theme", "Sepia").commit()
        val settings = SettingsRepository(preferences).settings.value
        assertEquals(SearchEngine.Google, settings.searchEngine)
        assertEquals(ThemeChoice.System, settings.theme)
    }

    @Test
    fun everySearchEngineHasAWebAddressForSearchesAndSuggestions() {
        SearchEngine.entries.forEach { engine ->
            listOf(engine.searchTemplate, engine.suggestTemplate).forEach { template ->
                assertEquals(engine.name, true, template.startsWith("https://") && template.contains("%s"))
            }
        }
    }
}
