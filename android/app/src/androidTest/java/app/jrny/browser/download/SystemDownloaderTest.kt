package app.jrny.browser.download

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/** Files a page offers are written into the Downloads folder, and one stopped halfway is gone. */
@RunWith(AndroidJUnit4::class)
class SystemDownloaderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val downloader = SystemDownloader(context)

    private fun awaitStatus(file: String, done: (DownloadStatus) -> Boolean): DownloadStatus {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val status = downloader.savedStatus(file)
            if (done(status) || System.currentTimeMillis() > deadline) return status
            Thread.sleep(50)
        }
    }

    @Test
    fun aFileIsWrittenOutAndThenDone() {
        val content = "hello from a page".toByteArray()
        val file = downloader.receive("jrny-test-${System.nanoTime()}.txt", "text/plain", content.size.toLong(), content.inputStream())!!
        try {
            assertEquals(DownloadStatus.Done(content.size.toLong()), awaitStatus(file) { it is DownloadStatus.Done })
            val written = context.contentResolver.openInputStream(file.toUri())!!.use { it.readBytes() }
            assertTrue(written.contentEquals(content))
        } finally {
            context.contentResolver.delete(file.toUri(), null, null)
        }
    }

    @Test
    fun aFileStoppedHalfwayIsGone() {
        // A body that never ends, as a large file still on its way.
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                Thread.sleep(5)
                b.fill('x'.code.toByte(), off, off + len)
                return len
            }
        }
        val file = downloader.receive("jrny-test-${System.nanoTime()}.bin", "application/octet-stream", 1L shl 40, endless)!!
        assertTrue(awaitStatus(file) { it is DownloadStatus.Running } is DownloadStatus.Running)
        downloader.cancelSaved(file)
        assertEquals(DownloadStatus.Gone, awaitStatus(file) { it == DownloadStatus.Gone })
    }

    @Test
    fun anUnknownDownloadIsGone() {
        assertEquals(mapOf(987_654_321L to DownloadStatus.Gone), downloader.statuses(listOf(987_654_321L)))
        assertEquals(emptyMap<Long, DownloadStatus>(), downloader.statuses(emptyList()))
    }
}
