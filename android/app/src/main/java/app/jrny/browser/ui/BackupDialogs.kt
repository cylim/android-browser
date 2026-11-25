package app.jrny.browser.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.jrny.browser.R
import app.jrny.browser.backup.BackupFailure
import app.jrny.browser.backup.BackupFile
import app.jrny.browser.backup.BackupStep
import app.jrny.browser.backup.BackupSummary
import app.jrny.browser.backup.ImportMode

/** Backups of the app's data, and how far one has got (SPEC, "Backup"). */
class BackupUi(
    val step: BackupStep? = null,
    /** Writes a backup to the chosen file, encrypted with the password, or plain without one. */
    val export: (Uri, CharArray?) -> Unit = { _, _ -> },
    /** Reads the chosen backup file. */
    val open: (Uri) -> Unit = {},
    val unlock: (CharArray) -> Unit = {},
    val import: (ImportMode) -> Unit = {},
    val dismiss: () -> Unit = {},
)

/**
 * Asks how to protect a backup before the person chooses where it goes: with a password, typed
 * twice, which is on at first, or as plain JSON, which says who can read it then.
 */
@Composable
fun ExportBackupDialog(onExport: (password: CharArray?) -> Unit, onDismiss: () -> Unit) {
    var encrypt by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val tooShort = password.length < BackupFile.MIN_PASSWORD_LENGTH
    val differ = again != password
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("export-backup-dialog"),
        title = { Text(stringResource(R.string.export_backup)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.export_backup_body), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = encrypt, role = Role.Switch) { encrypt = it }
                        .testTag("backup-encrypt"),
                ) {
                    Text(stringResource(R.string.backup_encrypt), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = encrypt, onCheckedChange = null)
                }
                if (encrypt) {
                    PasswordField(password, { password = it }, R.string.password, "backup-password", ImeAction.Next, Modifier.padding(top = 8.dp))
                    PasswordField(
                        again,
                        { again = it },
                        R.string.backup_password_again,
                        "backup-password-again",
                        ImeAction.Done,
                        Modifier.padding(top = 8.dp),
                        error = when {
                            password.isNotEmpty() && tooShort -> pluralStringResource(R.plurals.backup_password_too_short, BackupFile.MIN_PASSWORD_LENGTH, BackupFile.MIN_PASSWORD_LENGTH)
                            again.isNotEmpty() && differ -> stringResource(R.string.backup_passwords_differ)
                            else -> null
                        },
                    )
                    Text(
                        stringResource(R.string.backup_encrypt_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.backup_plain_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).testTag("backup-plain-warning"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(if (encrypt) password.toCharArray() else null) },
                enabled = !encrypt || (!tooShort && !differ),
                modifier = Modifier.testTag("confirm-export-backup"),
            ) { Text(stringResource(R.string.backup_choose_place)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    tag: String,
    ime: ImeAction,
    modifier: Modifier = Modifier,
    error: String? = null,
    errorTag: String = "$tag-error",
    autofill: ContentType = ContentType.NewPassword,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it, modifier = Modifier.testTag(errorTag)) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime, autoCorrectEnabled = false),
        modifier = modifier.fillMaxWidth().semantics { contentType = autofill }.testTag(tag),
    )
}

/** What the window shows of a backup under way: one dialog for each [BackupStep]. */
@Composable
fun BackupStepDialog(ui: BackupUi) {
    when (val step = ui.step ?: return) {
        BackupStep.Exporting -> Working(R.string.backup_exporting)
        BackupStep.Reading -> Working(R.string.backup_reading)
        BackupStep.Importing -> Working(R.string.backup_importing)
        BackupStep.Exported -> Done(R.string.backup_exported, R.string.backup_exported_body, ui.dismiss)
        is BackupStep.Imported -> Done(
            R.string.backup_imported,
            if (step.mode == ImportMode.Replace) R.string.backup_replaced_body else R.string.backup_merged_body,
            ui.dismiss,
        )
        is BackupStep.Failed -> Done(R.string.backup_failed, step.failure.message, ui.dismiss, tag = "backup-failed")
        is BackupStep.NeedsPassword -> UnlockDialog(step.wrong, ui)
        is BackupStep.Ready -> ImportDialog(step.summary, ui)
    }
}

