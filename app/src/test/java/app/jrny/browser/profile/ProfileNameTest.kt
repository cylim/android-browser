package app.jrny.browser.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileNameTest {

    @Test
    fun `names are trimmed`() {
        assertEquals("Work", ProfileName.clean("  Work \n"))
    }

    @Test
    fun `blank names are refused`() {
        assertNull(ProfileName.clean(""))
        assertNull(ProfileName.clean("   "))
    }

    @Test
    fun `names up to the limit are kept, longer ones refused`() {
        assertEquals("a".repeat(ProfileName.MAX_LENGTH), ProfileName.clean("a".repeat(ProfileName.MAX_LENGTH)))
        assertNull(ProfileName.clean("a".repeat(ProfileName.MAX_LENGTH + 1)))
    }
}
