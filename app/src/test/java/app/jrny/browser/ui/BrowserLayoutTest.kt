package app.jrny.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserLayoutTest {

    @Test
    fun `phones and narrow windows get the Bottom Bar`() {
        assertEquals(BrowserLayout.BottomBar, BrowserLayout.forWindowWidth(411f)) // Pixel 7, portrait
        assertEquals(BrowserLayout.BottomBar, BrowserLayout.forWindowWidth(800f)) // Pixel Tablet, portrait
        assertEquals(BrowserLayout.BottomBar, BrowserLayout.forWindowWidth(839.9f))
    }

    @Test
    fun `windows 840dp and wider get the Sidebar`() {
        assertEquals(BrowserLayout.Sidebar, BrowserLayout.forWindowWidth(840f))
        assertEquals(BrowserLayout.Sidebar, BrowserLayout.forWindowWidth(1280f)) // Pixel Tablet, landscape
    }
}
