package app.jrny.browser.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.download.Download
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadStatus
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Visit
import app.jrny.browser.tab.displayHost
import kotlinx.coroutines.delay

/** A list that takes the page's place: the Active Profile's history, bookmarks or downloads. */
enum class Panel { History, Bookmarks, Downloads }

/** The Active Profile's history, bookmarks and downloads, and what can be done with them. */
class LibraryUi(
    val visits: List<Visit> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    /** Bookmarks the address with that title, or removes its bookmark. */
    val toggleBookmark: (url: String, title: String) -> Unit = { _, _ -> },
    val deleteBookmark: (Bookmark) -> Unit = {},
    val deleteVisit: (Visit) -> Unit = {},
    val clearHistory: () -> Unit = {},
    val downloads: List<Download> = emptyList(),
    /** Starts the download the person agreed to. */
    val startDownload: (DownloadRequest) -> Unit = {},
    /** Where each of the downloads stands, by its list id. */
    val downloadStatuses: suspend (List<Download>) -> Map<Long, DownloadStatus> = { emptyMap() },
    /** Opens a finished download. False when nothing on the device can show it. */
    val openDownload: (Download) -> Boolean = { false },
    /** Takes a download off the list, stopping it first if it is still [running]. */
    val removeDownload: (Download, running: Boolean) -> Unit = { _, _ -> },
) {
    fun isBookmarked(url: String?): Boolean = url != null && bookmarks.any { it.url == url }
}

/**
 * The history or the bookmarks of [profileName], in the page's place. Choosing a row opens its
 * address in the shown Tab ([onOpen]) and closes the panel.
 *
 * The content is one column, at most [PANEL_MAX_WIDTH] wide and centered, so rows stay readable
 * on a tablet. In it, text starts [PANEL_TEXT_INSET] in and each row's icon button ends
 * [PANEL_ICON_INSET] in, the same lines as a Tab row. [contentPadding] keeps the list clear of
 * what the layout has at the bottom edge.
 */
@Composable
fun LibraryPanel(
    panel: Panel,
    library: LibraryUi,
    profileName: String,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmClear by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxSize().testTag("panel-${panel.name}")) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = PANEL_MAX_WIDTH).padding(horizontal = PANEL_GUTTER)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = PANEL_TEXT_INSET, end = PANEL_ICON_INSET),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                when (panel) {
                                    Panel.History -> R.string.history
                                    Panel.Bookmarks -> R.string.bookmarks
                                    Panel.Downloads -> R.string.downloads
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(profileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (panel == Panel.History && library.visits.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("clear-history")) {
                            Text(stringResource(R.string.clear_history))
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close-panel")) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close))
                    }
                }
                when (panel) {
                    Panel.History -> PanelList(
                        items = library.visits,
                        key = { it.id },
                        emptyText = R.string.history_empty,
                        contentPadding = contentPadding,
                    ) { visit ->
                        PanelRow(
                            title = visit.title,
                            url = visit.url,
                            detail = visitTime(visit.visitedAt),
                            deleteLabel = R.string.delete_from_history,
                            onOpen = { onOpen(visit.url) },
                            onDelete = { library.deleteVisit(visit) },
                        )
                    }
                    Panel.Bookmarks -> PanelList(
                        items = library.bookmarks,
                        key = { it.id },
                        emptyText = R.string.bookmarks_empty,
                        contentPadding = contentPadding,
                    ) { bookmark ->
                        PanelRow(
                            title = bookmark.title,
                            url = bookmark.url,
                            detail = null,
                            deleteLabel = R.string.delete_bookmark,
                            onOpen = { onOpen(bookmark.url) },
                            onDelete = { library.deleteBookmark(bookmark) },
                        )
                    }
                    Panel.Downloads -> DownloadList(library, contentPadding)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title, profileName)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        library.clearHistory()
                        confirmClear = false
                    },
                    modifier = Modifier.testTag("confirm-clear-history"),
                ) { Text(stringResource(R.string.clear_history), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun <T> PanelList(
    items: List<T>,
    key: (T) -> Any,
    emptyText: Int,
    contentPadding: PaddingValues,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            stringResource(emptyText),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_TEXT_INSET, vertical = 48.dp),
        )
    } else {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.testTag("panel-list")) {
            items(items, key = key) { row(it) }
        }
    }
}

