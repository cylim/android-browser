package app.jrny.browser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.jrny.browser.R
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Folder
import app.jrny.browser.library.FolderName
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.displayHost

/**
 * The Active Profile's bookmarks as its Pinned Tabs, and what can be done with them (SPEC,
 * "Pinned Tabs").
 */
class PinsUi(
    /** The Profile's top level in order: bookmarks, and Folders with their bookmarks. */
    val items: List<PinnedItem> = emptyList(),
    /** Shows the bookmark as a Pinned Tab: its open Tab, or a new one that loads its address. */
    val open: (Bookmark) -> Unit = {},
    /** Pins the Tab: bookmarks its page and makes the Tab that bookmark's Pinned Tab. */
    val pin: (SavedTab) -> Unit = {},
    /** Removes the bookmark. Its open Tab stays, as an ordinary Tab. */
    val unpin: (Bookmark) -> Unit = {},
    /** Loads the bookmark's own address again in its open Tab, which has moved on to another page. */
    val reset: (Bookmark) -> Unit = {},
    val rename: (Bookmark, String) -> Unit = { _, _ -> },
    /** Moves the bookmark to the end of that Folder, or of the top level with null. */
    val move: (Bookmark, Folder?) -> Unit = { _, _ -> },
    /** Adds a Folder with that name and, with a bookmark, moves it there. */
    val addFolder: (name: String, first: Bookmark?) -> Unit = { _, _ -> },
    val renameFolder: (Folder, String) -> Unit = { _, _ -> },
    val setCollapsed: (Folder, Boolean) -> Unit = { _, _ -> },
    /** Deletes the Folder. Its bookmarks stay, at the top level. */
    val deleteFolder: (Folder) -> Unit = {},
    /** Puts the top level in this order. */
    val reorder: (List<PinnedItem>) -> Unit = {},
    /** Puts the bookmarks of one Folder in this order. */
    val reorderIn: (List<Bookmark>) -> Unit = {},
    /**
     * Shows the bookmark as a Pinned Tab from the shown Tab's Start Page: a closed one opens in
     * that Tab, so no empty Tab is left behind, and an open one is shown while that Tab closes.
     */
    val openHere: (Bookmark) -> Unit = open,
) {
    val folders: List<Folder> get() = items.filterIsInstance<PinnedItem.Group>().map { it.folder }

    /** Every bookmark listed, at the top level or in a Folder. */
    val bookmarks: List<Bookmark>
        get() = items.flatMap {
            when (it) {
                is PinnedItem.Page -> listOf(it.bookmark)
                is PinnedItem.Group -> it.folder.bookmarks
            }
        }
}

/** A bookmark's name in lists: its title, else its site. */
fun bookmarkLabel(bookmark: Bookmark): String = bookmark.title.ifBlank { displayHost(bookmark.url).ifEmpty { bookmark.url } }

/** A dialog the Pinned section has open. */
sealed interface PinDialog {
    data class Rename(val bookmark: Bookmark) : PinDialog
    data class Move(val bookmark: Bookmark) : PinDialog

    /** A new Folder, which [first] moves into right away. */
    data class NewFolder(val first: Bookmark?) : PinDialog
    data class RenameFolder(val folder: Folder) : PinDialog
}

