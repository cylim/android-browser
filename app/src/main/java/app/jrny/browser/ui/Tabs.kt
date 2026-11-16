package app.jrny.browser.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.jrny.browser.address.WebAddress
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.PinnedItem
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalViewConfiguration
import app.jrny.browser.library.Folder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jrny.browser.LinkOffer
import app.jrny.browser.R
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.TabId

/** Everything the layouts need to show and change the Active Profile's Tabs. */
class TabsUi(
    val all: List<SavedTab>,
    val selected: SavedTab,
    val select: (TabId) -> Unit,
    val close: (TabId) -> Unit,
    /** Opens an empty Tab and shows it. */
    val open: () -> Unit,
    /** Move: the selected Tab goes to that Profile, carrying only its address. */
    val move: (Profile) -> Unit = {},
    /** Open in Profile: the selected Tab's address opens there as a new Tab. */
    val openIn: (Profile) -> Unit = {},
    /** Opens an empty Private Tab and shows it. */
    val openPrivate: () -> Unit = {},
    /** What the window offers for the selected Tab, while it is one an Incoming Link just opened. */
    val linkOffer: LinkOffer? = null,
    val dismissIncomingLink: () -> Unit = {},
    /** Takes up the offered Site Rule ([LinkOffer.Always]). */
    val acceptRuleOffer: () -> Unit = {},
    /** The Profile's bookmarks as its Pinned Tabs. */
    val pins: PinsUi = PinsUi(),
) {
    /** True while the selected Tab is one an Incoming Link just opened. */
    val incomingLink: Boolean get() = linkOffer is LinkOffer.Opened

    /** The Tab after the selected one, or before it with [step] -1, for swiping. Null at either end. */
    fun neighbour(step: Int): SavedTab? {
        val index = all.indexOfFirst { it.id == selected.id }
        return if (index < 0) null else all.getOrNull(index + step)
    }
}

/** A Tab's name in lists: its title, else its host, else "New Tab" or "New Private Tab". */
@Composable
fun tabLabel(tab: SavedTab): String =
    tab.pageTitle ?: tab.displayHost.ifEmpty { stringResource(if (tab.isPrivate) R.string.new_private_tab else R.string.new_tab) }

/**
 * The Active Profile's Tab list, for the Sidebar and the Tab Overview: its Pinned Tabs and
 * Folders under "Pinned", then its other Tabs, oldest first, then its Private Tabs, each marked
 * with a lock. The selected Tab sits on a [selectedColor] card. In every row the site mark starts
 * [LIST_TEXT_INSET] in, the text follows [LIST_ICON_GAP] after it, and the close button sits in a
 * 48dp slot [LIST_ICON_INSET] from the end.
 *
 * "Pinned" shows only once there is something pinned. [tabsHeader] is the label above the other
 * Tabs; without one, the list labels them "Tabs" itself when there is a Pinned section above.
 *
 * A Private Tab's page is named only while a Private Tab is the one showing, which is when the
 * window can't be captured ([SecureWindow]). Otherwise its row just says "Private Tab".
 */
