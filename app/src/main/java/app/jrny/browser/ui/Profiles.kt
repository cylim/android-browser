package app.jrny.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.jrny.browser.R
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileEmoji
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileLook
import app.jrny.browser.profile.ProfileTint
import app.jrny.browser.profile.ProfileName

/** Everything the layouts need to show and change Profiles. */
class ProfilesUi(
    val all: List<Profile>,
    val active: Profile,
    /** Deleted Profiles whose web data is wiped at the next cold start (SPEC, "Profiles"). */
    val pendingDeletions: Int,
    val switchTo: (Profile) -> Unit,
    /** Opens the Profile editor: for [Profile], to change it; for null, to create one. */
    val edit: (Profile?) -> Unit,
) {
    /** The Profile after the active one, or before it with [step] -1, for swiping. Null at either end. */
    fun neighbour(step: Int): Profile? {
        // By id: a rename can reach the list before it reaches the Active Profile.
        val index = all.indexOfFirst { it.id == active.id }
        return if (index < 0) null else all.getOrNull(index + step)
    }
}

/**
 * The Profiles to switch between, each with an edit button, then "New Profile". Shared by the
 * Sidebar's menu and the Bottom Bar's Profile sheet. Rows are 56dp; avatars sit in 48dp slots
 * 4dp in, so they line up with the avatars around them. [onDone] runs after any choice.
 */
