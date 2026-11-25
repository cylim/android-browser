package app.jrny.browser.ui

import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestServer
import app.jrny.browser.TestPages.runInPage
import app.jrny.browser.download.Download
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadStatus
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Agreeing to a download, the note that it started, and the Downloads list. */
@RunWith(AndroidJUnit4::class)
class DownloadsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val server = TestServer(
        mapOf(
            "/page" to "<title>page</title><a id=f href=/file>file</a>",
            "/file" to "download:report.pdf:0123456789",
        ),
    )
    private var downloads by mutableStateOf(emptyList<Download>())
    private var statuses by mutableStateOf(emptyMap<Long, DownloadStatus>())
    private val started = mutableListOf<DownloadRequest>()
    private val opened = mutableListOf<Download>()
    private val removed = mutableListOf<Download>()
    private var canOpen = true
    private var pageFileSaved by mutableStateOf<Pair<String, Boolean>?>(null)

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-downloads")))
            tab.load(server.url("/page"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "page" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
        server.close()
    }

    private fun show(size: DpSize) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TestTabs.ui(),
                    TestProfiles.ui(),
                    LibraryUi(
                        downloads = downloads,
                        startDownload = { started += it },
                        downloadStatuses = { asked -> asked.associate { it.id to (statuses[it.id] ?: DownloadStatus.Gone) } },
                        openDownload = {
                            opened += it
                            canOpen
                        },
                        removeDownload = { removed += it },
                    ),
                    links = LinksUi(pageFileSaved = pageFileSaved, dismissPageFileSaved = { pageFileSaved = null }),
                )
            }
        }
    }

    private fun clickLink(id: String) {
        runInPage(tab, "document.getElementById('$id').click()")
        compose.waitUntil(10_000) { tab.download != null }
    }

    private fun download(id: Long, name: String) = Download(id, 100 + id, server.url("/$name"), name, "application/pdf", startedAt = 1_700_000_000_000)

    @Test
    fun aFileDownloadsOnlyAfterThePersonAgrees() {
        show(PHONE_PORTRAIT)
        clickLink("f")
        compose.onNodeWithText("Download report.pdf?").assertExists()
        val size = Formatter.formatShortFileSize(InstrumentationRegistry.getInstrumentation().targetContext, 10)
        compose.onNodeWithText(size, substring = true).assertExists()
        assertEquals(emptyList<DownloadRequest>(), started)

        compose.onNodeWithTag("confirm-download").performClick()
        assertEquals(listOf("report.pdf"), started.map { it.fileName })
        compose.onNodeWithTag("download-dialog").assertDoesNotExist()

        // The note names the file and leads to the list.
        compose.onNodeWithText("Downloading report.pdf").assertExists()
        compose.onNodeWithTag("download-started-action").performClick()
        compose.onNodeWithTag("panel-Downloads").assertExists()
    }

    @Test
    fun aFileThatCantBeSavedAfterAllSaysSoInPlaceOfDownloading() {
        show(PHONE_PORTRAIT)
        clickLink("f")
        compose.onNodeWithTag("confirm-download").performClick()
        compose.onNodeWithText("Downloading report.pdf").assertExists()

        // The Downloads folder refused the file.
        compose.runOnUiThread { pageFileSaved = "report.pdf" to false }
        compose.waitForIdle()
        compose.onNodeWithText("Downloading report.pdf").assertDoesNotExist()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(app.jrny.browser.R.string.page_file_failed, "report.pdf")).assertExists()
    }

    @Test
    fun theSameFileFailingTwiceSaysSoTwice() {
        val failed = InstrumentationRegistry.getInstrumentation().targetContext.getString(app.jrny.browser.R.string.page_file_failed, "report.pdf")
        show(PHONE_PORTRAIT)
        clickLink("f")
        compose.onNodeWithTag("confirm-download").performClick()
        compose.runOnUiThread { pageFileSaved = "report.pdf" to false }
        compose.onNodeWithText(failed).assertExists()

        // Tried again without closing the notice. The new save clears it, and fails the same way
        // before the screen draws again.
        clickLink("f")
        compose.onNodeWithTag("confirm-download").performClick()
        compose.runOnUiThread {
            pageFileSaved = null
            pageFileSaved = "report.pdf" to false
        }
        compose.onNodeWithText("Downloading report.pdf").assertDoesNotExist()
        compose.onNodeWithText(failed).assertExists()

        // Closing it leaves nothing behind, not "Downloading".
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(app.jrny.browser.R.string.dismiss)).performClick()
        compose.onNodeWithTag("page-file-prompt").assertDoesNotExist()
        compose.onNodeWithText("Downloading report.pdf").assertDoesNotExist()
    }

    @Test
    fun cancellingStartsNothing() {
        show(TABLET_LANDSCAPE)
        clickLink("f")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("download-dialog").assertDoesNotExist()
        compose.onNodeWithTag("download-started-prompt").assertDoesNotExist()
        assertEquals(emptyList<DownloadRequest>(), started)
        assertEquals(null, tab.download)
    }

    @Test
    fun aFileWithNothingToReadCantBeDownloadedAndSaysSo() {
        var dismissed = 0
        // An offer whose response had no body.
        val made = DownloadRequest("blob:https://example.com/1b2c", "made-here.txt", "text/plain", -1)
        compose.setContent {
            JrnyTheme { DownloadDialog(made, onDownload = { started += made }, onDismiss = { dismissed++ }) }
        }
        compose.onNodeWithText("Download made-here.txt?").assertExists()
        compose.onNodeWithText("This kind of file can't be downloaded yet.").assertExists()
        compose.onNodeWithTag("confirm-download").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissed)
        assertEquals(emptyList<DownloadRequest>(), started)
    }

    @Test
    fun theListShowsWhereEachDownloadStandsAndOpensFinishedFiles() {
        downloads = listOf(download(3, "running.pdf"), download(2, "done.pdf"), download(1, "failed.pdf"))
        statuses = mapOf(3L to DownloadStatus.Running(0.4f), 2L to DownloadStatus.Done(2000), 1L to DownloadStatus.Failed)
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-downloads").performClick()

        compose.onNodeWithText("Downloading · 40%", substring = true).assertExists()
        // The size in the device's own format, which has its own spacing characters.
        val size = Formatter.formatShortFileSize(InstrumentationRegistry.getInstrumentation().targetContext, 2000)
        compose.onNodeWithText(size, substring = true).assertExists()
        compose.onNodeWithText("Failed", substring = true).assertExists()

        // Only a finished file opens.
        compose.onNodeWithText("running.pdf").performClick()
        assertEquals(emptyList<Download>(), opened)
        compose.onNodeWithText("done.pdf").performClick()
        assertEquals(listOf(downloads[1]), opened)

        // The button says what it will do: cancel a running download, take a finished one off the list.
        compose.onNodeWithContentDescription("Cancel the download of running.pdf").performClick()
        compose.onNodeWithContentDescription("Remove done.pdf from the list").performClick()
        assertEquals(listOf(downloads[0], downloads[1]), removed)
    }

    @Test
    fun aFileNoAppCanOpenSaysSo() {
        canOpen = false
        downloads = listOf(download(1, "strange.xyz"))
        statuses = mapOf(1L to DownloadStatus.Done(10))
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-downloads").performClick()
        compose.onNodeWithText("strange.xyz").performClick()
        compose.onNodeWithText("No app on this device can open this file.").assertExists()
    }

    @Test
    fun anEmptyListSaysWhatWillShowThere() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-downloads").performClick()
        compose.onNodeWithText("Files you download in this Profile show here.").assertExists()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
