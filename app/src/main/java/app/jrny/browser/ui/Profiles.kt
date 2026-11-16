package app.jrny.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import app.jrny.browser.profile.ProfileIcon
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
 * Creates a Profile ([profile] null) or changes one: name, color and icon. A Profile being
 * changed can also be deleted, unless it's the last one ([canDelete] false).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorDialog(
    profile: Profile?,
    canDelete: Boolean,
    onSave: (name: String, color: ProfileColor, icon: ProfileIcon) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var color by remember { mutableStateOf(profile?.color ?: ProfileColor.Teal) }
    var icon by remember { mutableStateOf(profile?.icon ?: ProfileIcon.Person) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cleanName = ProfileName.clean(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        // The dialog lays itself out in the whole window and keeps clear of the system bars and
        // the keyboard itself. Left to the platform, a landscape tablet's keyboard covers the
        // lower half of the dialog, Create and Cancel included. The choices scroll in what is left.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp).testTag("profile-editor"),
        icon = { ProfileBadge(color, icon, 56.dp) },
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
                Text(
                    stringResource(R.string.profile_color),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileColor.entries.forEach { option ->
                        Choice(
                            selected = option == color,
                            label = stringResource(R.string.profile_color_option, option.name),
                            onClick = { color = option },
                            modifier = Modifier.testTag("color-${option.name}"),
                        ) { ProfileBadge(option, icon, 40.dp) }
                    }
                }
                Text(
                    stringResource(R.string.profile_icon),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileIcon.entries.forEach { option ->
                        Choice(
                            selected = option == icon,
                            label = stringResource(R.string.profile_icon_option, option.name),
                            onClick = { icon = option },
                            modifier = Modifier.testTag("icon-${option.name}"),
                        ) { ProfileBadge(color, option, 40.dp) }
                    }
                }
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
                onClick = { onSave(cleanName!!, color, icon) },
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

/** A 48dp choice with a ring when selected. */
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
            .size(48.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) { content() }
}
