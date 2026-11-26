package app.jrny.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import app.jrny.browser.R
import app.jrny.browser.engine.ExtensionAsk
import app.jrny.browser.engine.ExtensionNotice
import app.jrny.browser.engine.ExtensionPermissions
import app.jrny.browser.engine.Extensions
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtension.InstallException.ErrorCodes
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * An extension's popup for the shown Tab, in a sheet over the page (SPEC, "Extensions"). The
 * sheet doesn't follow drags, which belong to the popup's own page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPopup(private: Boolean) {
    val popup = Extensions.popup ?: return
    ModalBottomSheet(
        onDismissRequest = Extensions::closePopup,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        // The sheet is a window of its own: over a Private Tab it can't be captured either.
        properties = ModalBottomSheetProperties(securePolicy = if (private) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit),
        modifier = Modifier.testTag("extension-popup"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(nameOf(popup.extension), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.extension_page_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        SessionHost(popup.session, Modifier.fillMaxWidth().fillMaxHeight(POPUP_HEIGHT))
    }
}

/**
 * An extension's own page, such as its settings, in the page's place. Back shows the page it was
 * opened from, and Close closes them all.
 */
@Composable
fun ExtensionPagePanel(contentPadding: PaddingValues) {
    val page = Extensions.pages.lastOrNull() ?: return
    PanelFrame(
        tag = "panel-ExtensionPage",
        title = nameOf(page.extension),
        subtitle = stringResource(R.string.extension_page_note),
        onClose = Extensions::closePages,
        onBack = Extensions::closePage,
    ) {
        SessionHost(page.session, Modifier.padding(contentPadding))
    }
}

/** Every installed extension, with a switch each, and ways to find, add and update them (SPEC, "Extensions"). */
@Composable
fun ExtensionsPanel(
    onOpen: (WebExtension) -> Unit,
    onFindMore: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues,
) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        if (file != null) Extensions.installFile(file)
    }
    PanelFrame(
        tag = "panel-Extensions",
        title = stringResource(R.string.extensions),
        subtitle = null,
        onBack = onBack,
        onClose = onClose,
        action = {
            TextButton(onClick = { Extensions.checkUpdates() }, enabled = !Extensions.checkingUpdates, modifier = Modifier.testTag("check-extension-updates")) {
                Text(stringResource(if (Extensions.checkingUpdates) R.string.checking_for_updates else R.string.check_for_updates))
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(contentPadding)) {
            Text(
                stringResource(R.string.extensions_every_profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp),
            )
            Link(R.string.find_extensions, stringResource(R.string.find_extensions_explanation), "", "find-extensions", onFindMore)
            Link(R.string.install_from_file, stringResource(R.string.install_from_file_explanation), "", "install-extension-file") {
                pickFile.launch(arrayOf("application/x-xpinstall", "application/zip", "application/octet-stream"))
            }
            Section(R.string.extensions)
            if (Extensions.installed.isEmpty()) {
                Text(
                    stringResource(R.string.extensions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 12.dp),
                )
            }
            Extensions.installed.forEach { extension -> ExtensionRow(extension, onOpen = { onOpen(extension) }) }
        }
    }
}

@Composable
private fun ExtensionRow(extension: WebExtension, onOpen: () -> Unit) {
    val on = extension.metaData.enabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp)
            .testTag("extension-${extension.id}"),
    ) {
        ExtensionIcon(extension, 32.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(nameOf(extension), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (on) stringResource(R.string.version, extension.metaData.version.orEmpty()) else stringResource(R.string.extension_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = on,
            onCheckedChange = { Extensions.setEnabled(extension, it) },
            modifier = Modifier.testTag("extension-switch-${extension.id}"),
        )
    }
}

/** One extension: what it is, what it can do, and its switches, settings and removal. */
@Composable
fun ExtensionPanel(id: String, onBack: () -> Unit, onClose: () -> Unit, contentPadding: PaddingValues) {
    val extension = Extensions.installed.firstOrNull { it.id == id }
    // Removed meanwhile, here or by the engine: back to the list.
    LaunchedEffect(extension == null) { if (extension == null) onBack() }
    extension ?: return
    val meta = extension.metaData
    var confirmRemove by remember(id) { mutableStateOf(false) }
    PanelFrame(
        tag = "panel-Extension",
        title = nameOf(extension),
        subtitle = stringResource(R.string.version, meta.version.orEmpty()),
        onBack = onBack,
        onClose = onClose,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(contentPadding)) {
            Row(Modifier.padding(horizontal = PANEL_TEXT_INSET, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                ExtensionIcon(extension, 48.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    meta.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    meta.creatorName?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.extension_by, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Toggle(R.string.extension_on, stringResource(R.string.extension_page_note), meta.enabled, "extension-enabled") { Extensions.setEnabled(extension, it) }
            Toggle(R.string.extension_private_tabs, stringResource(R.string.extension_private_tabs_explanation), meta.allowedInPrivateBrowsing, "extension-private-tabs") {
                Extensions.setInPrivateTabs(extension, it)
            }
            if (meta.optionsPageUrl != null && meta.enabled) {
                Link(R.string.extension_settings, "", "", "extension-settings") { Extensions.openSettings(extension) }
            }
            Section(R.string.extension_permissions)
            PermissionList(
                ExtensionPermissions.of(
                    meta.requiredPermissions + meta.grantedOptionalPermissions,
                    meta.requiredOrigins + meta.grantedOptionalOrigins,
                    meta.requiredDataCollectionPermissions + meta.grantedOptionalDataCollectionPermissions,
                ),
                Modifier.padding(horizontal = PANEL_TEXT_INSET),
            )
            TextButton(
                onClick = { confirmRemove = true },
                modifier = Modifier.padding(horizontal = PANEL_ICON_INSET, vertical = 16.dp).testTag("extension-remove"),
            ) { Text(stringResource(R.string.extension_remove), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            modifier = Modifier.testTag("extension-remove-dialog"),
            title = { Text(stringResource(R.string.extension_remove_title, nameOf(extension))) },
            text = { Text(stringResource(R.string.extension_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    Extensions.remove(extension)
                }, modifier = Modifier.testTag("extension-remove-confirm")) { Text(stringResource(R.string.extension_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * What extensions ask: to be added, or allowed more; what their pages ask, as a page's dialogs;
 * a file to import, from Android's file picker; and where to save a file they made. Also what
 * the person should hear: an extension added or refused, and updates done.
 */
@Composable
fun ExtensionAsks() {
    PagePrompts(Extensions.prompt, Extensions::closePrompt)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { Extensions.answerFileField(it) }
    val field = Extensions.fileField
    LaunchedEffect(field) { if (field != null) pick.launch(arrayOf("text/plain", "application/json")) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { Extensions.saveFile(it) }
    val file = Extensions.fileToSave
    LaunchedEffect(file) { if (file != null) save.launch(file.fileName) }

    when (val ask = Extensions.ask) {
        is ExtensionAsk.Add -> AddDialog(ask)
        is ExtensionAsk.Allow -> AllowDialog(ask)
        null -> Unit
    }
    Extensions.notice?.let { NoticeDialog(it) }
}

@Composable
private fun AddDialog(ask: ExtensionAsk.Add) {
    var privateTabs by remember(ask) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { ask.answer(false, false) },
        modifier = Modifier.testTag("extension-add-dialog"),
        icon = { ExtensionIcon(ask.extension, 40.dp) },
        title = { Text(stringResource(R.string.add_extension_title, nameOf(ask.extension))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PermissionList(ask.permissions)
                Text(stringResource(R.string.extension_every_profile), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp).clickable { privateTabs = !privateTabs }.testTag("extension-add-private"),
                ) {
                    Checkbox(checked = privateTabs, onCheckedChange = null)
                    Text(stringResource(R.string.extension_private_tabs), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ask.answer(true, privateTabs) }, modifier = Modifier.testTag("extension-add-confirm")) { Text(stringResource(R.string.add_extension)) }
        },
        dismissButton = { TextButton(onClick = { ask.answer(false, false) }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AllowDialog(ask: ExtensionAsk.Allow) {
    val name = nameOf(ask.extension)
    AlertDialog(
        onDismissRequest = { ask.answer(false) },
        modifier = Modifier.testTag("extension-allow-dialog"),
        icon = { ExtensionIcon(ask.extension, 40.dp) },
        title = { Text(stringResource(if (ask.update) R.string.extension_update_title else R.string.extension_optional_title, name)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { PermissionList(ask.permissions) } },
        confirmButton = { TextButton(onClick = { ask.answer(true) }) { Text(stringResource(R.string.allow)) } },
        dismissButton = { TextButton(onClick = { ask.answer(false) }) { Text(stringResource(R.string.not_now)) } },
    )
}

@Composable
private fun NoticeDialog(notice: ExtensionNotice) {
    val (title, body) = when (notice) {
        is ExtensionNotice.Added -> stringResource(R.string.extension_added, nameOf(notice.extension)) to null
        is ExtensionNotice.Failed -> {
            val title = notice.name?.let { stringResource(R.string.extension_failed, it) } ?: stringResource(R.string.extension_failed_unnamed)
            title to stringResource(failureReason(notice.code))
        }
        is ExtensionNotice.Updated -> when {
            notice.count == 0 && notice.waiting == 0 -> stringResource(R.string.extensions_up_to_date) to null
            notice.count == 0 -> pluralStringResource(R.plurals.extensions_update_waiting, notice.waiting, notice.waiting) to null
            else -> pluralStringResource(R.plurals.extensions_updated, notice.count, notice.count) to
                notice.waiting.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.extensions_update_waiting, it, it) }
        }
        ExtensionNotice.UpdateCheckFailed -> stringResource(R.string.extensions_update_failed) to stringResource(R.string.extensions_update_failed_body)
    }
    AlertDialog(
        onDismissRequest = Extensions::dismissNotice,
        modifier = Modifier.testTag("extension-notice"),
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = Extensions::dismissNotice) { Text(stringResource(R.string.ok)) } },
    )
}

/** Why GeckoView refused an extension, in the person's words. */
private fun failureReason(code: Int?): Int = when (code) {
    ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED -> R.string.extension_failed_unsigned
    ErrorCodes.ERROR_INCOMPATIBLE, ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE, ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE -> R.string.extension_failed_incompatible
    ErrorCodes.ERROR_BLOCKLISTED, ErrorCodes.ERROR_SOFT_BLOCKED -> R.string.extension_failed_blocked
    ErrorCodes.ERROR_NETWORK_FAILURE, ErrorCodes.ERROR_INCORRECT_HASH -> R.string.extension_failed_network
    else -> R.string.extension_failed_file
}

/** What an extension can do, one line each: the sites first, then the permissions that matter. */
@Composable
private fun PermissionList(asked: ExtensionPermissions, modifier: Modifier = Modifier) {
    val lines = buildList {
        if (asked.allSites) add(stringResource(R.string.perm_all_sites))
        if (asked.sites.isNotEmpty()) add(stringResource(R.string.perm_sites, asked.sites.joinToString(", ")))
        asked.permissions.mapNotNull(PERMISSION_TEXT::get).distinct().forEach { add(stringResource(it)) }
    }
    val sends = asked.dataCollection.map { name -> COLLECTION_TEXT[name]?.let { stringResource(it) } ?: name }
    Column(modifier.testTag("extension-permissions")) {
        if (lines.isEmpty()) Text(stringResource(R.string.extension_asks_nothing), style = MaterialTheme.typography.bodyMedium)
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        if (sends.isNotEmpty()) {
            Text(stringResource(R.string.extension_sends, sends.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** An extension's own icon, or a puzzle piece until it has loaded or when it has none. */
@Composable
fun ExtensionIcon(extension: WebExtension, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, extension.id, extension.metaData.version, px) {
        value = extension.metaData.icon?.let { image ->
            suspendCancellableCoroutine { done -> image.getBitmap(px).accept({ done.resume(it?.asImageBitmap()) }, { done.resume(null) }) }
        }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val loaded = icon
        if (loaded != null) Image(loaded, contentDescription = null, modifier = Modifier.size(size)) else Icon(JrnyIcons.Extension, contentDescription = null)
    }
}

/** The name an extension gives itself, else its id. */
fun nameOf(extension: WebExtension): String = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id

/** Permissions Firefox warns about, in the person's words. The others aren't listed, as in Firefox. */
private val PERMISSION_TEXT = mapOf(
    "tabs" to R.string.perm_tabs,
    "history" to R.string.perm_history,
    "topSites" to R.string.perm_history,
    "bookmarks" to R.string.perm_bookmarks,
    "downloads" to R.string.perm_downloads,
    "downloads.open" to R.string.perm_downloads_open,
    "clipboardRead" to R.string.perm_clipboard_read,
    "clipboardWrite" to R.string.perm_clipboard_write,
    "geolocation" to R.string.perm_geolocation,
    "notifications" to R.string.perm_notifications,
    "nativeMessaging" to R.string.perm_native_messaging,
    "privacy" to R.string.perm_privacy,
    "proxy" to R.string.perm_proxy,
    "browsingData" to R.string.perm_browsing_data,
    "management" to R.string.perm_management,
    "sessions" to R.string.perm_sessions,
    "webNavigation" to R.string.perm_web_navigation,
    "declarativeNetRequest" to R.string.perm_declarative_net_request,
    "declarativeNetRequestWithHostAccess" to R.string.perm_declarative_net_request,
    "find" to R.string.perm_find,
    "tabHide" to R.string.perm_tab_hide,
)

/** What an extension may say it sends its developer, by Firefox's names. */
private val COLLECTION_TEXT = mapOf(
    "authenticationInfo" to R.string.collects_authentication,
    "bookmarksInfo" to R.string.collects_bookmarks,
    "browsingActivity" to R.string.collects_browsing_activity,
    "financialAndPaymentInfo" to R.string.collects_financial,
    "healthInfo" to R.string.collects_health,
    "locationInfo" to R.string.collects_location,
    "personalCommunications" to R.string.collects_communications,
    "personallyIdentifyingInfo" to R.string.collects_personal,
    "searchTerms" to R.string.collects_search_terms,
    "websiteActivity" to R.string.collects_website_activity,
    "websiteContent" to R.string.collects_website_content,
    "technicalAndInteraction" to R.string.collects_technical,
)

/** How much of the window the popup's page takes. */
private const val POPUP_HEIGHT = 0.7f