/**
 * A page or file in a panel list: its title, then its site and [detail]. A tap opens it, unless
 * it isn't [enabled] yet, and the button at the end removes it.
 */
@Composable
private fun PanelRow(
    title: String,
    url: String,
    detail: String?,
    deleteLabel: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteIcon: ImageVector = Icons.Filled.Delete,
    enabled: Boolean = true,
) {
    val host = displayHost(url)
    val name = title.ifBlank { host.ifEmpty { url } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
            .testTag("panel-row"),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).padding(start = PANEL_TEXT_INSET, top = 8.dp, bottom = 8.dp),
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val second = listOfNotNull(host.takeIf { it.isNotEmpty() && it != name }, detail).joinToString(" · ")
            if (second.isNotEmpty()) {
                Text(
                    second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.padding(end = PANEL_ICON_INSET)) {
            Icon(deleteIcon, stringResource(deleteLabel, name))
        }
    }
}

/**
 * The Profile's downloads with where each stands, asked again every second while the list shows.
 * A tap opens a finished file; the row's button stops a running download or takes a finished one
 * off the list (the file stays on the device).
 */
@Composable
private fun DownloadList(library: LibraryUi, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var statuses by remember { mutableStateOf(emptyMap<Long, DownloadStatus>()) }
    var cantOpen by remember { mutableStateOf(false) }
    LaunchedEffect(library.downloads) {
        while (true) {
            statuses = library.downloadStatuses(library.downloads)
            // Nothing changes any more once every download has ended.
            if (statuses.values.none { it is DownloadStatus.Running }) break
            delay(1_000)
        }
    }
    PanelList(items = library.downloads, key = { it.id }, emptyText = R.string.downloads_empty, contentPadding = contentPadding) { download ->
        val status = statuses[download.id]
        val running = status is DownloadStatus.Running
        val state = when (status) {
            is DownloadStatus.Running ->
                status.fraction?.let { stringResource(R.string.download_running_percent, (it * 100).toInt()) } ?: stringResource(R.string.download_running)
            is DownloadStatus.Done -> Formatter.formatShortFileSize(context, status.bytes)
            DownloadStatus.Failed -> stringResource(R.string.download_failed)
            DownloadStatus.Gone -> stringResource(R.string.download_gone)
            null -> null
        }
        PanelRow(
            title = download.fileName,
            url = download.url,
            detail = listOfNotNull(state, visitTime(download.startedAt)).joinToString(" · "),
            deleteLabel = if (running) R.string.cancel_download else R.string.remove_download,
            deleteIcon = if (running) Icons.Filled.Close else Icons.Filled.Delete,
            enabled = status is DownloadStatus.Done,
            onOpen = { if (!library.openDownload(download)) cantOpen = true },
            onDelete = { library.removeDownload(download, running) },
        )
    }
    if (cantOpen) {
        AlertDialog(
            onDismissRequest = { cantOpen = false },
            text = { Text(stringResource(R.string.cant_open_file)) },
            confirmButton = { TextButton(onClick = { cantOpen = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** The day and time of a visit, in the device's own format. */
@Composable
private fun visitTime(millis: Long): String = DateUtils.formatDateTime(
    LocalContext.current,
    millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
)

/** The panel's column is at most this wide, centered. */
private val PANEL_MAX_WIDTH = 720.dp

/** Between the panel's column and the edges of its place. */
private val PANEL_GUTTER = 8.dp

/** Where text starts inside the column. */
private val PANEL_TEXT_INSET = 16.dp

/** Where a 48dp icon-button slot ends inside the column, so its icon ends 16dp in. */
private val PANEL_ICON_INSET = 4.dp
