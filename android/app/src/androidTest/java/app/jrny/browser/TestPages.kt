package app.jrny.browser

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry

/** Pages and waits shared by the device tests. Every page is a `data:` URL, so no test needs the network. */
object TestPages {

    /** A `data:` page with [html] as its body. */
    fun page(html: String): String = "data:text/html," + Uri.encode(html)

    /**
     * A page whose title is "[name] <navigation type>": "navigate" on a fresh load, "reload" after
     * a reload and "back_forward" after back or forward. A test can tell a real reload from none.
     */
    fun titled(name: String): String = page(
        "<title>$name</title><p>$name</p>" +
            "<script>document.title='$name '+performance.getEntriesByType('navigation')[0].type</script>",
    )

    /** Runs [block] on the main thread and returns its result. */
    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** Waits until [condition], checked on the main thread, holds. */
    fun await(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!onMain(condition)) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(50)
        }
    }
}
