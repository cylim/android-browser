package app.jrny.browser.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.jrny.browser.R
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
) {
    /** The Tab after the selected one, or before it with [step] -1, for swiping. Null at either end. */
    fun neighbour(step: Int): SavedTab? {
        val index = all.indexOfFirst { it.id == selected.id }
        return if (index < 0) null else all.getOrNull(index + step)
    }
}

/** A Tab's name in lists: its title, else its host, else "New Tab". */
@Composable
fun tabLabel(tab: SavedTab): String = tab.pageTitle ?: tab.displayHost.ifEmpty { stringResource(R.string.new_tab) }

/**
 * The Active Profile's Tabs, oldest first: the Sidebar's list and the Tab Overview. The selected
 * Tab sits on a [selectedColor] card. Rows are [ROW_HEIGHT] tall, text starts [TEXT_INSET] in, and
 * each close button sits in a 48dp slot [ICON_INSET] from the end.
 */
@Composable
fun TabList(
    tabs: TabsUi,
    shape: Shape,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onChosen: () -> Unit = {},
) {
    LazyColumn(modifier.testTag("tab-list"), contentPadding = contentPadding) {
        items(tabs.all, key = { it.id.value }) { tab ->
            TabRow(
                tab,
                selected = tab.id == tabs.selected.id,
                shape = shape,
                selectedColor = selectedColor,
                onSelect = {
                    tabs.select(tab.id)
                    onChosen()
                },
                onClose = { tabs.close(tab.id) },
            )
        }
    }
}

@Composable
private fun TabRow(tab: SavedTab, selected: Boolean, shape: Shape, selectedColor: Color, onSelect: () -> Unit, onClose: () -> Unit) {
    val label = tabLabel(tab)
    Surface(
        onClick = onSelect,
        color = if (selected) selectedColor else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("tab-row-${tab.id.value}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = ROW_HEIGHT)) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TEXT_INSET, top = 8.dp, bottom = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tab.pageTitle != null && tab.displayHost.isNotEmpty()) {
                    Text(
                        tab.displayHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.padding(end = ICON_INSET).testTag("close-tab")) {
                Icon(Icons.Filled.Close, stringResource(R.string.close_tab_named, label))
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

/** Height of a Tab row. */
private val ROW_HEIGHT: Dp = 56.dp

/** Where a Tab row's text starts. */
private val TEXT_INSET: Dp = 16.dp

/** Where a Tab row's 48dp close-button slot ends, so its icon centers 28dp from the row's end. */
private val ICON_INSET: Dp = 4.dp
