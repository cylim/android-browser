package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.jrny.browser.R
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.rules.SitePattern
import app.jrny.browser.rules.SiteRule
import app.jrny.browser.rules.ruleFor

/** The Site Rules and how to change them. */
class SiteRulesUi(
    /** Every rule, oldest first. */
    val rules: List<SiteRule> = emptyList(),
    /** Adds a rule, or changes [existing]: links the pattern covers open in that Profile. */
    val save: (existing: SiteRule?, pattern: SitePattern, profile: Profile) -> Unit = { _, _, _ -> },
    val delete: (SiteRule) -> Unit = {},
)

/**
 * The Site Rules, in the page's place: which Profile takes the links from other apps for which
 * site. A tap on a rule changes it. "Check a link" answers where a given link would open, with
 * the rules as they stand.
 *
 * Each rule shows the avatar of the Profile it opens in: the avatar's circle starts on the
 * panel's text line, and the pattern follows 8dp after its 48dp slot. [pageUrl] is the address of
 * the page in the shown Tab, whose site a new rule can take over with one tap.
 */
@Composable
fun SiteRulesPanel(
    ui: SiteRulesUi,
    profiles: ProfilesUi,
    pageUrl: String?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Some(null) adds a rule, Some(rule) changes one.
    var editing by remember { mutableStateOf<RuleEdit?>(null) }
    var checked by rememberSaveable { mutableStateOf("") }
    val byId = profiles.all.associateBy { it.id }
    // A rule whose Profile was just deleted goes with it; until the list catches up, it isn't shown.
    val rules = ui.rules.filter { it.profileId in byId }

    PanelFrame(
        tag = "panel-SiteRules",
        title = stringResource(R.string.site_rules),
        subtitle = stringResource(R.string.site_rules_subtitle),
        onClose = onClose,
        onBack = onBack,
        modifier = modifier,
        action = {
            if (rules.isNotEmpty()) {
                TextButton(onClick = { editing = RuleEdit(null) }, modifier = Modifier.testTag("add-site-rule")) {
                    Text(stringResource(R.string.add_rule))
                }
            }
        },
    ) {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.testTag("site-rules-list")) {
            if (profiles.all.size < 2) {
                item("one-profile") {
                    Text(
                        stringResource(R.string.site_rules_one_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp).testTag("site-rules-one-profile"),
                    )
                }
            }
            if (rules.isEmpty()) {
                item("empty") { NoRules(onAdd = { editing = RuleEdit(null) }) }
            } else {
                item("check") { CheckLink(checked, { checked = it }, rules, byId, profiles.active) }
                items(rules, key = { it.id }) { rule ->
                    RuleRow(rule, byId.getValue(rule.profileId), onEdit = { editing = RuleEdit(rule) }, onDelete = { ui.delete(rule) })
                }
            }
        }
    }

    editing?.let { edit ->
        SiteRuleEditorDialog(
            rule = edit.rule,
            rules = rules,
            profiles = profiles.all,
            preselected = profiles.active,
            pageSite = SitePattern.forSiteOf(pageUrl),
            onSave = { pattern, profile ->
                ui.save(edit.rule, pattern, profile)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private class RuleEdit(val rule: SiteRule?)

/** What the panel says before there is a rule: what one does, and the way to the first. */
@Composable
private fun NoRules(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_TEXT_INSET, vertical = 48.dp),
    ) {
        Text(stringResource(R.string.site_rules_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.site_rules_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        FilledTonalButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp).testTag("add-first-site-rule")) {
            Text(stringResource(R.string.add_rule))
        }
    }
}

/**
 * Answers where a link would open: the Profile of the rule that covers it, or the Active Profile.
 * What is typed without `https://` in front is read as if it had it.
 */
@Composable
private fun CheckLink(text: String, onChange: (String) -> Unit, rules: List<SiteRule>, profiles: Map<ProfileId, Profile>, active: Profile) {
    val url = SitePattern.linkFrom(text)
    val rule = url?.let(rules::ruleFor)
    val answer = when {
        text.isBlank() -> null
        url == null -> stringResource(R.string.check_link_not_web)
        rule != null -> stringResource(R.string.check_link_rule, profiles.getValue(rule.profileId).name, rule.pattern.text)
        else -> stringResource(R.string.check_link_no_rule, active.name)
    }
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.check_link)) },
        placeholder = { Text(stringResource(R.string.check_link_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText = answer?.let { { Text(it, modifier = Modifier.testTag("check-link-answer")) } },
        trailingIcon = if (text.isEmpty()) {
            null
        } else {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } }
        },
        singleLine = true,
        keyboardOptions = WEB_ADDRESS_KEYBOARD,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_TEXT_INSET).padding(top = 8.dp, bottom = 8.dp).testTag("check-link"),
    )
}