@Composable
fun ProfileList(profiles: ProfilesUi, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag("profile-list")) {
        profiles.all.forEach { profile ->
            val active = profile.id == profiles.active.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { selected = active }
                    .clickable(role = Role.Button) {
                        profiles.switchTo(profile)
                        onDone()
                    }
                    .padding(start = 4.dp, end = 4.dp)
                    .testTag("profile-row-${profile.id.value}"),
            ) {
                ProfileAvatar(profile)
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (active) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.active_profile),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                IconButton(onClick = { profiles.edit(profile); onDone() }) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.edit_profile_named, profile.name))
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(role = Role.Button) {
                    profiles.edit(null)
                    onDone()
                }
                .padding(start = 4.dp, end = 16.dp)
                .testTag("new-profile"),
        ) {
            Box(Modifier.size(AVATAR_SLOT), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
            Text(stringResource(R.string.new_profile), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
        }
        if (profiles.pendingDeletions > 0) {
            Text(
                pluralStringResource(R.plurals.profiles_pending_removal, profiles.pendingDeletions, profiles.pendingDeletions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("pending-removal"),
            )
        }
    }
}

/**
 * Creates a Profile ([profile] null) or changes one: its name and its look. The look is the
 * Profile's theme: its color, how strongly that colors the browser, and an icon or an emoji for
 * its avatar. A Profile being changed can also be deleted, unless it's the last one ([canDelete]
 * false).
 */
@Composable
fun ProfileEditorDialog(
    profile: Profile?,
    canDelete: Boolean,
    onSave: (name: String, look: ProfileLook) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var look by remember { mutableStateOf(profile?.look ?: ProfileLook()) }
    // What is typed for an emoji of one's own. The avatar takes its first emoji.
    var typedEmoji by remember { mutableStateOf(profile?.emoji?.takeIf { it !in ProfileEmoji.suggested }.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cleanName = ProfileName.clean(name)

    @Composable
    fun Heading(text: Int) = Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        // The dialog lays itself out in the whole window and keeps clear of the system bars and
        // the keyboard itself. Left to the platform, a landscape tablet's keyboard covers the
        // lower half of the dialog, Create and Cancel included. The choices scroll in what is left.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp).testTag("profile-editor"),
        icon = { ProfileBadge(look.color, look.icon, 56.dp, look.emoji) },
        title = { Text(stringResource(if (profile == null) R.string.new_profile else R.string.edit_profile)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileName.MAX_LENGTH) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile-name-field"),
                )
                Heading(R.string.profile_color)
                ChoiceGrid(ProfileColor.entries) { option ->
                    Choice(
                        selected = option == look.color,
                        label = stringResource(R.string.profile_color_option, option.name),
                        onClick = { look = look.copy(color = option) },
                        modifier = Modifier.testTag("color-${option.name}"),
                    ) { ProfileBadge(option, look.icon, CHOICE_BADGE, look.emoji) }
                }
                Heading(R.string.profile_tint)
                // How much of the color the Sidebar takes, shown as it will look.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    ProfileTint.entries.forEach { option ->
                        val label = stringResource(
                            when (option) {
                                ProfileTint.Soft -> R.string.profile_tint_soft
                                ProfileTint.Medium -> R.string.profile_tint_medium
                                ProfileTint.Strong -> R.string.profile_tint_strong
                            },
                        )
                        val selected = option == look.tint
                        Surface(
                            color = profileTint(look.color, option),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = selected, role = Role.RadioButton) { look = look.copy(tint = option) }
                                .testTag("tint-${option.name}"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                }
                Heading(R.string.profile_icon)
                ChoiceGrid(ProfileIcon.entries) { option ->
                    Choice(
                        selected = look.emoji == null && option == look.icon,
                        label = stringResource(R.string.profile_icon_option, option.name),
                        onClick = {
                            look = look.copy(icon = option, emoji = null)
                            typedEmoji = ""
                        },
                        modifier = Modifier.testTag("icon-${option.name}"),
                    ) { ProfileBadge(look.color, option, CHOICE_BADGE) }
                }
                Heading(R.string.profile_emoji)
                ChoiceGrid(ProfileEmoji.suggested) { option ->
                    Choice(
                        selected = look.emoji == option,
                        label = stringResource(R.string.profile_emoji_option, option),
                        onClick = {
                            look = look.copy(emoji = option)
                            typedEmoji = ""
                        },
                        modifier = Modifier.testTag("emoji-$option"),
                    ) { ProfileBadge(look.color, look.icon, CHOICE_BADGE, option) }
                }
                OutlinedTextField(
                    value = typedEmoji,
                    onValueChange = { typed ->
                        typedEmoji = typed.take(ProfileEmoji.MAX_LENGTH)
                        // Cleared, the avatar goes back to its icon. Plain text changes nothing.
                        val emoji = ProfileEmoji.clean(typed)
                        if (emoji != null || typed.isBlank()) look = look.copy(emoji = emoji)
                    },
                    label = { Text(stringResource(R.string.profile_own_emoji)) },
                    supportingText = if (typedEmoji.isNotBlank() && ProfileEmoji.clean(typedEmoji) == null) {
                        { Text(stringResource(R.string.profile_own_emoji_not_one)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("profile-emoji-field"),
                )
                if (profile != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = canDelete,
                        modifier = Modifier.testTag("delete-profile"),
                    ) {
                        Text(stringResource(R.string.delete_profile), color = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!canDelete) {
                        Text(
                            stringResource(R.string.last_profile_cannot_be_deleted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(cleanName!!, look) },
                enabled = cleanName != null,
                modifier = Modifier.testTag("save-profile"),
            ) { Text(stringResource(if (profile == null) R.string.create else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (confirmDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_profile_title, profile.name)) },
            text = { Text(stringResource(R.string.delete_profile_body, profile.name)) },
            confirmButton = {
                TextButton(onClick = onDelete, modifier = Modifier.testTag("confirm-delete")) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The choices in rows of [columns], each in a cell of the same width, so the colors, icons and
 * emoji form one grid whatever the dialog's width. A last row with fewer keeps the cells' places.
 */
@Composable
private fun <T> ChoiceGrid(options: List<T>, columns: Int = 6, choice: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { option -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { choice(option) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The badge inside a choice: 4dp inside the choice's ring. */
private val CHOICE_BADGE = 36.dp

/** A 44dp choice with a ring when selected. */
@Composable
private fun Choice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) { content() }
}
