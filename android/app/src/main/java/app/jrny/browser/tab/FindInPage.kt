package app.jrny.browser.tab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession

/**
 * Find in page for one Tab (SPEC, "Find in page"): it looks in the page of [session], which may
 * change when the page's content process is replaced. Use it on the main thread.
 */
class FindInPage(private val session: () -> GeckoSession) {
    /** What it is looking for and has found, or null while it is closed. */
    var state: FindState? by mutableStateOf(null)
        private set

    /** Opens it, empty. */
    fun open() {
        if (state == null) state = FindState("", 0, 0)
    }

    /** Looks for [text] in the page and highlights every match. */
    fun find(text: String) {
        state = FindState(text, 0, 0)
        val finder = session().finder
        if (text.isEmpty()) return finder.clear()
        finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        finder.find(text, 0).accept { result -> showFound(text, result) }
    }

    /** Moves to the next match, or the one before with [forward] false. */
    fun next(forward: Boolean) {
        val query = state?.query
        if (query.isNullOrEmpty()) return
        session().finder.find(null, if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS).accept { result -> showFound(query, result) }
    }

    /** Closes it and clears its highlights. */
    fun close() {
        if (state == null) return
        state = null
        session().finder.clear()
    }

    private fun showFound(query: String, result: GeckoSession.FinderResult?) {
        val open = state ?: return
        if (open.query != query || result == null) return
        state = open.copy(current = if (result.total == 0) 0 else result.current.coerceAtLeast(1), total = result.total)
    }
}

/** What Find in page looks for, and which of its matches is the current one (1-based, 0 for none) of [total]. */
data class FindState(val query: String, val current: Int, val total: Int)
