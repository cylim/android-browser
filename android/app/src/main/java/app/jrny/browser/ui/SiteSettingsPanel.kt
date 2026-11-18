package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import app.jrny.browser.site.SiteSettingsEntry

/** The Active Profile's Site settings and how to change them. */
class SiteSettingsUi(
    /** Its sites with a setting, in alphabetical order. */
    val sites: List<SiteSettingsEntry> = emptyList(),
    val set: (SiteOrigin, SiteSetting, Boolean) -> Unit = { _, _, _ -> },
    val reset: (SiteOrigin, SiteSetting) -> Unit = { _, _ -> },
    val resetAll: () -> Unit = {},
    /** Keeps the person's answer to a site's ask in the Profile of the Tab it came from. */
    val rememberPermission: (SiteOrigin, Set<SiteSetting>, Boolean) -> Unit = { _, _, _ -> },
)

/**
 * The Active Profile's Site settings, in the page's place (SPEC, "Site permissions and Site
 * settings"): each site under its name, with each permission it was given or refused and its
 * Desktop site choice. Tapping one offers to allow it, block it, or reset it so the site asks
 * again. "Reset all" resets every site of the Profile, after a confirmation.
 */
@Composable
fun SiteSettingsPanel(
    ui: SiteSettingsUi,
    profileName: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmReset by remember { mutableStateOf(false) }
    PanelFrame(
        tag = "panel-SiteSettings",
        title = stringResource(R.string.site_settings),
        subtitle = profileName,
        onClose = onClose,
        onBack = onBack,
        modifier = modifier,
        action = {
            if (ui.sites.isNotEmpty()) {
                TextButton(onClick = { confirmReset = true }, modifier = Modifier.testTag("reset-all-sites")) { Text(stringResource(R.string.reset_all)) }
            }
        },
    ) {
        if (ui.sites.isEmpty()) {
            Text(
                stringResource(R.string.site_settings_empty, profileName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 12.dp).testTag("site-settings-empty"),
            )
        } else {
            LazyColumn(contentPadding = contentPadding) {
                items(ui.sites, key = { it.origin.text }) { site ->
                    Text(
                        siteLabel(site.origin),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = PANEL_TEXT_INSET, end = PANEL_TEXT_INSET, top = 24.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                    SiteSetting.entries.forEach { setting ->
                        site.settings[setting]?.let { enabled ->
                            SettingRow(
                                site.origin,
                                setting,
                                enabled,
                                onSet = { ui.set(site.origin, setting, it) },
                                onReset = { ui.reset(site.origin, setting) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            modifier = Modifier.testTag("reset-all-sites-dialog"),
            title = { Text(stringResource(R.string.reset_all_sites_title, profileName)) },
            text = { Text(stringResource(R.string.reset_all_sites_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.resetAll()
                        confirmReset = false
                    },
                    modifier = Modifier.testTag("confirm-reset-all-sites"),
                ) { Text(stringResource(R.string.reset_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The site's host, with its scheme only when it isn't the usual https, so an http site stands out. */
private fun siteLabel(origin: SiteOrigin): String =
    if (origin.text.startsWith("https://")) origin.text.removePrefix("https://") else origin.text

/**
 * One setting of one site: what it is, and at the row's end what it is set to. A tap opens the
 * choices: allow or block (on or off for Desktop site), and reset.
 */
@Composable
private fun SettingRow(origin: SiteOrigin, setting: SiteSetting, enabled: Boolean, onSet: (Boolean) -> Unit, onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = stringResource(settingName(setting))
    val onText = stringResource(if (setting.isPermission) R.string.site_setting_allowed else R.string.site_setting_on)
    val offText = stringResource(if (setting.isPermission) R.string.site_setting_blocked else R.string.site_setting_off)
    val change = stringResource(R.string.change_site_setting, name, origin.host)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = change, role = Role.DropdownList) { open = true }
                .padding(horizontal = PANEL_TEXT_INSET)
                .testTag("site-setting-${origin.host}-${setting.key}"),
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (enabled) onText else offText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (setting.isPermission) R.string.allow else R.string.site_setting_on)) },
                onClick = {
                    open = false
                    onSet(true)
                },
                modifier = Modifier.testTag("site-setting-allow"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (setting.isPermission) R.string.block else R.string.site_setting_off)) },
                onClick = {
                    open = false
                    onSet(false)
                },
                modifier = Modifier.testTag("site-setting-block"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reset)) },
                onClick = {
                    open = false
                    onReset()
                },
                modifier = Modifier.testTag("site-setting-reset"),
            )
        }
    }
}

private fun settingName(setting: SiteSetting): Int = when (setting) {
    SiteSetting.Camera -> R.string.site_setting_camera
    SiteSetting.Microphone -> R.string.site_setting_microphone
    SiteSetting.Location -> R.string.site_setting_location
    SiteSetting.DesktopSite -> R.string.site_setting_desktop
}