@Composable
fun TabList(
    tabs: TabsUi,
    shape: Shape,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onChosen: () -> Unit = {},
    tabsHeader: (@Composable () -> Unit)? = null,
) {
    val pins = tabs.pins
    var dialog by remember { mutableStateOf<PinDialog?>(null) }
    val listed = pins.bookmarks.map { it.id }.toSet()
    // A bookmark's open Tab is listed in the bookmark's row, not among the other Tabs.
    val openTabs = tabs.all.filter { it.bookmarkId in listed }.associateBy { it.bookmarkId }
    val others = tabs.all.filter { it.bookmarkId !in listed }

    /**
     * What moves the entry at [index] of [list] [by] places and hands the new order to [reorder],
     * or null when that would leave the list. The new order is made only when it is asked for.
     */
    fun <T> mover(list: List<T>, index: Int, by: Int, reorder: (List<T>) -> Unit): (() -> Unit)? =
        if (index + by !in list.indices) null else ({ reorder(list.toMutableList().apply { add(index + by, removeAt(index)) }) })

    // Dragging: a row of the Pinned section that is held follows the finger. While it does, the
    // list shows the order it would have if let go now, and keeps showing it until the saved
    // order arrives, so the row doesn't jump back for a moment.
    val listState = rememberLazyListState()
    val drag = remember(listState) { PinnedDragState(listState) }
    var draggedOrder by remember { mutableStateOf<List<PinnedItem>?>(null) }
    LaunchedEffect(pins.items) { draggedOrder = null }
    val items = draggedOrder ?: pins.items
    val latestPins by rememberUpdatedState(pins)
    // The order as it stands this instant, for the drag: a change made by the last move of the
    // finger counts before the list has been drawn again.
    fun orderNow(): List<PinnedItem> = draggedOrder ?: latestPins.items
    /** The Folder a held Pinned Tab is over: let go there, it goes in. */
    var dropFolder by remember { mutableStateOf<Long?>(null) }
    val slop = LocalViewConfiguration.current.touchSlop

    fun pinKey(bookmark: Bookmark) = "pin-${bookmark.id}"
    fun folderKey(folder: Folder) = "folder-${folder.id}"
    fun keyOf(entry: PinnedItem) = when (entry) {
        is PinnedItem.Page -> pinKey(entry.bookmark)
        is PinnedItem.Group -> folderKey(entry.folder)
    }
    fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().apply { this[a] = this@swapped[b]; this[b] = this@swapped[a] }
    fun ClosedFloatingPointRange<Float>.middle() = (start + endInclusive) / 2

    /**
     * A top-level row is being dragged. Past the middle of the Pinned Tab next to it, the two
     * change places. A Folder next to it is passed once its own row is passed; while a Pinned Tab
     * is over that row, the Folder is where it would be dropped.
     */
    fun dragTop(key: String) {
        val center = drag.center() ?: return
        val order = orderNow()
        val index = order.indexOfFirst { keyOf(it) == key }
        if (index < 0) return
        val next = order.getOrNull(index + 1)
        val previous = order.getOrNull(index - 1)
        dropFolder = if (order[index] !is PinnedItem.Page) {
            null
        } else {
            listOfNotNull(next, previous).filterIsInstance<PinnedItem.Group>()
                .firstOrNull { drag.bounds(folderKey(it.folder))?.contains(center) == true }?.folder?.id
        }
        if (dropFolder != null) return
        fun edge(entry: PinnedItem, below: Boolean): Float? = when (entry) {
            is PinnedItem.Page -> drag.bounds(pinKey(entry.bookmark))?.middle()
            is PinnedItem.Group -> drag.bounds(folderKey(entry.folder))?.let { if (below) it.endInclusive else it.start }
        }
        val down = next?.let { edge(it, below = true) }
        val up = previous?.let { edge(it, below = false) }
        when {
            down != null && center > down -> draggedOrder = order.swapped(index, index + 1)
            up != null && center < up -> draggedOrder = order.swapped(index, index - 1)
        }
    }

    /** Let go: into the Folder it was over, or to its new place. */
    fun dropTop(key: String) {
        val target = dropFolder
        dropFolder = null
        val order = draggedOrder
        val dropped = latestPins.items.firstOrNull { keyOf(it) == key }
        val folder = latestPins.folders.firstOrNull { it.id == target }
        if (folder != null && dropped is PinnedItem.Page) {
            draggedOrder = null
            latestPins.move(dropped.bookmark, folder)
        } else if (order != null && order.map(::keyOf) != latestPins.items.map(::keyOf)) {
            latestPins.reorder(order)
        } else {
            draggedOrder = null
        }
    }

    /** A Folder's row is being dragged: it changes places with the rows next to it in the Folder. */
    fun dragInFolder(folderId: Long, key: String) {
        val center = drag.center() ?: return
        val order = orderNow()
        val at = order.indexOfFirst { it is PinnedItem.Group && it.folder.id == folderId }
        val folder = (order.getOrNull(at) as? PinnedItem.Group)?.folder ?: return
        val index = folder.bookmarks.indexOfFirst { pinKey(it) == key }
        if (index < 0) return
        val down = folder.bookmarks.getOrNull(index + 1)?.let { drag.bounds(pinKey(it)) }?.middle()
        val up = folder.bookmarks.getOrNull(index - 1)?.let { drag.bounds(pinKey(it)) }?.middle()
        val swapped = when {
            down != null && center > down -> folder.bookmarks.swapped(index, index + 1)
            up != null && center < up -> folder.bookmarks.swapped(index, index - 1)
            else -> return
        }
        draggedOrder = order.toMutableList().also { it[at] = PinnedItem.Group(folder.copy(bookmarks = swapped)) }
    }

    /** Let go: out of the Folder, when dragged past its first or last row, or to its new place in it. */
    fun dropInFolder(folderId: Long, key: String) {
        val center = drag.center()
        val shown = (orderNow().firstOrNull { it is PinnedItem.Group && it.folder.id == folderId } as? PinnedItem.Group)?.folder
        val saved = latestPins.folders.firstOrNull { it.id == folderId }
        val bookmark = saved?.bookmarks?.firstOrNull { pinKey(it) == key }
        // The Folder's rows as the list has laid them out, the held one's own place among them.
        val top = shown?.let { drag.bounds(folderKey(it)) }?.start
        val bottom = shown?.bookmarks?.mapNotNull { drag.bounds(pinKey(it))?.endInclusive }?.maxOrNull()
        val outside = center != null && top != null && bottom != null && (center < top || center > bottom)
        if (bookmark != null && outside) {
            draggedOrder = null
            latestPins.move(bookmark, null)
        } else if (shown != null && saved != null && shown.bookmarks.map { it.id } != saved.bookmarks.map { it.id }) {
            latestPins.reorderIn(shown.bookmarks)
        } else {
            draggedOrder = null
        }
    }

    @Composable
    fun Pinned(bookmark: Bookmark, inFolder: Folder? = null, onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
        val open = openTabs[bookmark.id]
        val key = pinKey(bookmark)
        PinnedRow(
            bookmark,
            openTab = open,
            selected = open != null && open.id == tabs.selected.id,
            shape = shape,
            selectedColor = selectedColor,
            pins = pins,
            onOpen = {
                if (open != null) tabs.select(open.id) else pins.open(bookmark)
                onChosen()
            },
            onClose = { open?.let { tabs.close(it.id) } },
            onDialog = { dialog = it },
            indent = if (inFolder != null) FOLDER_INDENT else 0.dp,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            drag = { openMenu ->
                Modifier.pinnedDrag(
                    drag,
                    key,
                    slop,
                    onHeld = openMenu,
                    onDrag = { if (inFolder != null) dragInFolder(inFolder.id, key) else dragTop(key) },
                    onDrop = { if (inFolder != null) dropInFolder(inFolder.id, key) else dropTop(key) },
                    // A Folder's rows go along while the Folder is dragged.
                    with = inFolder?.let(::folderKey),
                )
            },
            lifted = drag.key == key && drag.moved,
        )
    }

    LazyColumn(modifier.testTag("tab-list"), state = listState, contentPadding = contentPadding) {
        if (items.isNotEmpty()) {
            item(key = "pinned-label") {
                SectionLabel(stringResource(R.string.pinned)) {
                    IconButton(onClick = { dialog = PinDialog.NewFolder(null) }, modifier = Modifier.testTag("new-folder")) {
                        Icon(JrnyIcons.NewFolder, stringResource(R.string.new_folder))
                    }
                }
            }
            items.forEachIndexed { index, entry ->
                // The top level in its new order, when this entry can go that way.
                val up = mover(items, index, -1, pins.reorder)
                val down = mover(items, index, 1, pins.reorder)
                when (entry) {
                    is PinnedItem.Page -> item(key = pinKey(entry.bookmark)) { Pinned(entry.bookmark, onMoveUp = up, onMoveDown = down) }
                    is PinnedItem.Group -> {
                        val folder = entry.folder
                        val key = folderKey(folder)
                        item(key = key) {
                            FolderRow(
                                folder,
                                shape,
                                pins,
                                onDialog = { dialog = it },
                                onMoveUp = up,
                                onMoveDown = down,
                                drag = { openMenu ->
                                    Modifier.pinnedDrag(drag, key, slop, onHeld = openMenu, onDrag = { dragTop(key) }, onDrop = { dropTop(key) })
                                },
                                lifted = drag.key == key && drag.moved,
                                dropTarget = dropFolder == folder.id,
                            )
                        }
                        if (!folder.collapsed) {
                            itemsIndexed(folder.bookmarks, key = { _, bookmark -> pinKey(bookmark) }) { place, bookmark ->
                                Pinned(
                                    bookmark,
                                    inFolder = folder,
                                    onMoveUp = mover(folder.bookmarks, place, -1, pins.reorderIn),
                                    onMoveDown = mover(folder.bookmarks, place, 1, pins.reorderIn),
                                )
                            }
                            if (folder.bookmarks.isEmpty()) item(key = "folder-empty-${folder.id}") { EmptyFolderHint() }
                        }
                    }
                }
            }
        }
        if (tabsHeader != null) {
            item(key = "tabs-label") { tabsHeader() }
        } else if (items.isNotEmpty()) {
            item(key = "tabs-label") { SectionLabel(stringResource(R.string.tabs)) }
        }
        items(others, key = { it.id.value }) { tab ->
            TabRow(
                tab,
                selected = tab.id == tabs.selected.id,
                named = !tab.isPrivate || tabs.selected.isPrivate,
                shape = shape,
                selectedColor = selectedColor,
                onSelect = {
                    tabs.select(tab.id)
                    onChosen()
                },
                onClose = { tabs.close(tab.id) },
                // Only a saved Tab with a web page can be pinned.
                onPin = if (!tab.isPrivate && WebAddress.isWebAddress(tab.url)) ({ pins.pin(tab) }) else null,
            )
        }
    }
    dialog?.let { open -> PinDialogs(open, pins, onDialog = { dialog = it }, onDone = { dialog = null }) }
}

