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
    fun onlyWebAddressesCanBeFetched() {
        fun request(url: String) = DownloadRequest(url, "", "file", "", -1)
        assertTrue(request("https://example.com/file.zip").supported)
        assertTrue(request("http://example.com/file.zip").supported)
        assertFalse(request("blob:https://example.com/1234").supported)
        assertFalse(request("data:text/plain,hello").supported)
        assertFalse(request("file:///sdcard/secret").supported)
    }
}
