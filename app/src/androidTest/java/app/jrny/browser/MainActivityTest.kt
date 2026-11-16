package app.jrny.browser

import android.annotation.SuppressLint
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.titled
import app.jrny.browser.tab.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The real activity: what survives recreation, and what reaches disk when the app goes to the background. */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireMultiProfile() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    /** The Active Profile's selected Tab, once the database has said which it is. */
    private fun tab(): Tab {
        var tab: Tab? = null
        compose.waitUntil(10_000) {
            compose.activityRule.scenario.onActivity { activity ->
                val browser = activity.browser!!
                tab = browser.active.value?.selected?.let(browser::tabFor)
            }
            tab != null
        }
        return tab!!
    }

    @Test
    fun theAddressComesBackAfterRecreationWithoutOpeningTheKeyboard() {
        val one = titled("one")
        val before = tab()
        compose.runOnUiThread { before.load(one) }
        await("loaded") { !before.isLoading && before.url == one && before.title == "one navigate" }

        compose.activityRule.scenario.recreate()

        val after = tab()
        assertTrue("a new Tab after recreation", after !== before)
        assertEquals(one, after.url)
        await("reloaded") { !after.isLoading && after.title == "one navigate" }
        // A restored Tab has an address, so the field doesn't grab focus and open the keyboard.
        compose.onNodeWithTag("address").assertIsNotFocused()
    }

    @Test
    fun cookiesReachDiskWhenTheAppGoesToTheBackground() {
        val name = "jrny_flush_${System.nanoTime()}"
        val profile = tab().profile
        val set = CountDownLatch(1)
        compose.runOnUiThread {
            profile.cookieManager.setCookie("https://example.com", "$name=1; Max-Age=600; Secure; Path=/") {
                set.countDown()
            }
        }
        assertTrue(set.await(5, TimeUnit.SECONDS))
        // WebView writes cookies only every 30 s or so, so a new one isn't on disk yet.
        assertFalse("cookie on disk before onStop", cookieFilesContain(name))

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // runs onStop
        assertTrue("cookie on disk after onStop", cookieFilesContain(name))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    /** Searches WebView's cookie databases (and their journals) in the app's data folder. */
    private fun cookieFilesContain(text: String): Boolean {
        var found = false
        compose.activityRule.scenario.onActivity { activity ->
            val needle = text.toByteArray()
            found = File(activity.applicationInfo.dataDir).walkTopDown()
                .filter { it.isFile && it.name.startsWith("Cookies") }
                .any { file -> file.readBytes().containsSequence(needle) }
        }
        return found
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
}
