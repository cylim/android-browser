package app.jrny.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import app.jrny.browser.R
import app.jrny.browser.engine.ContentBlocker

/**
 * uBlock Origin's popup for the shown Tab, in a sheet over the page (SPEC, "Content blocking").
 * The sheet doesn't follow drags, which belong to the popup's own page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentBlockerPopup(private: Boolean) {
    val popup = ContentBlocker.popup ?: return
    ModalBottomSheet(
        onDismissRequest = ContentBlocker::closePopup,
        // The sheet is a window of its own: over a Private Tab it can't be captured either.
        properties = ModalBottomSheetProperties(securePolicy = if (private) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        modifier = Modifier.testTag("content-blocker-popup"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.content_blocker_name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.content_blocker_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        SessionHost(popup, Modifier.fillMaxWidth().fillMaxHeight(POPUP_HEIGHT))
    }
}

/**
 * uBlock Origin's dashboard, or another of its pages, in the page's place. Back shows the page it
 * was opened from, and Close closes them all.
 */
@Composable
fun ContentBlockerPanel(contentPadding: PaddingValues) {
    val page = ContentBlocker.pages.lastOrNull() ?: return
    PanelFrame(
        tag = "panel-ContentBlocker",
        title = stringResource(R.string.content_blocker_name),
        subtitle = stringResource(R.string.content_blocker_note),
        onClose = ContentBlocker::closePages,
        onBack = ContentBlocker::closePage,
    ) {
        SessionHost(page, Modifier.padding(contentPadding))
    }
}

/**
 * What uBlock Origin's pages ask: their dialogs, as a page's are shown; a file to import, from
 * Android's file picker; and where to save a file they made, such as a backup.
 */
@Composable
fun ContentBlockerAsks() {
    PagePrompts(ContentBlocker.prompt, ContentBlocker::closePrompt)
    // Its file fields take text and JSON files: lists of filters, trusted sites and backups.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { ContentBlocker.answerFileField(it) }
    val field = ContentBlocker.fileField
    LaunchedEffect(field) { if (field != null) pick.launch(arrayOf("text/plain", "application/json")) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { ContentBlocker.saveFile(it) }
    val file = ContentBlocker.fileToSave
    LaunchedEffect(file) { if (file != null) save.launch(file.fileName) }
}

/** How much of the window the popup's page takes. */
private const val POPUP_HEIGHT = 0.7f
