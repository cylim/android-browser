package app.jrny.browser.handoff

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** How a link for another app is read: only the link goes, and an intent for a web page stays a web link. */
@RunWith(AndroidJUnit4::class)
class AppLinkTest {

    @Test
    fun aPhoneLinkIsForAnotherApp() {
        val link = AppLink.of("tel:+15550100")!!
        assertEquals("tel", link.scheme)
        assertTrue(link.openable)
        assertNull(link.webAddress)
    }

    @Test
    fun aWebPageIsNoLinkForAnotherApp() {
        assertNull(AppLink.of("https://example.com/"))
        assertNull(AppLink.of("data:text/plain,hi"))
    }

    @Test
    fun anIntentForAWebPageThatNamesNoAppIsAWebLink() {
        val link = AppLink.of("intent://example.com:8080/path?q=1#Intent;scheme=https;end")!!
        assertEquals("https://example.com:8080/path?q=1", link.webAddress)
    }

    @Test
    fun anIntentKeepsItsWebFallbackAndNothingElseOfThePages() {
        val link = AppLink.of(
            "intent://open/item#Intent;scheme=myapp;package=com.example.app;component=com.example.app/.Secret;" +
                "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fitem;S.token=abc;end",
        )!!
        assertEquals("https://example.com/item", link.fallback)
        assertNull(link.webAddress)
        assertTrue(link.openable)
    }

    @Test
    fun aFallbackThatIsntAWebPageIsIgnored() {
        val link = AppLink.of("intent://x#Intent;scheme=myapp;S.browser_fallback_url=javascript%3Aalert(1);end")!!
        assertNull(link.fallback)
    }
}
