package app.jrny.browser.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Android's download service accepts what the app hands it, and forgets a download that is cancelled. */
@RunWith(AndroidJUnit4::class)
class SystemDownloaderTest {

    private val downloader = SystemDownloader(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun aDownloadIsAcceptedThenCancelled() {
        TestServer(mapOf("/file" to "download:jrny-test.txt:hello")).use { server ->
            val request = DownloadRequest(server.url("/file"), "JrnyTest", "jrny-test-${System.nanoTime()}.txt", "text/plain", 5)
            val id = downloader.start(request, cookies = "session=test")
            try {
                // Running or already done, depending on how fast the service picked it up.
                assertNotEquals(DownloadStatus.Gone, downloader.statuses(listOf(id))[id])
            } finally {
                downloader.cancel(id)
            }
            assertEquals(DownloadStatus.Gone, downloader.statuses(listOf(id))[id])
        }
    }

    @Test
    fun anUnknownDownloadIsGone() {
        assertEquals(mapOf(987_654_321L to DownloadStatus.Gone), downloader.statuses(listOf(987_654_321L)))
        assertEquals(emptyMap<Long, DownloadStatus>(), downloader.statuses(emptyList()))
    }
}
