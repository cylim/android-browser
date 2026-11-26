package app.jrny.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPermissionsTest {

    @Test
    fun anyPatternForEveryPageMeansAllSites() {
        for (pattern in listOf("<all_urls>", "*://*/*", "http://*/*", "https://*/*")) {
            val asked = ExtensionPermissions.of(emptyArray(), arrayOf(pattern, "https://example.com/*"), emptyArray())
            assertTrue(pattern, asked.allSites)
            assertEquals("no sites listed beside all of them", emptyList<String>(), asked.sites)
        }
    }

    @Test
    fun aPathOnEverySiteIsStillEverySite() {
        val asked = ExtensionPermissions.of(emptyArray(), arrayOf("https://*/api/*"), emptyArray())
        assertTrue(asked.allSites)
        assertEquals(emptyList<String>(), asked.sites)
    }

    @Test
    fun sitesAreNamedOnceWithoutSchemeWildcardOrPath() {
        val asked = ExtensionPermissions.of(
            emptyArray(),
            arrayOf("*://*.example.com/*", "https://example.com/login", "https://news.example.org:8443/*", "file://*/*"),
            emptyArray(),
        )
        assertFalse(asked.allSites)
        assertEquals(listOf("example.com", "news.example.org"), asked.sites)
    }

    @Test
    fun aPatternThatIsntForWebPagesNamesNoSite() {
        assertNull(ExtensionPermissions.siteOf("file://*/*"))
        assertNull(ExtensionPermissions.siteOf("moz-extension://*/*"))
        assertNull(ExtensionPermissions.siteOf("https://*/*"))
    }

    @Test
    fun sendingNothingListsNothing() {
        val asked = ExtensionPermissions.of(arrayOf("tabs", "tabs", "storage"), emptyArray(), arrayOf("none"))
        assertEquals(listOf("tabs", "storage"), asked.permissions)
        assertEquals(emptyList<String>(), asked.dataCollection)
    }
}
