package app.jrny.browser.engine

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Base64

/** A site's icon, as the page sends it: a `data:` image, read no larger than an icon needs. */
@RunWith(AndroidJUnit4::class)
class SiteIconDecodeTest {

    private fun png(side: Int): String {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun anIconKeepsItsSize() {
        val icon = PageBridge.decodeDataImage(png(64))!!
        assertEquals(64, icon.width)
    }

    @Test
    fun aHugePictureInASmallFileIsReadSmall() {
        // One color compresses to a few kilobytes, but would take 64 MB as pixels.
        val address = png(4000)
        assertTrue(address.length < 512 * 1024)
        val icon = PageBridge.decodeDataImage(address)!!
        assertTrue("${icon.width}", icon.width <= 512)
    }

    @Test
    fun anythingButAnImageIsNoIcon() {
        assertNull(PageBridge.decodeDataImage("data:text/html;base64,PHA+"))
        assertNull(PageBridge.decodeDataImage("data:image/png;base64,bm90IGFuIGltYWdl"))
    }
}
