package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.jrny.browser.R
import app.jrny.browser.rules.LinkWrapper
import app.jrny.browser.rules.SitePattern

/**
 * The Link Wrappers that Site Rules see through, in the page's place, opened from the Site Rules.
 * A tap on one changes it. Once the person has changed the list, "Use default list" takes the
 * default back, after asking.
 */
@Composable
fun LinkWrappersPanel(
    ui: SiteRulesUi,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Some(null) adds a wrapper, Some(wrapper) changes one.
    var editing by remember { mutableStateOf<WrapperEdit?>(null) }
    var confirmingDefault by remember { mutableStateOf(false) }
    val wrappers = ui.wrappers.all

    PanelFrame(
        tag = "panel-LinkWrappers",
        title = stringResource(R.string.link_wrappers),
        subtitle = stringResource(R.string.link_wrappers_subtitle),
        onClose = onClose,
        onBack = onBack,
        modifier = modifier,
        action = {
            TextButton(onClick = { editing = WrapperEdit(null) }, modifier = Modifier.testTag("add-link-wrapper")) {
                Text(stringResource(R.string.add_link_wrapper))
            }
        },
    ) {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.testTag("link-wrappers-list")) {
            item("explanation") {
                Text(
                    stringResource(if (wrappers.isEmpty()) R.string.link_wrappers_empty else R.string.link_wrappers_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp),
                )
            }
            // A site has one wrapper, so its pattern is its key.
            items(wrappers, key = { it.pattern.text }) { wrapper ->
                WrapperRow(wrapper, onEdit = { editing = WrapperEdit(wrapper) }, onDelete = { ui.deleteWrapper(wrapper) })
            }
            if (!ui.wrappers.isDefault) {
                item("use-default") {
                    TextButton(
                        onClick = { confirmingDefault = true },
                        modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET - 12.dp, vertical = 8.dp).testTag("use-default-link-wrappers"),
                    ) { Text(stringResource(R.string.use_default_link_wrappers)) }
                }
            }
        }
    }

    editing?.let { edit ->
        LinkWrapperEditorDialog(
            wrapper = edit.wrapper,
            others = wrappers - listOfNotNull(edit.wrapper).toSet(),
            onSave = { wrapper ->
                ui.saveWrapper(edit.wrapper, wrapper)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmingDefault) {
        AlertDialog(
            onDismissRequest = { confirmingDefault = false },
            title = { Text(stringResource(R.string.use_default_link_wrappers_title)) },
            text = { Text(stringResource(R.string.use_default_link_wrappers_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.useDefaultWrappers()
                        confirmingDefault = false
                    },
                    modifier = Modifier.testTag("confirm-use-default-link-wrappers"),
                ) { Text(stringResource(R.string.use_default_link_wrappers)) }
            },
            dismissButton = { TextButton(onClick = { confirmingDefault = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private class WrapperEdit(val wrapper: LinkWrapper?)

/** The names of [parameters] as a person reads them: `url`, `q or url`, `a, b or c`. */
@Composable
private fun parameterNames(parameters: List<String>): String =
    if (parameters.size < 2) {
        parameters.joinToString()
    } else {
        stringResource(R.string.link_wrapper_parameters_or, parameters.dropLast(1).joinToString(", "), parameters.last())
    }

/** A Link Wrapper: its site, the parameters it is read from, and the button that deletes it. A tap changes it. */
@Composable
private fun WrapperRow(wrapper: LinkWrapper, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(start = PANEL_TEXT_INSET)
            .testTag("link-wrapper"),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(wrapper.pattern.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                stringResource(R.string.link_wrapper_reads, parameterNames(wrapper.parameters)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.padding(end = PANEL_ICON_INSET)) {
            Icon(Icons.Filled.Delete, stringResource(R.string.delete_link_wrapper, wrapper.pattern.text))
        }
    }
}

/**
 * Adds a Link Wrapper ([wrapper] null) or changes one: its site, read as a Site Rule's but naming
 * its own site, and the names of the query parameters that carry the address. A site has one
 * wrapper, so the dialog says when this one replaces one of [others].
 */
@Composable
private fun LinkWrapperEditorDialog(wrapper: LinkWrapper?, others: List<LinkWrapper>, onSave: (LinkWrapper) -> Unit, onDismiss: () -> Unit) {
    var site by remember { mutableStateOf(wrapper?.pattern?.text.orEmpty()) }
    var parameters by remember { mutableStateOf(wrapper?.parameters?.joinToString(", ").orEmpty()) }
    val pattern = SitePattern.parse(site)
    val ownSite = pattern != null && LinkWrapper.namesItsSite(pattern)
    val names = LinkWrapper.parametersOf(parameters)
    val parsed = if (ownSite && names != null) LinkWrapper(pattern, names) else null
    val replaced = others.firstOrNull { it.pattern == pattern }
    val siteField = remember { FocusRequester() }
    if (wrapper == null) LaunchedEffect(Unit) { siteField.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // As the Site Rule editor: laid out in the whole window, clear of the system bars and the keyboard.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp).testTag("link-wrapper-editor"),
        title = { Text(stringResource(if (wrapper == null) R.string.new_link_wrapper else R.string.edit_link_wrapper)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text(stringResource(R.string.rule_site)) },
                    placeholder = { Text(stringResource(R.string.rule_site_placeholder)) },
                    isError = site.isNotBlank() && !ownSite,
                    supportingText = {
                        Text(
                            when {
                                site.isBlank() -> stringResource(R.string.link_wrapper_site_help)
                                pattern == null -> stringResource(R.string.rule_not_a_site)
                                !ownSite -> stringResource(R.string.link_wrapper_too_broad)
                                replaced != null -> stringResource(R.string.link_wrapper_replaces, replaced.pattern.text)
                                else -> stringResource(R.string.link_wrapper_site_help)
                            },
                            modifier = Modifier.testTag("wrapper-site-explanation"),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = WEB_ADDRESS_KEYBOARD.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(siteField).testTag("wrapper-site-field"),
                )
                val badNames = parameters.isNotBlank() && names == null
                OutlinedTextField(
                    value = parameters,
                    onValueChange = { parameters = it },
                    label = { Text(stringResource(R.string.link_wrapper_parameters)) },
                    placeholder = { Text(stringResource(R.string.link_wrapper_parameters_placeholder)) },
                    isError = badNames,
                    supportingText = {
                        Text(stringResource(if (badNames) R.string.link_wrapper_parameters_invalid else R.string.link_wrapper_parameters_help))
                    },
                    singleLine = true,
                    keyboardOptions = WEB_ADDRESS_KEYBOARD.copy(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("wrapper-parameter-field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null, modifier = Modifier.testTag("save-link-wrapper")) {
                Text(stringResource(if (wrapper == null) R.string.add_link_wrapper else R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