/**
 * The label above a section of the Tab list ("Pinned", "Tabs"), as tall as a compact row when
 * rows are compact. Its text starts where the rows' marks do, and [action], a [ListActionButton],
 * sits in the rows' close-button column.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().heightIn(min = if (LocalCompactTabs.current) COMPACT_ROW_HEIGHT else 48.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = LIST_TEXT_INSET),
        )
        action()
    }
}

/**
 * A bookmark as a Pinned Tab. It is open while [openTab] shows it: then it has a close button,
 * which closes the Tab and keeps the bookmark. A closed one is dimmed, and a tap opens it. A long
 * press opens its menu: rename, move to a Folder, move up or down, unpin. Held and dragged, it
 * goes to another place in its list, into a Folder or out of one. [indent] sets a Folder's
 * bookmarks in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedRow(
    bookmark: Bookmark,
    openTab: SavedTab?,
    selected: Boolean,
    shape: Shape,
    selectedColor: Color,
    pins: PinsUi,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDialog: (PinDialog) -> Unit,
    indent: Dp = 0.dp,
    /** Moves the row one place up (-1) or down (1) in its list, or null at that end of it. */
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    /**
     * Makes the row one that can be held and dragged, given what opens its menu. Then holding it
     * opens the menu only when it is let go without having been dragged.
     */
    drag: ((openMenu: () -> Unit) -> Modifier)? = null,
    /** True while the row is held and follows the finger: it is drawn as a card above the others. */
    lifted: Boolean = false,
) {
    val label = bookmarkLabel(bookmark)
    var menu by remember { mutableStateOf(false) }
    val state = stringResource(if (openTab != null) R.string.pinned_open else R.string.pinned_closed)
    val moreActions = stringResource(R.string.more_actions)
    Box(drag?.invoke { menu = true } ?: Modifier) {
        Surface(
            color = if (selected || lifted) selectedColor else Color.Transparent,
            contentColor = if (selected || lifted) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
            shape = shape,
            shadowElevation = if (lifted) 6.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                // A screen reader's "long press" asks for the menu itself, with no finger to wait
                // for. Set before the click handling, so this is the action it finds.
                .semantics {
                    onLongClick(moreActions) {
                        menu = true
                        true
                    }
                }
                .combinedClickable(
                    role = Role.Tab,
                    onLongClickLabel = moreActions,
                    // With dragging, the hold is the drag's to answer: the menu waits for the finger to lift.
                    onLongClick = { if (drag == null) menu = true },
                    onClick = onOpen,
                )
                .semantics {
                    this.selected = selected
                    stateDescription = state
                }
                .testTag("pinned-row-${bookmark.id}"),
        ) {
            ListRow(height = if (LocalCompactTabs.current) COMPACT_ROW_HEIGHT else PINNED_ROW_HEIGHT) {
                SiteIcon(bookmark.url, Modifier.padding(start = LIST_TEXT_INSET + indent), size = listIconSize(), dimmed = openTab == null)
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (openTab == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = listIconGap(), end = if (openTab == null) LIST_TEXT_INSET else 0.dp),
                )
                if (openTab != null) {
                    CloseRowButton(stringResource(R.string.close_tab_named, label), onClose, Modifier.testTag("close-pinned"))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(12.dp)) {
            @Composable
            fun Item(text: Int, tag: String, icon: ImageVector? = null, onClick: () -> Unit) = DropdownMenuItem(
                text = { Text(stringResource(text)) },
                leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
                onClick = {
                    menu = false
                    onClick()
                },
                modifier = Modifier.testTag(tag),
            )
            // The Tab has moved on from the page that was pinned.
            if (openTab != null && openTab.url != bookmark.url) {
                Item(R.string.back_to_pinned_page, "pinned-reset") { pins.reset(bookmark) }
            }
            Item(R.string.rename, "pinned-rename") { onDialog(PinDialog.Rename(bookmark)) }
            Item(R.string.move_to_folder, "pinned-move", JrnyIcons.Folder) { onDialog(PinDialog.Move(bookmark)) }
            if (onMoveUp != null) Item(R.string.move_up, "pinned-up", Icons.Filled.KeyboardArrowUp, onMoveUp)
            if (onMoveDown != null) Item(R.string.move_down, "pinned-down", Icons.Filled.KeyboardArrowDown, onMoveDown)
            Item(R.string.unpin, "pinned-unpin", JrnyIcons.Pin) { pins.unpin(bookmark) }
        }
    }
}

/**
 * A Folder of Pinned Tabs. A tap shows or hides its bookmarks, and a long press opens its menu:
 * rename, move up or down, delete. Its icon sits where a row's site mark does, and tells open from closed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
    folder: Folder,
    shape: Shape,
    pins: PinsUi,
    onDialog: (PinDialog) -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    /** As for a Pinned Tab's row: makes the Folder one that can be held and dragged. */
    drag: ((openMenu: () -> Unit) -> Modifier)? = null,
    lifted: Boolean = false,
    /** True while a Pinned Tab is held over the Folder: let go, it goes in. */
    dropTarget: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val state = stringResource(if (folder.collapsed) R.string.folder_collapsed else R.string.folder_expanded)
    val moreActions = stringResource(R.string.more_actions)
    val background = when {
        dropTarget -> MaterialTheme.colorScheme.primaryContainer
        lifted -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }
    Box(drag?.invoke { menu = true } ?: Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (LocalCompactTabs.current) COMPACT_ROW_HEIGHT else PINNED_ROW_HEIGHT)
                .clip(shape)
                .background(background)
                .semantics {
                    onLongClick(moreActions) {
                        menu = true
                        true
                    }
                }
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = moreActions,
                    onLongClick = { if (drag == null) menu = true },
                    onClick = { pins.setCollapsed(folder, !folder.collapsed) },
                )
                .semantics { stateDescription = state }
                .testTag("folder-row-${folder.id}"),
        ) {
            Icon(
                if (folder.collapsed) JrnyIcons.Folder else JrnyIcons.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = LIST_TEXT_INSET).size(listIconSize()),
            )
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = listIconGap()),
            )
            // How many it holds, while they are hidden.
            if (folder.collapsed && folder.bookmarks.isNotEmpty()) {
                Text(
                    folder.bookmarks.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // Ends on the close icons' line.
            Box(Modifier.padding(end = LIST_TEXT_INSET))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(12.dp)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    menu = false
                    onDialog(PinDialog.RenameFolder(folder))
                },
                modifier = Modifier.testTag("folder-rename"),
            )
            if (onMoveUp != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    onClick = {
                        menu = false
                        onMoveUp()
                    },
                    modifier = Modifier.testTag("folder-up"),
                )
            }
            if (onMoveDown != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    onClick = {
                        menu = false
                        onMoveDown()
                    },
                    modifier = Modifier.testTag("folder-down"),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_folder)) },
                onClick = {
                    menu = false
                    pins.deleteFolder(folder)
                },
                modifier = Modifier.testTag("folder-delete"),
            )
        }
    }
}