/** A Tab's row. With [onPin], a long press opens its menu: pin it, or close it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabRow(
    tab: SavedTab,
    selected: Boolean,
    named: Boolean,
    shape: Shape,
    selectedColor: Color,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onPin: (() -> Unit)?,
) {
    val label = if (named) tabLabel(tab) else stringResource(R.string.private_tab)
    var menu by remember { mutableStateOf(false) }
    val compact = LocalCompactTabs.current
    Box {
        Surface(
            color = if (selected) selectedColor else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    role = Role.Tab,
                    onLongClickLabel = if (onPin != null) stringResource(R.string.more_actions) else null,
                    onLongClick = if (onPin != null) ({ menu = true }) else null,
                    onClick = onSelect,
                )
                .semantics { this.selected = selected }
                .testTag("tab-row-${tab.id.value}"),
        ) {
            ListRow(height = if (compact) COMPACT_ROW_HEIGHT else ROW_HEIGHT) {
                if (tab.isPrivate) {
                    // The lock sits where another row's site mark does.
                    Box(Modifier.padding(start = LIST_TEXT_INSET).size(SITE_ICON_SIZE), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = if (named) stringResource(R.string.private_tab) else null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).testTag("private-tab-mark"),
                        )
                    }
                } else {
                    SiteIcon(tab.url, Modifier.padding(start = LIST_TEXT_INSET))
                }
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).padding(start = LIST_ICON_GAP).padding(vertical = if (compact) 0.dp else 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact && named && tab.pageTitle != null && tab.displayHost.isNotEmpty()) {
                        Text(
                            tab.displayHost,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.padding(end = closeButtonInset()).testTag("close-tab")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close_tab_named, label))
                }
            }
        }
        if (onPin != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(12.dp)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pin_tab)) },
                    leadingIcon = { Icon(JrnyIcons.Pin, contentDescription = null) },
                    onClick = {
                        menu = false
                        onPin()
                    },
                    modifier = Modifier.testTag("tab-pin"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.close_tab)) },
                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    onClick = {
                        menu = false
                        onClose()
                    },
                    modifier = Modifier.testTag("tab-close"),
                )
            }
        }
    }
}

/**
 * The Bottom Bar's button for the Tab Overview: the number of Tabs in a rounded square, as
 * browsers show it, capped at "99+".
 */
