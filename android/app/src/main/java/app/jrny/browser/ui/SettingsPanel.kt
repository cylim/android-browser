package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.settings.SearchEngine
import kotlin.math.roundToInt
import app.jrny.browser.settings.Settings
import app.jrny.browser.settings.ThemeChoice

/** The app's settings and how to change them. */
class SettingsUi(
    val settings: Settings = Settings(),
    val update: ((Settings) -> Settings) -> Unit = {},
    /** Deleted Profiles whose web data is wiped at the next cold start (SPEC, "Profiles"). */
    val pendingDeletions: Int = 0,
    val version: String = "",
)

/**
 * The app's settings, in the page's place. They are shared by all Profiles, and the panel says
 * so. Each row is at least 56dp tall; text starts on the panel's text line, and switches and
 * radio buttons end 16dp from the column's edge.
 */
@Composable
fun SettingsPanel(
    ui: SettingsUi,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** How many Site Rules there are, shown on the row that leads to them. */
    siteRules: Int = 0,
    onSiteRules: () -> Unit = {},
    /** The Active Profile, whose Site settings the row that leads to them names. */
    profileName: String = "",
    /** How many sites the Active Profile has Site settings for. */
    sites: Int = 0,
    onSiteSettings: () -> Unit = {},
) {
    val settings = ui.settings
    PanelFrame(
        tag = "panel-Settings",
        title = stringResource(R.string.settings),
        subtitle = stringResource(R.string.settings_for_all_profiles),
        onClose = onClose,
        modifier = modifier,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(contentPadding)) {
            Section(R.string.search_engine)
            Column(Modifier.selectableGroup()) {
                SearchEngine.entries.forEach { engine ->
                    Choice(engine.label, selected = settings.searchEngine == engine, tag = "engine-${engine.name}") {
                        ui.update { it.copy(searchEngine = engine) }
                    }
                }
            }
            Toggle(
                title = R.string.search_suggestions,
                explanation = stringResource(R.string.search_suggestions_explanation, settings.searchEngine.label),
                checked = settings.suggestions,
                tag = "setting-suggestions",
            ) { on -> ui.update { it.copy(suggestions = on) } }

            Section(R.string.links_from_other_apps)
            Link(
                title = R.string.site_rules,
                explanation = stringResource(R.string.site_rules_setting_explanation),
                value = if (siteRules == 0) stringResource(R.string.site_rules_none) else pluralStringResource(R.plurals.site_rule_count, siteRules, siteRules),
                tag = "setting-site-rules",
                onClick = onSiteRules,
            )

            Section(R.string.sites)
            Link(
                title = R.string.site_settings,
                explanation = stringResource(R.string.site_settings_explanation, profileName),
                value = if (sites == 0) stringResource(R.string.site_settings_none) else pluralStringResource(R.plurals.site_count, sites, sites),
                tag = "setting-site-settings",
                onClick = onSiteSettings,
            )

            Section(R.string.safety)
            Toggle(
                title = R.string.safe_browsing,
                explanation = stringResource(R.string.safe_browsing_explanation),
                checked = settings.safeBrowsing,
                tag = "setting-safe-browsing",
            ) { on -> ui.update { it.copy(safeBrowsing = on) } }

            Section(R.string.theme)
            Column(Modifier.selectableGroup()) {
                ThemeChoice.entries.forEach { theme ->
                    val label = when (theme) {
                        ThemeChoice.System -> R.string.theme_system
                        ThemeChoice.Light -> R.string.theme_light
                        ThemeChoice.Dark -> R.string.theme_dark
                    }
                    Choice(stringResource(label), selected = settings.theme == theme, tag = "theme-${theme.name}") {
                        ui.update { it.copy(theme = theme) }
                    }
                }
            }

            Section(R.string.pages)
            TextSize(settings.textZoom) { zoom -> ui.update { it.copy(textZoom = zoom) } }
            Toggle(
                title = R.string.darken_pages,
                explanation = stringResource(R.string.darken_pages_explanation),
                checked = settings.darkenPages,
                tag = "setting-darken-pages",
            ) { on -> ui.update { it.copy(darkenPages = on) } }
            Toggle(
                title = R.string.pull_to_refresh,
                explanation = stringResource(R.string.pull_to_refresh_explanation),
                checked = settings.pullToRefresh,
                tag = "setting-pull-to-refresh",
            ) { on -> ui.update { it.copy(pullToRefresh = on) } }

            Section(R.string.tab_list)
            Toggle(
                title = R.string.compact_tabs,
                explanation = stringResource(R.string.compact_tabs_explanation),
                checked = settings.compactTabs,
                tag = "setting-compact-tabs",
            ) { on -> ui.update { it.copy(compactTabs = on) } }

            if (ui.pendingDeletions > 0) {
                Section(R.string.profiles)
                Note(pluralStringResource(R.plurals.profiles_pending_removal, ui.pendingDeletions, ui.pendingDeletions), "settings-pending-removal")
            }
            if (ui.version.isNotEmpty()) {
                Note(stringResource(R.string.version, ui.version), "settings-version")
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = PANEL_TEXT_INSET, end = PANEL_TEXT_INSET, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 12.dp).testTag(tag),
    )
}

/** One of several choices. The whole row is the target, and the radio button follows the text. */
@Composable
private fun Choice(label: String, selected: Boolean, tag: String, onChoose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onChoose)
            .padding(horizontal = PANEL_TEXT_INSET)
            .testTag(tag),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * A row that leads to more settings, with what they are for underneath, and at its end what is
 * set there now and an arrow. The arrow ends on the same line as the switches.
 */
@Composable
private fun Link(title: Int, explanation: String, value: String, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp)
            .testTag(tag),
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Text size for every page (SPEC, "Text size"): a slider in steps of 10%, from 50% to 200%, with
 * a sample line in that size.
 */
@Composable
private fun TextSize(zoom: Int, onChange: (Int) -> Unit) {
    val steps = (Settings.MAX_TEXT_ZOOM - Settings.MIN_TEXT_ZOOM) / Settings.TEXT_ZOOM_STEP
    Column(Modifier.fillMaxWidth().padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp).testTag("setting-text-size")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.text_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.percent, zoom), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = zoom.toFloat(),
            onValueChange = { onChange((it / Settings.TEXT_ZOOM_STEP).roundToInt() * Settings.TEXT_ZOOM_STEP) },
            valueRange = Settings.MIN_TEXT_ZOOM.toFloat()..Settings.MAX_TEXT_ZOOM.toFloat(),
            steps = steps - 1,
            modifier = Modifier.testTag("text-size-slider"),
        )
        Text(
            stringResource(R.string.text_size_sample),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * (zoom / 100f)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An on/off setting with what it means underneath. The whole row is the target. */
@Composable
private fun Toggle(title: Int, explanation: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp)
            .testTag(tag),
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