private val BackupFailure.message: Int
    get() = when (this) {
        BackupFailure.CantWrite -> R.string.backup_cant_write
        BackupFailure.CantRead -> R.string.backup_cant_read
        BackupFailure.CantImport -> R.string.backup_cant_import
        BackupFailure.NotABackup -> R.string.backup_not_a_backup
        BackupFailure.TooNew -> R.string.backup_too_new
        BackupFailure.TooLarge -> R.string.backup_too_large
        BackupFailure.TooLargeToSave -> R.string.backup_too_large_to_save
    }

/** Work that takes a moment: deriving the key takes about a second. It can't be dismissed. */
@Composable
private fun Working(text: Int) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag("backup-working"),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun Done(title: Int, body: Int, onDismiss: () -> Unit, tag: String = "backup-done") {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(tag),
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup-ok")) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun UnlockDialog(wrong: Boolean, ui: BackupUi) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = ui.dismiss,
        modifier = Modifier.testTag("backup-password-dialog"),
        title = { Text(stringResource(R.string.backup_needs_password)) },
        text = {
            PasswordField(
                password,
                { password = it },
                R.string.password,
                "backup-unlock-password",
                ImeAction.Done,
                error = if (wrong) stringResource(R.string.backup_wrong_password) else null,
                errorTag = "backup-wrong-password",
                autofill = ContentType.Password,
            )
        },
        confirmButton = {
            TextButton(onClick = { ui.unlock(password.toCharArray()) }, enabled = password.isNotEmpty(), modifier = Modifier.testTag("confirm-unlock-backup")) {
                Text(stringResource(R.string.backup_open))
            }
        },
        dismissButton = { TextButton(onClick = ui.dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Says what the backup holds and asks how to import it: merging, chosen at first, or replacing,
 * which says what it deletes.
 */
@Composable
private fun ImportDialog(summary: BackupSummary, ui: BackupUi) {
    var mode by remember { mutableStateOf(ImportMode.Merge) }
    AlertDialog(
        onDismissRequest = ui.dismiss,
        modifier = Modifier.testTag("import-backup-dialog"),
        title = { Text(stringResource(R.string.import_backup)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    pluralStringResource(R.plurals.backup_profiles, summary.profiles.size, summary.profiles.size, summary.profiles.joinToString(", ")),
                    modifier = Modifier.testTag("backup-summary"),
                )
                Text(
                    listOf(
                        pluralStringResource(R.plurals.backup_bookmarks, summary.bookmarks, summary.bookmarks),
                        pluralStringResource(R.plurals.backup_visits, summary.visits, summary.visits),
                        pluralStringResource(R.plurals.backup_tabs, summary.tabs, summary.tabs),
                    ).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(Modifier.selectableGroup()) {
                    ImportMode.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = mode == option, role = Role.RadioButton) { mode = option }
                                .padding(vertical = 8.dp)
                                .testTag("import-${option.name}"),
                        ) {
                            RadioButton(selected = mode == option, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(
                                    stringResource(if (option == ImportMode.Merge) R.string.backup_merge else R.string.backup_replace),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    stringResource(if (option == ImportMode.Merge) R.string.backup_merge_explanation else R.string.backup_replace_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (mode == ImportMode.Replace) {
                    Text(
                        stringResource(R.string.backup_replace_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).testTag("backup-replace-warning"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ui.import(mode) }, modifier = Modifier.testTag("confirm-import-backup")) {
                Text(stringResource(if (mode == ImportMode.Replace) R.string.backup_replace else R.string.backup_import))
            }
        },
        dismissButton = { TextButton(onClick = ui.dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
