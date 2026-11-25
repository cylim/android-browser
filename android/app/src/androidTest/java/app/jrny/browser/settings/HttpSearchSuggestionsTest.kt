package app.jrny.browser.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The request for suggestions, against a server on the device standing in for a search engine.
 * It listens on 127.0.0.1: Okapi's own connections refuse http:// anywhere else.
 */
@RunWith(AndroidJUnit4::class)
class HttpSearchSuggestionsTest {

    private val suggestions = HttpSearchSuggestions()

    @Test
    fun theAnswerIsReadAndTheRequestCarriesNoCookies() {
        TestServer(mapOf("/suggest?q=two+words" to """["two words",["two words one","two words two"]]"""), loopback = true).use { engine ->
            val offered = runBlocking { suggestions.fetch(engine.url("/suggest?q=%s"), "two words") }
            assertEquals(listOf("two words one", "two words two"), offered)
            assertEquals(listOf("/suggest?q=two+words" to ""), engine.requests.toList())
        }
    }

    @Test
    fun anEngineThatFailsOrCantBeReachedGivesNothing() {
        TestServer(mapOf("/broken?q=x" to "<html>Service unavailable</html>"), loopback = true).use { engine ->
            assertEquals(emptyList<String>(), runBlocking { suggestions.fetch(engine.url("/broken?q=%s"), "x") })
            assertEquals(emptyList<String>(), runBlocking { suggestions.fetch(engine.url("/missing?q=%s"), "x") })
        }
        assertEquals(emptyList<String>(), runBlocking { suggestions.fetch("http://127.0.0.1:9/?q=%s", "x") })
    }

    @Test
    fun okapisOwnConnectionsRefuseHttpToOtherHosts() {
        TestServer(mapOf("/suggest?q=x" to """["x",["x one"]]""")).use { engine ->
            assertEquals(emptyList<String>(), runBlocking { suggestions.fetch(engine.url("/suggest?q=%s"), "x") })
            assertEquals(emptyList<Pair<String, String>>(), engine.requests.toList())
        }
    }
}
