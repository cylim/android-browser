package app.jrny.browser.icons

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Site icons on disk: one per site, per Profile, small, and gone when asked. */
@RunWith(AndroidJUnit4::class)
class SiteIconStoreTest {

    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "icons-${System.nanoTime()}")
    private val personal = ProfileId("1")
    private val work = ProfileId("2")

    @After
    fun delete() {
        root.deleteRecursively()
    }

    private fun icon(color: Int, size: Int = 32): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun anIconIsKeptForItsSiteAndReadBackAfterARestart() = runBlocking {
        val store = SiteIconStore(root)
        assertNull(store.load(personal, "https://example.com/"))
        assertEquals(0, store.version.value)

        store.save(personal, "https://www.example.com/a/page?x=1", icon(Color.RED))
        assertEquals(1, store.version.value)
        // Any page of the site has it, with or without "www.", and at once from memory.
        assertEquals(Color.RED, store.cached(personal, "https://example.com/other")?.getPixel(0, 0))
        assertEquals(Color.RED, store.load(personal, "http://EXAMPLE.com/")?.getPixel(0, 0))
        // Another site of the same domain has its own.
        assertNull(store.load(personal, "https://mail.example.com/"))

        // As after the app restarts: nothing in memory, read from the file.
        val restarted = SiteIconStore(root)
        assertNull(restarted.cached(personal, "https://example.com/"))
        assertEquals(Color.RED, restarted.load(personal, "https://example.com/")?.getPixel(0, 0))
        assertNotNull(restarted.cached(personal, "https://example.com/"))

        // A newer icon replaces it.
        store.save(personal, "https://example.com/", icon(Color.BLUE))
        assertEquals(Color.BLUE, SiteIconStore(root).load(personal, "https://example.com/")?.getPixel(0, 0))
    }

    @Test
    fun aLargeIconIsScaledDownAndWhatHasNoSiteIsNotKept() = runBlocking {
        val store = SiteIconStore(root)
        store.save(personal, "https://example.com/", icon(Color.GREEN, size = 512))
        val kept = SiteIconStore(root).load(personal, "https://example.com/")!!
        assertEquals(SiteIconStore.MAX_SIZE to SiteIconStore.MAX_SIZE, kept.width to kept.height)

        store.save(personal, "about:blank", icon(Color.GREEN))
        store.save(personal, "data:text/html,<p>hi</p>", icon(Color.GREEN))
        assertNull(store.load(personal, "about:blank"))
        assertNull(store.load(personal, null))
        assertEquals(1, File(root, personal.value).list()?.size)
    }

    @Test
    fun iconsBelongToTheirProfileAndTheFolderDoesNotNameSites() = runBlocking {
        val store = SiteIconStore(root)
        store.save(personal, "https://example.com/", icon(Color.RED))
        assertNull(store.load(work, "https://example.com/"))
        store.save(work, "https://example.com/", icon(Color.BLUE))
        assertEquals(Color.RED, store.load(personal, "https://example.com/")?.getPixel(0, 0))
        assertEquals(Color.BLUE, store.load(work, "https://example.com/")?.getPixel(0, 0))

        val names = root.walk().filter { it.isFile }.map { it.name }.toList()
        assertEquals(2, names.size)
        assertTrue(names.toString(), names.none { "example" in it })

        // Deleting a Profile's icons leaves the other's.
        store.clear(work)
        assertNull(store.load(work, "https://example.com/"))
        assertNull(SiteIconStore(root).load(work, "https://example.com/"))
        assertNotNull(store.load(personal, "https://example.com/"))
    }

    @Test
    fun clearingKeepsOnlyTheIconsOfThePagesStillOnRecord() = runBlocking {
        val store = SiteIconStore(root)
        store.save(personal, "https://kept.example/", icon(Color.RED))
        store.save(personal, "https://visited.example/", icon(Color.BLUE))
        store.save(work, "https://visited.example/", icon(Color.BLUE))
        val before = store.version.value

        store.keepOnly(personal, listOf("https://www.kept.example/some/bookmark", "about:blank"))
        assertTrue(store.version.value > before)
        assertNotNull(store.load(personal, "https://kept.example/"))
        assertNull(store.load(personal, "https://visited.example/"))
        assertNull(SiteIconStore(root).load(personal, "https://visited.example/"))
        // Another Profile's are untouched.
        assertNotNull(store.load(work, "https://visited.example/"))
    }
}