/** Under an open Folder that holds nothing yet: how something gets there. */
@Composable
fun EmptyFolderHint() {
    Text(
        stringResource(R.string.folder_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = LIST_TEXT_INSET + FOLDER_INDENT, end = LIST_TEXT_INSET, top = 4.dp, bottom = 12.dp),
    )
}

/** The dialog [dialog] of the Pinned section, until [onDone]. */
@Composable
fun PinDialogs(dialog: PinDialog, pins: PinsUi, onDialog: (PinDialog) -> Unit, onDone: () -> Unit) {
    when (dialog) {
        is PinDialog.Rename -> NameDialog(
            title = R.string.rename_pinned_tab,
            label = R.string.name,
            initial = bookmarkLabel(dialog.bookmark),
            confirm = R.string.save,
            maxLength = BOOKMARK_TITLE_MAX,
            onConfirm = {
                pins.rename(dialog.bookmark, it)
                onDone()
            },
            onDismiss = onDone,
        )
        is PinDialog.NewFolder -> NameDialog(
            title = R.string.new_folder,
            label = R.string.folder_name,
            initial = "",
            confirm = R.string.create,
            maxLength = FolderName.MAX_LENGTH,
            onConfirm = {
                pins.addFolder(it, dialog.first)
                onDone()
            },
            onDismiss = onDone,
        )
        is PinDialog.RenameFolder -> NameDialog(
            title = R.string.rename_folder,
            label = R.string.folder_name,
            initial = dialog.folder.name,
            confirm = R.string.save,
            maxLength = FolderName.MAX_LENGTH,
            onConfirm = {
                pins.renameFolder(dialog.folder, it)
                onDone()
            },
            onDismiss = onDone,
        )
        is PinDialog.Move -> MoveToFolderDialog(
            bookmark = dialog.bookmark,
            folders = pins.folders,
            onMove = {
                pins.move(dialog.bookmark, it)
                onDone()
            },
            onNewFolder = { onDialog(PinDialog.NewFolder(dialog.bookmark)) },
            onDismiss = onDone,
        )
    }
}

/**
 * Asks for a name, with the keyboard up and the present name selected, so typing replaces it.
 * Laid out in the whole window, clear of the system bars and the keyboard, as the other editors.
 */
@Composable
private fun NameDialog(title: Int, label: Int, initial: String, confirm: Int, maxLength: Int, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val clean = name.text.trim().takeIf { it.isNotEmpty() }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp).testTag("name-dialog"),
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.copy(text = it.text.take(maxLength)) },
                label = { Text(stringResource(label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { clean?.let(onConfirm) }),
                modifier = Modifier.fillMaxWidth().focusRequester(field).testTag("name-field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clean!!) }, enabled = clean != null, modifier = Modifier.testTag("confirm-name")) {
                Text(stringResource(confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Chooses where a Pinned Tab goes: one of the Profile's other Folders, out of its Folder, or a
 * new Folder. Rows are 56dp, with the icon on the dialog's text line.
 */
@Composable
private fun MoveToFolderDialog(bookmark: Bookmark, folders: List<Folder>, onMove: (Folder?) -> Unit, onNewFolder: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("move-to-folder"),
        title = { Text(stringResource(R.string.move_to_folder_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                @Composable
                fun Destination(icon: ImageVector, name: String, tag: String, onClick: () -> Unit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onClick)
                            .testTag(tag),
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                        )
                    }
                }
                if (bookmark.folderId != null) {
                    Destination(JrnyIcons.Pin, stringResource(R.string.out_of_folder), "move-to-top") { onMove(null) }
                }
                folders.filter { it.id != bookmark.folderId }.forEach { folder ->
                    Destination(JrnyIcons.Folder, folder.name, "move-to-folder-${folder.id}") { onMove(folder) }
                }
                Destination(Icons.Filled.Add, stringResource(R.string.new_folder_ellipsis), "move-to-new-folder", onNewFolder)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Height of a Pinned Tab's or a Folder's row: one line of text. */
val PINNED_ROW_HEIGHT: Dp = 48.dp

/** How far a Folder's bookmarks are set in from the Folder's own row. */
val FOLDER_INDENT: Dp = 16.dp

/** A bookmark's name can be this long. */
private const val BOOKMARK_TITLE_MAX = 120
