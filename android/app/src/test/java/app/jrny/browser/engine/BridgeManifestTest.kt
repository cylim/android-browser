package app.jrny.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The built-in extension comes in two copies: the release one (`src/main/assets/okapi`) and the
 * debug one (`src/debug/assets/okapi`), which also lets tests run scripts in a page. Only that
 * may differ between them.
 */
class BridgeManifestTest {
    // Gradle runs unit tests from the module's folder.
    private val release = File("src/main/assets/okapi/manifest.json").readText()
    private val debug = File("src/debug/assets/okapi/manifest.json").readText()

    @Test
    fun releaseCantRunCodeItIsSent() {
        assertFalse(release.contains("unsafe-eval"))
        assertFalse(release.contains("test.js"))
        assertFalse(File("src/main/assets/okapi/page.js").readText().contains("eval("))
    }

    @Test
    fun debugDiffersOnlyByTheTestScript() {
        val expected = release
            .replace("\"script-src 'self'; object-src 'self'\"", "\"script-src 'self' 'unsafe-eval'; object-src 'self'\"")
            .replace("\"js\": [\"page.js\"]", "\"js\": [\"page.js\", \"test.js\"]")
        assertEquals(expected, debug)
    }
}
