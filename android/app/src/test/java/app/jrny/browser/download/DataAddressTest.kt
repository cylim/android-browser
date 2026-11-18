package app.jrny.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataAddressTest {

    @Test
    fun base64ContentIsDecoded() {
        val file = DataAddress.decode("data:text/csv;base64,YSxiCjEsMgo=")!!
        assertEquals("text/csv", file.mimeType)
        assertEquals("a,b\n1,2\n", String(file.bytes))
    }

    @Test
    fun plainContentIsPercentDecoded() {
        val file = DataAddress.decode("data:text/plain;charset=utf-8,Hello%2C%20world+%21")!!
        assertEquals("text/plain", file.mimeType)
        assertEquals("Hello, world+!", String(file.bytes))
    }

    @Test
    fun noTypeMeansPlainText() {
        assertEquals("text/plain", DataAddress.decode("data:,hi")!!.mimeType)
    }

    @Test
    fun aBrokenAddressIsNoFile() {
        assertNull(DataAddress.decode("data:text/plain;base64"))
        assertNull(DataAddress.decode("data:;base64,@@@"))
        assertNull(DataAddress.decode("https://example.com/a.txt"))
    }
}
