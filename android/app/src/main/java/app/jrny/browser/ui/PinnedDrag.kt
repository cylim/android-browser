package app.jrny.browser.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * A row of the Pinned section being held and dragged (SPEC, "Pinned Tabs"). The row follows the
 * finger over the list, and the list tells from [center] what it is over. Positions are those of
 * the list's own layout, top to bottom, in pixels.
 */
@Stable
class PinnedDragState(private val list: LazyListState) {

    /** The key of the row being held, or null. */
    var key: String? by mutableStateOf(null)
        private set

    /** True once the held row has left its place: from then on letting go drops it, not opens its menu. */
    var moved: Boolean by mutableStateOf(false)
        private set

    /** Where the held row's top was when it was picked up, and how far the finger has gone since. */
    private var start by mutableFloatStateOf(0f)
    private var dragged by mutableFloatStateOf(0f)

    /** Where the row [key] is laid out now: its top and bottom. Null while it is scrolled out of view. */
    fun bounds(key: String): ClosedFloatingPointRange<Float>? =
        list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { it.offset.toFloat()..(it.offset + it.size).toFloat() }

    /** The middle of the held row, where the finger has taken it. */
    fun center(): Float? = key?.let(::bounds)?.let { start + dragged + (it.endInclusive - it.start) / 2 }

    /**
     * How far to draw the row [key] from where the list has laid it out: for the held row, as far
     * as the finger has taken it. The list moves the row's place while it is dragged, so this is
     * taken from the place it has now.
     */
    fun translationOf(key: String): Float = if (key != this.key) 0f else bounds(key)?.let { start + dragged - it.start } ?: 0f

    fun begin(key: String) {
        this.key = key
        start = bounds(key)?.start ?: 0f
        dragged = 0f
        moved = false
    }

    /** The finger moved [by]. Returns whether the row has left its place, [slop] being what a still finger wanders. */
    fun drag(by: Float, slop: Float): Boolean {
        dragged += by
        if (abs(dragged) > slop) moved = true
        return moved
    }

    fun end() {
        key = null
        moved = false
        dragged = 0f
    }
}

/**
 * Makes the row [key] one that can be held and dragged with [state]. Held and let go without
 * moving, it calls [onHeld], which opens its menu. Dragged, it follows the finger above the other
 * rows, [onDrag] runs for every move, and [onDrop] when it is let go. A row drawn [with] another
 * key moves along with that one: a Folder's rows with their Folder.
 */
fun Modifier.pinnedDrag(
    state: PinnedDragState,
    key: String,
    slop: Float,
    onHeld: () -> Unit,
    onDrag: () -> Unit,
    onDrop: () -> Unit,
    with: String? = null,
): Modifier = this
    .zIndex(if (state.key == key || (with != null && state.key == with)) 1f else 0f)
    .graphicsLayer { translationY = state.translationOf(key) + (with?.let(state::translationOf) ?: 0f) }
    .pointerInput(key) {
        awaitEachGesture {
            // Watched before the row's own click handling sees anything, and left alone until the
            // row has been held long enough: a tap stays a tap, and a swipe scrolls the list.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var wandered = Offset.Zero
            val letGoOrMoved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!change.pressed) return@withTimeoutOrNull true
                    wandered += change.positionChange()
                    if (wandered.getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (letGoOrMoved != null) return@awaitEachGesture

            // Held. From here on the gesture is the drag's alone: the row's click handling and
            // the list's scrolling see nothing more of it.
            state.begin(key)
            var lifted = true
            while (lifted) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    change?.consume()
                    lifted = false
                } else {
                    val by = change.positionChange().y
                    change.consume()
                    if (state.drag(by, slop)) onDrag()
                }
            }
            val dropped = state.moved
            if (dropped) onDrop()
            state.end()
            if (!dropped) onHeld()
        }
    }
