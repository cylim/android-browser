package app.jrny.browser.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionParserTest {

    private fun parse(json: String) = SuggestionParser.parse(json)

    @Test
    fun theSuggestionsAreTheArrayAfterTheQuery() {
        assertEquals(listOf("android tablet", "android tv"), parse("""["andr",["android tablet","android tv"]]"""))
        assertEquals(listOf("a", "b"), parse("""  [ "q" , [ "a" , "b" ] , [] , {"x":1} ]"""))
    }

    @Test
    fun escapesAreRead() {
        assertEquals(listOf("say \"hi\"", "back\\slash", "café", "two lines"), parse("""["q",["say \"hi\"","back\\slash","café","two\nlines"]]"""))
    }

    @Test
    fun aQueryWithBracketsOrQuotesDoesNotConfuseIt() {
        assertEquals(listOf("x"), parse("""["a [\"b\"] c",["x"]]"""))
    }

    @Test
    fun anythingElseGivesNothing() {
        assertEquals(emptyList<String>(), parse(""))
        assertEquals(emptyList<String>(), parse("<html>Blocked</html>"))
        assertEquals(emptyList<String>(), parse("""{"error":"no"}"""))
        assertEquals(emptyList<String>(), parse("""["q"]"""))
        assertEquals(emptyList<String>(), parse("""["q",[]]"""))
        assertEquals(emptyList<String>(), parse("""["q",[{"phrase":"x"}]]"""))
        assertEquals(listOf("whole"), parse("""["q",["whole","cut off"""))
    }

    @Test
    fun onlyAFewReasonableSuggestionsAreKept() {
        val many = (1..20).joinToString(",") { "\"s$it\"" }
        assertEquals(8, parse("""["q",[$many]]""").size)
        assertEquals(listOf("ok"), parse("""["q",["${"x".repeat(300)}","  ","ok"]]"""))
    }
}
