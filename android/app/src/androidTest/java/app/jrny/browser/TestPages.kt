package app.jrny.browser

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.tab.Tab
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Pages and waits shared by the device tests. Every page is a `data:` URL, so no test needs the network. */
object TestPages {

    /** A `data:` page with [html] as its body. */
    fun page(html: String): String = "data:text/html," + Uri.encode(html)

    /**
     * A page whose title is "[name] <navigation type>": "navigate" on a fresh load, "reload" after
     * a reload and "back_forward" after back or forward, unless the page came back from the
     * back-forward cache as it was. A test can tell a real reload from none.
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

    /**
     * Runs [script] in [tab]'s page, in Okapi's content script world, which sees the page's DOM,
     * and returns its result as JSON, or null when there was no page to run it in.
     */
    fun runInPage(tab: Tab, script: String, timeoutMs: Long = 15_000): String? {
        val done = CountDownLatch(1)
        var result: String? = null
        onMain {
            MainScope().launch {
                result = tab.evaluate(script)
                done.countDown()
            }
        }
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) throw AssertionError("Script didn't finish: $script")
        return result
    }

    /** Runs [script] in [tab]'s popup, as [runInPage] does in its page. */
    fun runInPopup(tab: Tab, script: String, timeoutMs: Long = 15_000): String? {
        val done = CountDownLatch(1)
        var result: String? = null
        onMain {
            MainScope().launch {
                result = tab.evaluateInPopup(script)
                done.countDown()
            }
        }
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) throw AssertionError("Script didn't finish: $script")
        return result
    }
}
