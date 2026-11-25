package app.jrny.browser.tab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.TestServer
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What a Private Tab's pages store (SPEC, "Private Tabs"). Only site databases (IndexedDB) reach
 * the disk: encrypted, under names that don't say the site, and deleted once no Private Tab is
 * open.
 */
@RunWith(AndroidJUnit4::class)
class PrivateStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engineFiles = File(context.filesDir, "mozilla")

    @Test
    fun aPrivateTabsSiteDataIsEncryptedOnDiskAndGoesWithTheLastPrivateTab() {
        val marker = "jrny-private-${System.nanoTime()}"
        val page = "<title>working</title><script>" +
            "localStorage.setItem('k','$marker');" +
            "var r=indexedDB.open('$marker',1);" +
            "r.onupgradeneeded=function(){r.result.createObjectStore('s')};" +
            "r.onsuccess=function(){var t=r.result.transaction('s','readwrite');t.objectStore('s').put('$marker','k');" +
            "t.oncomplete=function(){document.title='stored'}};" +
            "r.onerror=function(){document.title='error '+r.error}</script>"
        TestServer(mapOf("/store" to page)).use { server ->
            val started = System.currentTimeMillis()
            val tab = onMain { Tab(context, WebProfile.privateOf(ProfileId("test-private-storage"), 1)) }
            var destroyed = false
            try {
                onMain { tab.load(server.url("/store")) }
                await("page stored its data") { tab.title == "stored" || tab.title.startsWith("error") }
                assertEquals("stored", onMain { tab.title })
                // Give the engine time to write anything it would write.
                Thread.sleep(3_000)
                val written = engineFiles.walkTopDown().filter { it.isFile && it.lastModified() >= started - 1_000 }.toList()

                // Nothing the page stored can be read from the disk, and no file names the site.
                val readable = written.filter { String(it.readBytes(), Charsets.ISO_8859_1).contains(marker) }
                assertEquals("readable on disk", emptyList<File>(), readable)
                val host = server.url("/").substringAfter("://").substringBefore(':')
                assertEquals("named after the site", emptyList<File>(), written.filter { it.path.contains(host) })
                val private = written.filter { it.path.contains("/storage/private/") }
                assertTrue("its database is in the engine's private storage", private.isNotEmpty())

                // Closing the last Private Tab deletes it.
                onMain { tab.destroy() }
                destroyed = true
                await("private storage deleted", timeoutMs = 20_000) { private.none(File::exists) }
            } finally {
                if (!destroyed) onMain { tab.destroy() }
            }
        }
    }
}
