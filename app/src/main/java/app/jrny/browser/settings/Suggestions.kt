package app.jrny.browser.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Something offered while the person types in the address field. */
data class Suggestion(
    /** What the row says: a search to run, or a page's title. */
    val text: String,
    /** The page to open, or null for a search for [text]. */
    val url: String?,
    val kind: Kind,
) {
    enum class Kind { Search, History, Bookmark }
}

/** Where search suggestions come from. In the app, the chosen search engine; in tests, a fake. */
fun interface SearchSuggestions {
    /** Searches the engine suggests for [query]. Empty when it can't be reached. */
    suspend fun fetch(engine: SearchEngine, query: String): List<String>
}

/**
 * Asks the search engine itself. The request carries only the typed text: no cookies, so it isn't
 * tied to any Account or Profile, and a plain user agent.
 */
class HttpSearchSuggestions : SearchSuggestions {
    override suspend fun fetch(engine: SearchEngine, query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(engine.suggestTemplate.replace("%s", URLEncoder.encode(query, Charsets.UTF_8)))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching emptyList()
                val body = connection.inputStream.use { it.readNBytes(MAX_BYTES) }.toString(Charsets.UTF_8)
                SuggestionParser.parse(body)
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val TIMEOUT_MS = 3_000
        const val MAX_BYTES = 64 * 1024
    }
}

/**
 * Reads the OpenSearch suggestions format, `["query", ["first", "second", …], …]`, which every
 * [SearchEngine] here answers with. Anything else gives no suggestions.
 */
object SuggestionParser {
    private const val MAX_SUGGESTIONS = 8
    private const val MAX_LENGTH = 200

    fun parse(json: String): List<String> {
        // The suggestions are the first array inside the outer one.
        val outer = json.indexOf('[')
        if (outer < 0) return emptyList()
        var i = skipString(json, json.indexOfFirstFrom(outer + 1) { !it.isWhitespace() }) ?: return emptyList()
        i = json.indexOf('[', i)
        if (i < 0) return emptyList()
        i++
        val found = mutableListOf<String>()
        while (i < json.length && found.size < MAX_SUGGESTIONS) {
            when (json[i]) {
                ']' -> break
                '"' -> {
                    val (text, next) = readString(json, i) ?: return found
                    if (text.isNotBlank() && text.length <= MAX_LENGTH) found += text
                    i = next
                }
                // An engine may send objects or nested arrays here. Those aren't plain suggestions.
                '[', '{' -> return found
                else -> i++
            }
        }
        return found
    }

    /** The index after the string that starts at [start], or null when there is none. */
    private fun skipString(json: String, start: Int): Int? {
        if (start < 0 || start >= json.length || json[start] != '"') return null
        return readString(json, start)?.second
    }

    /** The string that starts at the quote at [start], and the index after its closing quote. */
    private fun readString(json: String, start: Int): Pair<String, Int>? {
        val text = StringBuilder()
        var i = start + 1
        while (i < json.length) {
            when (val c = json[i]) {
                '"' -> return text.toString() to i + 1
                '\\' -> {
                    if (i + 1 >= json.length) return null
                    when (val escaped = json[i + 1]) {
                        'n', 'r', 't', 'b', 'f' -> text.append(' ')
                        'u' -> {
                            val code = json.substring(i + 2, minOf(i + 6, json.length)).toIntOrNull(16) ?: return null
                            text.append(code.toChar())
                            i += 4
                        }
                        else -> text.append(escaped)
                    }
                    i += 2
                }
                else -> {
                    text.append(c)
                    i++
                }
            }
        }
        return null
    }

    private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (i in start until length) if (predicate(this[i])) return i
        return -1
    }
}
