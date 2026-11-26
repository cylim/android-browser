package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.backup.BackupFailure
import app.jrny.browser.backup.BackupStep
import app.jrny.browser.backup.BackupSummary
import app.jrny.browser.backup.ImportMode
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dialogs of a backup (SPEC, "Backup"): how it is protected, its password, and how it is imported. */
@RunWith(AndroidJUnit4::class)
class BackupUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val exported = mutableListOf<String?>()
    private val unlocked = mutableListOf<String>()
    private val imported = mutableListOf<ImportMode>()
    private var step: BackupStep? by mutableStateOf(null)

    private fun showExport() = compose.setContent {
        JrnyTheme { ExportBackupDialog(onExport = { exported += it?.concatToString() }, onDismiss = {}) }
    }

    private fun showStep(shown: BackupStep) {
        step = shown
        compose.setContent {
            JrnyTheme {
                BackupStepDialog(
                    BackupUi(
                        step = step,
                        unlock = { unlocked += it.concatToString() },
                        import = { imported += it },
                        dismiss = { step = null },
                    ),
                )
            }
        }
    }

    @Test
    fun aBackupIsProtectedWithAPasswordTypedTwiceOfAtLeastEightCharacters() {
        showExport()
        compose.onNodeWithTag("backup-encrypt").assertExists()
        compose.onNodeWithTag("confirm-export-backup").assertIsNotEnabled()
        compose.onNodeWithTag("backup-password").performTextReplacement("short")
        compose.onNodeWithTag("backup-password-again-error", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("At least 8 characters").assertExists()
        compose.onNodeWithTag("backup-password").performTextReplacement("long enough")
        compose.onNodeWithTag("backup-password-again").performTextReplacement("long enougH")
        compose.onNodeWithText("The passwords are different").assertExists()
        compose.onNodeWithTag("confirm-export-backup").assertIsNotEnabled()
        compose.onNodeWithTag("backup-password-again").performTextReplacement("long enough")
        compose.onNodeWithTag("backup-password-again-error", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("confirm-export-backup").assertIsEnabled().performClick()
        assertEquals(listOf<String?>("long enough"), exported)
    }

    @Test
    fun aPlainBackupSaysWhoCanReadIt() {
        showExport()
        compose.onNodeWithTag("backup-plain-warning").assertDoesNotExist()
        compose.onNodeWithTag("backup-encrypt").performClick()
        compose.onNodeWithTag("backup-password").assertDoesNotExist()
        compose.onNodeWithTag("backup-plain-warning").assertExists()
        compose.onNodeWithTag("confirm-export-backup").assertIsEnabled().performClick()
        assertEquals(listOf<String?>(null), exported)
    }

    @Test
    fun importingSaysWhatTheBackupHoldsAndOffersMergeFirst() {
        showStep(BackupStep.Ready(BackupSummary(listOf("Personal", "Work"), bookmarks = 12, visits = 1, tabs = 3)))
        compose.onNodeWithText("2 Profiles: Personal, Work").assertExists()
        compose.onNodeWithText("12 bookmarks, 1 visit, 3 open Tabs").assertExists()
        compose.onNodeWithTag("import-Merge").assertIsSelected()
        compose.onNodeWithTag("backup-replace-warning").assertDoesNotExist()
        compose.onNodeWithTag("confirm-import-backup").performClick()
        assertEquals(listOf(ImportMode.Merge), imported)
    }

    @Test
    fun replacingSaysWhatItDeletes() {
        showStep(BackupStep.Ready(BackupSummary(listOf("Personal"), bookmarks = 1, visits = 2, tabs = 1)))
        compose.onNodeWithText("1 Profile: Personal").assertExists()
        compose.onNodeWithTag("import-Replace").performClick()
        compose.onNodeWithTag("backup-replace-warning").performScrollTo().assertExists()
        compose.onNodeWithTag("confirm-import-backup").performClick()
        assertEquals(listOf(ImportMode.Replace), imported)
    }

    @Test
    fun anEncryptedBackupAsksForItsPasswordAndSaysWhenItIsWrong() {
        showStep(BackupStep.NeedsPassword())
        compose.onNodeWithTag("backup-wrong-password", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("confirm-unlock-backup").assertIsNotEnabled()
        compose.onNodeWithTag("backup-unlock-password").performTextReplacement("guess")
        compose.onNodeWithTag("confirm-unlock-backup").performClick()
        assertEquals(listOf("guess"), unlocked)
        step = BackupStep.NeedsPassword(wrong = true)
        compose.onNodeWithTag("backup-wrong-password", useUnmergedTree = true).assertExists()
    }

    @Test
    fun workUnderWayCantBeDismissedAndAFailureSaysWhy() {
        showStep(BackupStep.Importing)
        compose.onNodeWithTag("backup-working").assertExists()
        compose.onNodeWithText("Importing the backup…").assertExists()
        step = BackupStep.Failed(BackupFailure.TooNew)
        compose.onNodeWithTag("backup-failed").assertExists()
        compose.onNodeWithText("A newer version of Okapi made this backup. Update Okapi to import it.").assertExists()
        compose.onNodeWithTag("backup-ok").performClick()
        compose.waitForIdle()
        assertNull(step)
    }
}
