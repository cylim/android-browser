package app.jrny.browser.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.TestServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Redirects are followed in the app, and each hop gets only its own address's cookies. */
@RunWith(AndroidJUnit4::class)
class DownloadRedirectsTest {

    @Test
    fun eachHopGetsOnlyItsOwnCookiesAndTheLastAddressIsReturned() {
        TestServer(mapOf("/file" to "download:report.pdf:data")).use { files ->
            val pages = mapOf("/get" to "redirect:/get/2", "/get/2" to "redirect:${files.url("/file")}")
            TestServer(pages).use { site ->
                // As a cookie store does: each address has its own cookies.
                val cookiesFor = { url: String -> if (url.startsWith(site.url("/"))) "session=site-secret" else "cdn=public" }

                val resolved = DownloadRedirects.resolve(site.url("/get"), "TestAgent", cookiesFor)

                assertEquals(files.url("/file"), resolved)
                assertEquals(listOf("/get" to "session=site-secret", "/get/2" to "session=site-secret"), site.requests.toList())
                // The other server was asked once, and never saw the site's cookie.
                assertEquals(listOf("/file" to "cdn=public"), files.requests.toList())
            }
        }
    }

    @Test
    fun anAddressThatDoesNotRedirectIsItself() {
        TestServer(mapOf("/file" to "download:a.txt:hello")).use { server ->
            assertEquals(server.url("/file"), DownloadRedirects.resolve(server.url("/file"), "", { null }))
            assertEquals(server.url("/missing"), DownloadRedirects.resolve(server.url("/missing"), "", { null }))
        }
    }

    @Test
    fun redirectsThatNeverEndOrLeaveTheWebAreNotFollowedForever() {
        val pages = mapOf("/loop" to "redirect:/loop", "/app" to "redirect:intent://example.com/#Intent;scheme=https;end")
        TestServer(pages).use { server ->
            assertEquals(server.url("/loop"), DownloadRedirects.resolve(server.url("/loop"), "", { null }))
            assertTrue(server.requests.size <= 5)
            assertEquals(server.url("/app"), DownloadRedirects.resolve(server.url("/app"), "", { null }))
        }
    }

    @Test
    fun anAddressThatCantBeReachedIsItself() {
        assertEquals("http://127.0.0.1:9/file", DownloadRedirects.resolve("http://127.0.0.1:9/file", "", { null }))
    }
}
