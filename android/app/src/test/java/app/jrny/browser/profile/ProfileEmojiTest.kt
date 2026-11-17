package app.jrny.browser.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEmojiTest {

    @Test
    fun `one emoji is kept as it is`() {
        listOf("🚀", "❤️", "🏠", "⭐", "🛠️", "⚽", "☀️", "✈️", "㊗️").forEach { assertEquals(it, ProfileEmoji.clean(it)) }
        assertEquals("🦊", ProfileEmoji.clean("  🦊 "))
    }

    @Test
    fun `an emoji made of several code points counts as one`() {
        // A flag, a family joined with zero-width joiners, a skin tone, a keycap.
        listOf("🇲🇾", "👨‍👩‍👧‍👦", "👍🏽", "1️⃣").forEach { assertEquals(it, ProfileEmoji.clean(it)) }
    }

    @Test
    fun `only the first emoji counts`() {
        assertEquals("🦊", ProfileEmoji.clean("🦊🐻"))
        assertEquals("🇲🇾", ProfileEmoji.clean("🇲🇾🇯🇵"))
        assertEquals("🚀", ProfileEmoji.clean("🚀 to the moon"))
    }

    @Test
    fun `plain text is not an emoji`() {
        listOf("", "   ", "fox", "A", "7", "!", "é", "work 🚀").forEach { assertNull("\"$it\"", ProfileEmoji.clean(it)) }
        // Nor is a letter, a digit or punctuation of any other script.
        listOf("日", "日本", "Ж", "ع", "한", "あ", "、", "。", "٣", "€", "—").forEach { assertNull("\"$it\"", ProfileEmoji.clean(it)) }
    }

    @Test
    fun `every suggested emoji is one`() {
        ProfileEmoji.suggested.forEach { assertEquals(it, ProfileEmoji.clean(it)) }
        assertEquals(ProfileEmoji.suggested.size, ProfileEmoji.suggested.toSet().size)
        assertTrue(ProfileEmoji.suggested.all { it.length <= ProfileEmoji.MAX_LENGTH })
    }
}