/** A rule: the Profile it opens in, its pattern, and the button that deletes it. A tap changes it. */
@Composable
private fun RuleRow(rule: SiteRule, profile: Profile, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(start = RULE_AVATAR_INSET)
            .testTag("site-rule"),
    ) {
        ProfileAvatar(profile)
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(rule.pattern.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                stringResource(R.string.rule_opens_in, profile.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.padding(end = PANEL_ICON_INSET)) {
            Icon(Icons.Filled.Delete, stringResource(R.string.delete_rule_for, rule.pattern.text))
        }
    }
}

/**
 * Adds a Site Rule ([rule] null) or changes one: the site, and the Profile its links open in.
 * Under the field, the dialog says in words what the pattern typed so far covers, or that it
 * replaces another rule. [pageSite], the site of the page in the shown Tab, fills the field with
 * one tap.
 */
@Composable
fun SiteRuleEditorDialog(
    rule: SiteRule?,
    rules: List<SiteRule>,
    profiles: List<Profile>,
    preselected: Profile,
    pageSite: SitePattern?,
    onSave: (SitePattern, Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(rule?.pattern?.text.orEmpty()) }
    var target by remember { mutableStateOf(profiles.firstOrNull { it.id == rule?.profileId } ?: preselected) }
    val pattern = SitePattern.parse(text)
    val replaced = rules.firstOrNull { it.pattern == pattern && it.id != rule?.id }
    val replacedProfile = profiles.firstOrNull { it.id == replaced?.profileId }
    // A new rule starts with its site: the field has the focus, and the keyboard is up.
    val siteField = remember { FocusRequester() }
    if (rule == null) LaunchedEffect(Unit) { siteField.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // As the Profile editor: laid out in the whole window, clear of the system bars and the
        // keyboard, with the choices scrolling in what is left.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp).testTag("site-rule-editor"),
        title = { Text(stringResource(if (rule == null) R.string.new_site_rule else R.string.edit_site_rule)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    // Kept whole, however long: cut short, a pasted address would make a rule for
                    // more than it names. What is too long is refused below.
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.rule_site)) },
                    placeholder = { Text(stringResource(R.string.rule_site_placeholder)) },
                    isError = text.isNotBlank() && pattern == null,
                    supportingText = {
                        Text(
                            when {
                                text.isBlank() -> stringResource(R.string.rule_site_help)
                                pattern == null && text.trim().length > SitePattern.MAX_LENGTH ->
                                    pluralStringResource(R.plurals.rule_too_long, SitePattern.MAX_LENGTH, SitePattern.MAX_LENGTH)
                                pattern == null -> stringResource(R.string.rule_not_a_site)
                                replaced != null && replacedProfile != null ->
                                    stringResource(R.string.rule_replaces, pattern.text, replacedProfile.name)
                                '*' in pattern.text -> stringResource(R.string.rule_covers_star, pattern.text)
                                pattern.path.isNotEmpty() -> stringResource(R.string.rule_covers_path, pattern.text)
                                else -> stringResource(R.string.rule_covers_site, pattern.text)
                            },
                            modifier = Modifier.testTag("rule-site-explanation"),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = WEB_ADDRESS_KEYBOARD,
                    modifier = Modifier.fillMaxWidth().focusRequester(siteField).testTag("rule-site-field"),
                )
                // Not for a site that has its rule already: that rule is in the list, to be changed there.
                if (pageSite != null && pageSite != pattern && rules.none { it.pattern == pageSite }) {
                    AssistChip(
                        onClick = { text = pageSite.text },
                        label = { Text(stringResource(R.string.rule_use_page_site, pageSite.text), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                        modifier = Modifier.testTag("rule-use-page-site"),
                    )
                }
                Text(
                    stringResource(R.string.rule_open_in),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                // Rows reach 8dp past the field on both sides, so each avatar's 32dp circle starts
                // on the field's edge and each radio button ends on it, as in the Profile picker.
                Column(Modifier.bleed(AVATAR_INSET).selectableGroup()) {
                    profiles.forEach { profile ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = profile.id == target.id, role = Role.RadioButton) { target = profile }
                                .testTag("rule-profile-${profile.id.value}"),
                        ) {
                            ProfileAvatar(profile)
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                            )
                            RadioButton(selected = profile.id == target.id, onClick = null, modifier = Modifier.padding(end = AVATAR_INSET))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pattern!!, target) }, enabled = pattern != null, modifier = Modifier.testTag("save-site-rule")) {
                Text(stringResource(if (rule == null) R.string.add_rule else R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A keyboard for typing an address: no capitals, no corrections. */
private val WEB_ADDRESS_KEYBOARD = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Done,
)

/** A rule's avatar slot starts here, so its 32dp circle starts on the panel's text line. */
private val RULE_AVATAR_INSET = PANEL_TEXT_INSET - 8.dp
