package app.jrny.browser.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A search engine the person can choose (SPEC, "Browsing features"). In both templates `%s` is
 * the encoded query. [suggestTemplate] answers in the OpenSearch suggestions format.
 */
enum class SearchEngine(val label: String, val searchTemplate: String, val suggestTemplate: String) {
    Google(
        "Google",
        "https://www.google.com/search?q=%s",
        "https://suggestqueries.google.com/complete/search?client=firefox&q=%s",
    ),
    Bing("Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
    Brave("Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
}

/** Light, dark, or whatever the device uses. */
enum class ThemeChoice { System, Light, Dark }

/** The app's settings. They are shared by all Profiles (SPEC, "Profiles"). */
data class Settings(
    val searchEngine: SearchEngine = SearchEngine.Google,
    /** Whether what is typed in the address field goes to the search engine for suggestions. */
    val suggestions: Boolean = true,
    val safeBrowsing: Boolean = true,
    val theme: ThemeChoice = ThemeChoice.System,
    /** Whether a row of the Tab list takes one line, so that more Tabs fit. */
    val compactTabs: Boolean = false,
    /** How large the text of every page is, in percent (SPEC, "Text size"). */
    val textZoom: Int = 100,
    /** Whether Okapi darkens pages that have no dark theme, while the app's theme is dark (SPEC, "Dark pages"). */
    val darkenPages: Boolean = false,
    /** Whether pulling down on a page scrolled to its top reloads it (SPEC, "Pull to refresh"). */
    val pullToRefresh: Boolean = true,
) {
    companion object {
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 200
        const val TEXT_ZOOM_STEP = 10
    }
}

/**
 * Keeps the [Settings] in the app's own preferences file, which never leaves the device (backups
 * are off). A stored value this version doesn't know falls back to the default.
 */
class SettingsRepository(private val preferences: SharedPreferences) {
    private val state = MutableStateFlow(read())

    val settings: StateFlow<Settings> = state.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val changed = change(state.value)
        state.value = changed
        preferences.edit {
            putString(SEARCH_ENGINE, changed.searchEngine.name)
            putBoolean(SUGGESTIONS, changed.suggestions)
            putBoolean(SAFE_BROWSING, changed.safeBrowsing)
            putString(THEME, changed.theme.name)
            putBoolean(COMPACT_TABS, changed.compactTabs)
            putInt(TEXT_ZOOM, changed.textZoom)
            putBoolean(DARKEN_PAGES, changed.darkenPages)
            putBoolean(PULL_TO_REFRESH, changed.pullToRefresh)
        }
    }

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            searchEngine = SearchEngine.entries.firstOrNull { it.name == preferences.getString(SEARCH_ENGINE, null) } ?: defaults.searchEngine,
            suggestions = preferences.getBoolean(SUGGESTIONS, defaults.suggestions),
            safeBrowsing = preferences.getBoolean(SAFE_BROWSING, defaults.safeBrowsing),
            theme = ThemeChoice.entries.firstOrNull { it.name == preferences.getString(THEME, null) } ?: defaults.theme,
            compactTabs = preferences.getBoolean(COMPACT_TABS, defaults.compactTabs),
            textZoom = preferences.getInt(TEXT_ZOOM, defaults.textZoom).coerceIn(Settings.MIN_TEXT_ZOOM, Settings.MAX_TEXT_ZOOM),
            darkenPages = preferences.getBoolean(DARKEN_PAGES, defaults.darkenPages),
            pullToRefresh = preferences.getBoolean(PULL_TO_REFRESH, defaults.pullToRefresh),
        )
    }

    private companion object {
        const val SEARCH_ENGINE = "search_engine"
        const val SUGGESTIONS = "suggestions"
        const val SAFE_BROWSING = "safe_browsing"
        const val THEME = "theme"
        const val COMPACT_TABS = "compact_tabs"
        const val TEXT_ZOOM = "text_zoom"
        const val DARKEN_PAGES = "darken_pages"
        const val PULL_TO_REFRESH = "pull_to_refresh"
    }
}
