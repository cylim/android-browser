package app.jrny.browser

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.tab.SavedTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app receiving links from another app (SPEC, "Incoming Links"): a web link opens as a
 * new Tab in the Active Profile and offers a Move; anything else is ignored.
 */
@RunWith(AndroidJUnit4::class)
class IncomingLinkTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun browser(): BrowserState {
        var browser: BrowserState? = null
        compose.activityRule.scenario.onActivity { browser = it.browser }
        return browser!!
    }

    private fun tabs(): List<SavedTab> {
        compose.waitUntil(10_000) { browser().active.value != null }
        return browser().active.value!!.tabs
    }

    /** Sends [address] to the running app the way another app's link does. */
    private fun view(address: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(address))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    @Test
    fun aWebLinkOpensANewTabInTheActiveProfileAndOffersAMove() {
        TestServer(mapOf("/link" to "<title>linked</title>")).use { server ->
            val profile = browser().let { compose.waitUntil(10_000) { it.active.value != null }; it.active.value!!.profile }
            val before = tabs().map { it.id }

            view(server.url("/link"))

            compose.waitUntil(10_000) { tabs().size == before.size + 1 }
            val opened = tabs().single { it.id !in before }
            assertEquals(server.url("/link"), opened.url)
            assertEquals(true, opened.selected)
            assertEquals(profile.id, browser().active.value!!.profile.id)
            compose.waitUntil(10_000) { browser().incomingLinkTab == opened.id }
            // The same activity took the link: there is still one browser window.
            compose.waitUntil(15_000) { tabs().single { it.id == opened.id }.title == "linked" }
        }
    }

    @Test
    fun anythingThatIsNotAWebAddressIsIgnored() {
        val before = tabs().map { it.id }
        view("javascript:alert(1)")
        view("file:///data/data/app.jrny.browser/databases/jrny.db")
        view("content://app.jrny.browser/anything")
        view("intent://example.com/#Intent;scheme=https;end")
        // Give the intents time to arrive: the web link sent last is the one that must open.
        TestServer(mapOf("/ok" to "<title>ok</title>")).use { server ->
            view(server.url("/ok"))
            compose.waitUntil(10_000) { tabs().size == before.size + 1 }
            assertEquals(server.url("/ok"), tabs().single { it.id !in before }.url)
        }
    }
}