@Composable
fun TabCountButton(count: Int, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.show_tabs, count, count)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }.testTag("tab-count")) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, LocalContentColor.current, RoundedCornerShape(6.dp)),
        ) {
            Text(if (count > 99) "99+" else count.toString(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/**
 * A horizontal swipe of at least [SWIPE_DISTANCE] calls [onSwipe] with 1 for a swipe to the left
 * (the next item) or -1 for one to the right (the previous item). [key] restarts the gesture
 * detector when what a swipe would pick changes.
 *
 * With [beforeChildren], the swipe is claimed before the content under it sees the drag, so a
 * swipe that starts on the address field switches Tabs instead of scrolling the field's text.
 * Taps still reach the content: only a drag that turns horizontal is claimed.
 */
fun Modifier.horizontalSwipe(vararg key: Any?, beforeChildren: Boolean = false, onSwipe: (step: Int) -> Unit): Modifier =
    pointerInput(*key, beforeChildren) {
        val pass = if (beforeChildren) PointerEventPass.Initial else PointerEventPass.Main
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
            var dragged = Offset.Zero
            var swiping = false
            while (true) {
                val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!swiping && change.isConsumed) break
                dragged += change.positionChange()
                if (!swiping && abs(dragged.x) > viewConfiguration.touchSlop && abs(dragged.x) > abs(dragged.y)) swiping = true
                if (swiping) change.consume()
            }
            if (swiping) {
                when {
                    dragged.x <= -SWIPE_DISTANCE.toPx() -> onSwipe(1)
                    dragged.x >= SWIPE_DISTANCE.toPx() -> onSwipe(-1)
                }
            }
        }
    }

/** How far a swipe has to travel to switch Tabs or Profiles. */
private val SWIPE_DISTANCE = 64.dp

/** Height of a Tab row: its title and, under it, its site. */
private val ROW_HEIGHT: Dp = 56.dp

/** Height of every row of the Tab list with compact rows (SPEC, "Styling"): one line. */
val COMPACT_ROW_HEIGHT: Dp = 40.dp

/**
 * Where a row's close button ends. With compact rows the button is 40dp, not 48dp, and sits 4dp
 * further in, so its icon stays in the column of the 48dp buttons above and below the list.
 */
@Composable
fun closeButtonInset(): Dp = if (LocalCompactTabs.current) LIST_ICON_INSET + 4.dp else LIST_ICON_INSET

/** Whether the Tab list's rows take one line, as the person set it. */
val LocalCompactTabs = compositionLocalOf { false }

/**
 * A row of the Tab list, [height] tall at least. In a row lower than 48dp, the buttons in it are
 * that low too: otherwise a close button's touch target would push the row back to 48dp.
 */
@Composable
fun ListRow(height: Dp, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minOf(height, 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.heightIn(min = height), content = content)
    }
}

/** Where a list row's site mark starts, and a section label's text. */
val LIST_TEXT_INSET: Dp = 16.dp

/** Between a row's site mark and its text. */
val LIST_ICON_GAP: Dp = 12.dp

/** Where a row's 48dp close-button slot ends, so its icon centers 28dp from the row's end. */
val LIST_ICON_INSET: Dp = 4.dp
