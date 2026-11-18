package app.jrny.browser.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {

    private val known = mapOf("pdf" to "application/pdf", "png" to "image/png", "mp4" to "video/mp4")

    private fun types(vararg accept: String) = FileTypes(accept.toList()) { known[it] }

    @Test
    fun noAcceptListTakesAnyFileWithCamera() {
        val any = types()
        assertEquals(emptyList<String>(), any.mimeTypes)
        assertTrue(any.takesImages)
        assertTrue(any.takesVideos)
    }

    @Test
    fun imagesOnlyOffersPhotosNotVideos() {
        val images = types("image/*")
        assertEquals(listOf("image/*"), images.mimeTypes)
        assertTrue(images.takesImages)
        assertFalse(images.takesVideos)
    }

    @Test
    fun suffixesBecomeMimeTypes() {
        val mixed = types(".pdf", ".png")
        assertEquals(listOf("application/pdf", "image/png"), mixed.mimeTypes)
        assertTrue(mixed.takesImages)
        assertFalse(mixed.takesVideos)
    }

    @Test
    fun aDocumentFieldOffersNoCamera() {
        val pdf = types(".pdf")
        assertFalse(pdf.takesImages)
        assertFalse(pdf.takesVideos)
    }

    @Test
    fun anUnknownSuffixLeavesAnyFileOpen() {
        // Filtering the picker down to only the known part would hide the files the page wants.
        assertEquals(emptyList<String>(), types(".pdf", ".xyz").mimeTypes)
        assertEquals(emptyList<String>(), types("*/*").mimeTypes)
    }
}
