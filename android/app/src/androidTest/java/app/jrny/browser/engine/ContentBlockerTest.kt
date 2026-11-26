package app.jrny.browser.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.TestServer
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * uBlock Origin blocks what its built-in lists name, with no network: `/prebid-library.js` is in
 * EasyList for every site, and `/plain.js` is in no list.
 */
@RunWith(AndroidJUnit4::class)
class ContentBlockerTest {

    private val tabs = mutableListOf<Tab>()

    @After
    fun destroyTabs() = onMain { tabs.forEach(Tab::destroy) }

    @Test
    fun aPageDoesntGetWhatTheListsBlock() = awaitBlocking(WebProfile.of(ProfileId("test-blocker")))

    @Test
    fun aPrivateTabDoesntGetWhatTheListsBlockEither() = awaitBlocking(WebProfile.privateOf(ProfileId("test-blocker"), 1))

    /**
     * Loads the page again until a load gets the script no list names and not the blocked one.
     * After the app's first install, uBlock Origin needs a few seconds to read its lists; from then
     * on, and from the first page after any later start, it blocks.
     */
    private fun awaitBlocking(profile: WebProfile) {
        // A page and scripts of their own for each try, so no try is answered from the cache.
        val pages = (1..TRIES).flatMap { n ->
            listOf(
                "/ads$n" to """<title>ads</title><script src="/prebid-library.js?$n"></script><script src="/plain.js?$n"></script>""",
                "/prebid-library.js?$n" to "",
                "/plain.js?$n" to "",
            )
        }.toMap()
        TestServer(pages).use { server ->
            val tab = onMain { Tab(InstrumentationRegistry.getInstrumentation().targetContext, profile).also { tabs += it } }
            val deadline = System.currentTimeMillis() + 60_000
            var n = 0
            do {
                check(++n <= TRIES && System.currentTimeMillis() < deadline) { "Every load for 60 s requested the blocked script" }
                if (n > 1) Thread.sleep(200)
                onMain { tab.load(server.url("/ads$n")) }
                await("the script no list names") { !tab.isLoading && server.requests.any { it.first == "/plain.js?$n" } }
            } while (server.requests.any { it.first == "/prebid-library.js?$n" })
        }
    }

    private companion object {
        /** More pages than a minute of tries, 200 ms apart, can load. */
        const val TRIES = 300
    }
}
