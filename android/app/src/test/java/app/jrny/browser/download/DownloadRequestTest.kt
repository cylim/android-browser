package app.jrny.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestTest {

    private fun safe(name: String) = DownloadRequest.safeFileName(name)

    @Test
    fun anOrdinaryNameIsKept() {
        assertEquals("Report 2026 (final).pdf", safe("Report 2026 (final).pdf"))
        assertEquals("résumé.docx", safe("résumé.docx"))
    }

    @Test
    fun foldersAreDroppedSoAFileCantLandOutsideDownloads() {
        assertEquals("passwd", safe("../../etc/passwd"))
        assertEquals("evil.exe", safe("..\\..\\evil.exe"))
        assertEquals("file.txt", safe("/sdcard/Android/data/file.txt"))
    }

    @Test
    fun charactersAFileSystemRejectsAreReplaced() {
        assertEquals("a_b_c_d_e_f_g_h.txt", safe("a:b*c?d\"e<f>g|h.txt"))
        assertEquals("line_break.txt", safe("line\nbreak.txt"))
    }

    @Test
    fun aHiddenOrEmptyNameGetsAPlainOne() {
        assertEquals("bashrc", safe(".bashrc"))
        assertEquals("download", safe("..."))
        assertEquals("download", safe("   "))
        assertEquals("download", safe(""))
    }

    @Test
    fun aLongNameIsCutButKeepsItsExtension() {
        val cut = safe("a".repeat(300) + ".pdf")
        assertEquals(120, cut.length)
        assertTrue(cut.endsWith(".pdf"))
    }

    @Test
    fun theSitesOwnFileNameIsRead() {
        fun name(header: String?) = DownloadRequest.suggestedFileName(header)
        assertEquals("notes 2026.txt", name("attachment; filename=\"notes 2026.txt\""))
        assertEquals("report.pdf", name("attachment; filename=report.pdf"))
        assertEquals("report.pdf", name("ATTACHMENT; FILENAME=report.pdf; size=10"))
        assertEquals("naïve file+1.txt", name("attachment; filename=\"fallback.txt\"; filename*=UTF-8''na%C3%AFve%20file+1.txt"))
        assertEquals(null, name("attachment"))
        assertEquals(null, name("inline; filename=\"\""))
        assertEquals(null, name(null))
    }

    @Test
    fun aFileCanBeSavedWhenThereIsSomethingToRead() {
        fun response(url: String, body: java.io.InputStream?) = DownloadRequest(url, "file", "", -1, source = DownloadSource.Response(body))
        assertTrue(response("https://example.com/file.zip", "x".byteInputStream()).supported)
        assertTrue(response("blob:https://example.com/1234", "x".byteInputStream()).streamed)
        assertFalse(response("https://example.com/file.zip", null).supported)
        // "Save image" fetches from the page once the person agrees.
        val image = DownloadRequest("https://example.com/a.png", "a.png", "", -1, source = DownloadSource.FromPage)
        assertTrue(image.supported)
        assertFalse(image.streamed)
    }

    @Test
    fun theListShowsTheFilesWebAddressOrElseThePages() {
        assertEquals("https://example.com/a.zip", DownloadRequest("https://example.com/a.zip", "a.zip", "", -1, page = "https://example.com/").listedAddress)
        assertEquals("https://example.com/", DownloadRequest("data:text/plain,hi", "hi.txt", "", -1, page = "https://example.com/").listedAddress)
        assertEquals("", DownloadRequest("blob:https://example.com/1", "f", "", -1).listedAddress)
    }
}
