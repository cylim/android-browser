package app.jrny.browser.tab

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.WebRequestError

/** The page Okapi shows when a page can't be loaded (SPEC, "Error pages"). */
@RunWith(AndroidJUnit4::class)
class ErrorPagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun html(address: String): String = String(Base64.decode(address.substringAfter(";base64,"), Base64.DEFAULT))

    @Test
    fun eachFailureIsToldInItsOwnWords() {
        fun kind(code: Int, category: Int = WebRequestError.ERROR_CATEGORY_NETWORK) = ErrorPages.kindOf(WebRequestError(code, category))
        assertEquals(ErrorPages.Kind.Dangerous, kind(WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI, WebRequestError.ERROR_CATEGORY_SAFEBROWSING))
        assertEquals(ErrorPages.Kind.Insecure, kind(WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_CATEGORY_SECURITY))
        assertEquals(ErrorPages.Kind.NotFound, kind(WebRequestError.ERROR_UNKNOWN_HOST, WebRequestError.ERROR_CATEGORY_URI))
        assertEquals(ErrorPages.Kind.Offline, kind(WebRequestError.ERROR_OFFLINE))
        assertEquals(ErrorPages.Kind.Refused, kind(WebRequestError.ERROR_CONNECTION_REFUSED))
        assertEquals(ErrorPages.Kind.Other, kind(WebRequestError.ERROR_REDIRECT_LOOP))
    }

    @Test
    fun tryAgainAsksForTheFailedPageAgain() {
        val failed = "https://example.com/a?b=1&c=<2>"
        val page = html(ErrorPages.page(context, failed, ErrorPages.Kind.Offline, dark = false))
        assertTrue(page, "<h1>" + context.getString(app.jrny.browser.R.string.error_offline_title) in page)
        // Its own address, written so the page can't be broken by it.
        assertTrue(page, """href="https://example.com/a?b=1&amp;c=&lt;2&gt;"""" in page)
        assertTrue(page, context.getString(app.jrny.browser.R.string.error_try_again) in page)
        // It names the site, not the whole address.
        assertTrue(page, "<title>example.com</title>" in page)
    }

    @Test
    fun aDangerousSiteOffersOnlyGoingBack() {
        val failed = "https://dangerous.example/"
        val page = html(ErrorPages.page(context, failed, ErrorPages.Kind.Dangerous, dark = false))
        assertTrue(page, context.getString(app.jrny.browser.R.string.error_go_back) in page)
        assertFalse(page, context.getString(app.jrny.browser.R.string.error_try_again) in page)
        assertFalse("no link to the site", "href=\"$failed\"" in page)
    }

    @Test
    fun onlyOkapisOwnErrorPagesCountAsOne() {
        assertTrue(ErrorPages.isErrorPage(ErrorPages.page(context, "https://example.com/", ErrorPages.Kind.Other, dark = true)))
        // A data: page written the way they look can't pass for one.
        val lookalike = "data:text/html;charset=utf-8;base64," + Base64.encodeToString("<p>hi</p>".toByteArray(), Base64.NO_WRAP)
        assertFalse(ErrorPages.isErrorPage(lookalike))
        assertFalse(ErrorPages.isErrorPage("data:text/html;charset=utf-8;okapi-error=guess;base64,PHA+"))
        assertFalse(ErrorPages.isErrorPage(null))
    }
}
