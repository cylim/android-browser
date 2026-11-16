package app.jrny.browser.profile

/**
 * An isolated browsing identity (GLOSSARY): its own web data, Tabs, history, bookmarks and
 * downloads. [id] names its web data store on disk and never changes (ADR 0004); the name,
 * color and icon are only how the person tells Profiles apart.
 */
data class Profile(
    val id: ProfileId,
    val name: String,
    val color: ProfileColor,
    val icon: ProfileIcon,
)

/** The colors a Profile can have. Each is dark enough for a white icon on top. */
enum class ProfileColor(val argb: Long) {
    Blue(0xFF1E5AC8),
    Teal(0xFF00796B),
    Green(0xFF2E7D32),
    Amber(0xFFA65F00),
    Orange(0xFFD84315),
    Red(0xFFC62828),
    Pink(0xFFAD1457),
    Purple(0xFF6A1B9A),
}

/** The icons a Profile can have. The UI maps each to a Material icon. */
enum class ProfileIcon { Person, Home, Build, Email, ShoppingCart, Star, Favorite, Face }

/** Rules for a Profile's name. */
object ProfileName {
    const val MAX_LENGTH = 40

    /** The name to store, trimmed, or null when [input] can't be a name (blank, or too long). */
    fun clean(input: String): String? = input.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
