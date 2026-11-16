package app.jrny.browser.profile

/**
 * An isolated browsing identity (GLOSSARY): its own web data, Tabs, history, bookmarks and
 * downloads. [id] names its web data store on disk and never changes (ADR 0004); the name and
 * the look are only how the person tells Profiles apart.
 */
data class Profile(
    val id: ProfileId,
    val name: String,
    val color: ProfileColor,
    val icon: ProfileIcon,
    /** An emoji shown on the avatar in the icon's place, or null to show [icon]. */
    val emoji: String? = null,
    val tint: ProfileTint = ProfileTint.Medium,
) {
    val look: ProfileLook get() = ProfileLook(color, icon, emoji, tint)
}

/**
 * How a Profile looks (SPEC, "Profiles"): its color, which also colors the browser while the
 * Profile is active, how strongly it does, and what its avatar shows.
 */
data class ProfileLook(
    val color: ProfileColor = ProfileColor.Teal,
    val icon: ProfileIcon = ProfileIcon.Person,
    val emoji: String? = null,
    val tint: ProfileTint = ProfileTint.Medium,
)

/** The colors a Profile can have. Each is dark enough for a white icon on top. */
enum class ProfileColor(val argb: Long) {
    Blue(0xFF1E5AC8),
    Indigo(0xFF3949AB),
    Purple(0xFF6A1B9A),
    Pink(0xFFAD1457),
    Red(0xFFC62828),
    Orange(0xFFD84315),
    Amber(0xFFA65F00),
    Green(0xFF2E7D32),
    Teal(0xFF00796B),
    Cyan(0xFF00838F),
    Brown(0xFF6D4C41),
    Slate(0xFF455A64),
}

/**
 * How strongly a Profile's color tints the browser's own surfaces: [fraction] of the way from
 * the plain surface to the color.
 */
enum class ProfileTint(val fraction: Float) { Soft(0.05f), Medium(0.10f), Strong(0.20f) }

/** Rules for the emoji on a Profile's avatar. */
object ProfileEmoji {
    /** Offered in the editor. Any other emoji can be typed. */
    val suggested = listOf(
        "🏠", "💼", "🎓", "🛒", "✈️", "🎮", "🎵", "📚", "💰", "🧪", "🛠️", "🎨",
        "⚽", "🍳", "🌱", "🐶", "🚀", "🧳", "📷", "💡", "❤️", "⭐", "🔒", "🌍",
    )

    /**
     * The one emoji to store from what was typed: its first character as a person sees it, which
     * can be several code points (a flag, a family, a skin tone). Null when there is none, or
     * when it is plain text.
     */
    fun clean(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val breaks = java.text.BreakIterator.getCharacterInstance()
        breaks.setText(text)
        val first = text.substring(0, breaks.next())
        // Letters, digits and punctuation are not emoji. A digit with a keycap mark is.
        val plain = first.all { it.code < 0x2000 }
        return first.takeIf { !plain && it.length <= MAX_LENGTH }
    }

    /** The longest sequence stored: enough for a family of four with skin tones. */
    const val MAX_LENGTH = 32
}

/** The icons a Profile can have. The UI maps each to a Material icon. */
enum class ProfileIcon { Person, Home, Build, Email, ShoppingCart, Star, Favorite, Face }

/** Rules for a Profile's name. */
object ProfileName {
    const val MAX_LENGTH = 40

    /** The name to store, trimmed, or null when [input] can't be a name (blank, or too long). */
    fun clean(input: String): String? = input.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
