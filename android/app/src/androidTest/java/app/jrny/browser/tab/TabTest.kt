package app.jrny.browser.tab

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.TestPages.page
import app.jrny.browser.TestPages.titled
import app.jrny.browser.TestServer
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs on a device or emulator. Uses `data:` pages, so it needs no network. */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class TabTest {

    private val tabs = mutableListOf<Tab>()

    @Before
    fun requireMultiProfile() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    @After
    fun destroyTabs() = onMain { tabs.forEach(Tab::destroy) }

    @Test
    fun aTabUsesItsProfilesWebData() {
        val tab = newTab("test-binding")
        assertEquals("jrny-profile-test-binding", onMain { WebViewCompat.getProfile(tab.webView).name })
    }

    @Test
    fun profilesDontSeeEachOthersCookies() {
        val site = "https://example.com"
        val a = WebProfile.of(ProfileId("test-iso-a"))
        val b = WebProfile.of(ProfileId("test-iso-b"))
        val set = CountDownLatch(1)
        onMain { a.cookieManager.setCookie(site, "jrny_test=a; Max-Age=60; Secure") { set.countDown() } }
        assertTrue(set.await(5, TimeUnit.SECONDS))

        fun sees(cookies: () -> CookieManager) = onMain { cookies().getCookie(site).orEmpty().contains("jrny_test=a") }
        assertTrue("A sees its own cookie", sees { a.cookieManager })
        assertFalse("B sees A's cookie", sees { b.cookieManager })
        assertFalse("WebView's default profile sees A's cookie", sees { CookieManager.getInstance() })
    }

    @Test
    fun loadGoBackGoForwardAndReload() {
        val tab = newTab("test-nav")
        val one = titled("one")
        val two = titled("two")

        onMain { tab.load(one) }
        awaitPage(tab, one, "one navigate")
        assertFalse(onMain { tab.canGoBack })

        onMain { tab.load(two) }
        awaitPage(tab, two, "two navigate")
        assertTrue(onMain { tab.canGoBack })
        assertFalse(onMain { tab.canGoForward })

        onMain { tab.goBack() }
        awaitPage(tab, one, "one back_forward")
        assertTrue(onMain { tab.canGoForward })

        onMain { tab.goForward() }
        awaitPage(tab, two, "two back_forward")

        // The page itself reports a reload, so this fails if reload() does nothing.
        onMain { tab.reload() }
        awaitPage(tab, two, "two reload")
    }

    @Test
    fun theAddressIsSetAsSoonAsALoadStarts() {
        val tab = newTab("test-address")
        assertEquals(null, onMain { tab.url })
        val one = titled("one")
        assertEquals(one, onMain { tab.load(one); tab.url })
    }

    @Test
    fun linksToOtherSchemesDontLoad() {
        val tab = newTab("test-schemes")
        val start = page(
            "<title>start</title><script>" +
                "setTimeout(function(){location.href='mailto:someone@example.com'},0);" +
                "setTimeout(function(){location.href='intent://example.com#Intent;scheme=https;end'},100);" +
                "setTimeout(function(){document.title='done'},300)</script>",
        )
        onMain { tab.load(start) }
        awaitPage(tab, start, "done")
        Thread.sleep(500)
        // Had either link loaded, the Tab would show an error page at that address.
        assertEquals(start, onMain { tab.url })
        assertFalse(onMain { tab.crashed })
    }

    @Test
    fun httpPagesLoad() {
        // A browser must load the http:// pages people ask for (network_security_config.xml).
        TestServer(mapOf("/" to "<title>http ok</title>")).use { server ->
            val tab = newTab("test-http")
            val address = server.url("/")
            onMain { tab.load(address) }
            try {
                await("http page loaded") { !tab.isLoading && tab.url == address && tab.title == "http ok" }
            } catch (e: AssertionError) {
                val state = onMain { "url=${tab.url} title=${tab.title} loading=${tab.isLoading}" }
                throw AssertionError("${e.message}: $state")
            }
        }
    }

    @Test
    fun pageConsoleOutputStaysOutOfTheSystemLog() {
        val tab = newTab("test-console")
        val nonce = System.nanoTime().toString()
        val start = page("<script>console.log('jrny-console-$nonce');document.title='logged'</script>")
        onMain { tab.load(start) }
        awaitPage(tab, start, "logged")
        // A line the test writes itself proves this process can read its own log.
        Log.i("JrnyTabTest", "jrny-control-$nonce")
        Thread.sleep(500)

        val log = readOwnLog()
        assertTrue("the test can read its own log", log.contains("jrny-control-$nonce"))
        assertFalse("page console output was logged", log.contains("jrny-console-$nonce"))
    }

    @Test
    fun aDeadRendererGivesTheTabANewWebView() {
        val visits = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tab = onMain {
            Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-crash")), onVisited = { visits += it })
                .also { tabs += it }
        }
        val one = titled("one")
        onMain { tab.load(one) }
        awaitPage(tab, one, "one navigate")
        val first = onMain { tab.webView }

        // WebView's debug address for killing the renderer (see "Handle termination" in the WebView docs).
        onMain { first.loadUrl("chrome://crash") }
        await("renderer gone") { tab.crashed }
        assertTrue(onMain { tab.webView } !== first)

        // The reload brings the same page back in the new WebView, with its title kept meanwhile.
        onMain { tab.reload() }
        assertEquals("one navigate", onMain { tab.title })
        await("page back in the new WebView") { !tab.isLoading && tab.webView.url == one && tab.title == "one navigate" }
        assertFalse(onMain { tab.crashed })
        // It is the same visit as before, not a new one.
        assertEquals(listOf(one), visits.toList())
    }

    @Test
    fun aPageThatFailsToLoadKeepsItsAddressAndTakesNoTitleFromTheErrorPage() {
        val tab = newTab("test-load-failure")
        val one = titled("one")
        onMain { tab.load(one) }
        awaitPage(tab, one, "one navigate")

        // Nothing listens on this port. WebView shows its own error page, titled "Webpage not available".
        val unreachable = "http://127.0.0.1:9/"
        onMain { tab.load(unreachable) }
        await("load failed") { !tab.isLoading && tab.url == unreachable }
        Thread.sleep(300)
        assertEquals("", onMain { tab.title })
        assertEquals(unreachable, onMain { tab.url })

        // The next page that loads has its title again.
        val two = titled("two")
        onMain { tab.load(two) }
        awaitPage(tab, two, "two navigate")
    }

    @Test
    fun onlyPagesThePersonGoesToCountAsVisits() {
        val visits = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tab = onMain {
            Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-visits")), onVisited = { visits += it })
                .also { tabs += it }
        }
        val one = titled("one")
        val two = titled("two")

        // A saved page coming back is not a visit, and neither is reloading it. The saved title
        // differs from the page's, so the wait below ends only once the page has really loaded.
        onMain { tab.restore(one, "saved", 0) }
        awaitPage(tab, one, "one navigate")
        onMain { tab.reload() }
        awaitPage(tab, one, "one reload")
        assertEquals(emptyList<String>(), visits.toList())

        // Going to a page is a visit. Going back to the one before, and forward again, is not.
        onMain { tab.load(two) }
        awaitPage(tab, two, "two navigate")
        onMain { tab.goBack() }
        awaitPage(tab, one, "one back_forward")
        onMain { tab.goForward() }
        awaitPage(tab, two, "two back_forward")
        assertEquals(listOf(two), visits.toList())
    }

    @Test
    fun aFileIsOfferedAndThePageBeforeItStays() {
        val pages = mapOf("/page" to "<title>page</title><a id=f href=/file>file</a>", "/file" to "download:../notes 2026.txt:hello")
        TestServer(pages).use { server ->
            val tab = newTab("test-download")
            onMain { tab.load(server.url("/page")) }
            awaitPage(tab, server.url("/page"), "page")

            onMain { tab.webView.evaluateJavascript("document.getElementById('f').click()", null) }
            await("file offered") { tab.download != null }
            val offered = onMain { tab.download!! }
            assertEquals(server.url("/file"), offered.url)
            // The site's name for the file, without the folders it tried to add.
            assertEquals("notes 2026.txt", offered.fileName)
            assertEquals(5L, offered.size)
            assertEquals(server.url("/page"), offered.referrer)
            assertTrue(offered.supported)

            // The Tab still shows the page, not a load that never ends.
            await("page stays") { !tab.isLoading && tab.url == server.url("/page") && tab.title == "page" }
            onMain { tab.dismissDownload() }
            assertEquals(null, onMain { tab.download })

            // Typing the file's address offers it too. The Tab goes back to the page it shows, and
            // no page is named as the one that linked the file.
            onMain { tab.load(server.url("/file")) }
            await("file offered again") { tab.download != null }
            assertEquals(null, onMain { tab.download!!.referrer })
            await("page still stays") { !tab.isLoading && tab.url == server.url("/page") && tab.title == "page" }
        }
    }

    @Test
    fun aFileInANewTabLeavesTheTabEmpty() {
        TestServer(mapOf("/file" to "download:notes.txt:hello")).use { server ->
            val tab = newTab("test-download-new-tab")
            onMain { tab.load(server.url("/file")) }
            await("file offered") { tab.download != null }
            await("no page") { !tab.isLoading && tab.url == null }
        }
    }

    @Test
    fun aNewAddressWithinTheSamePageIsAVisitWithThatPagesTitle() {
        // As a site does when it swaps in its preferred address, or an app page moves on.
        val pages = mapOf("/app" to "<title>App</title><button id=go onclick=\"history.pushState({}, '', '/app/next')\">go</button>")
        TestServer(pages).use { server ->
            val visits = java.util.Collections.synchronizedList(mutableListOf<String>())
            val seen = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
            val tab = onMain {
                Tab(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    WebProfile.of(ProfileId("test-same-page")),
                    onVisited = { visits += it },
                    onPageSeen = { url, title -> seen += url to title },
                ).also { tabs += it }
            }
            onMain { tab.load(server.url("/app")) }
            awaitPage(tab, server.url("/app"), "App")

            onMain { tab.webView.evaluateJavascript("document.getElementById('go').click()", null) }
            await("moved on") { tab.url == server.url("/app/next") }
            assertEquals("App", onMain { tab.title })
            assertEquals(listOf(server.url("/app"), server.url("/app/next")), visits.toList())
            assertTrue(seen.toList().toString(), seen.contains(server.url("/app/next") to "App"))
        }
    }

    @Test
    fun aDesktopSiteGetsTheDesktopAgentAndAMobileOneTheTabsOwn() {
        TestServer(mapOf("/agent" to "echo-agent")).use { server ->
            val url = server.url("/agent")
            val site = app.jrny.browser.site.SiteOrigin.of(url)!!
            val tab = onMain {
                Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-desktop")), desktopFor = { it == site })
                    .also { tabs += it }
            }
            onMain { tab.load(url) }
            await("desktop agent") { !tab.isLoading && tab.title.startsWith("agent: ") }
            val desktop = onMain { tab.title }
            assertTrue(desktop, "X11; Linux x86_64" in desktop && "Android" !in desktop)
            assertTrue(onMain { tab.desktopSite })

            // Turned off for the site: the page loads again with the Tab's own agent.
            onMain { tab.setDesktopSite(site, false) }
            await("mobile agent") { !tab.isLoading && "Android" in tab.title }
            assertFalse(onMain { tab.desktopSite })
        }
    }

    @Test
    fun textSizeReachesTheOpenPage() {
        val tab = newTab("test-zoom")
        onMain { tab.textZoom = 150 }
        assertEquals(150, onMain { tab.webView.settings.textZoom })
    }

    @Test
    fun darkPagesGiveTheTabANewWebViewThatShowsThePageAgain() {
        val tab = newTab("test-dark")
        val start = page("<title>dark</title><p>text</p>")
        onMain { tab.load(start) }
        awaitPage(tab, start, "dark")
        val before = onMain { tab.webView }
        onMain { tab.darkPages = true }
        assertNotSame(before, onMain { tab.webView })
        awaitPage(tab, start, "dark")
        assertTrue(onMain { tab.webView.context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.isLightTheme)).use { !it.getBoolean(0, true) } })
    }

    private fun newTab(profileId: String): Tab = onMain {
        Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId(profileId)))
            .also { tabs += it }
    }

    private fun awaitPage(tab: Tab, url: String, title: String) =
        await("$title at $url") { !tab.isLoading && tab.url == url && tab.title == title }

    private fun readOwnLog(): String =
        Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "raw")).inputStream.bufferedReader().readText()
}
