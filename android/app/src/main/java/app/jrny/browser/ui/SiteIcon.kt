package app.jrny.browser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jrny.browser.address.WebAddress
import app.jrny.browser.tab.displayHost

/**
 * A small mark for the site of [url] in a list row: the site's own icon when the Active Profile
 * has one for it ([LocalSiteIcons]), else the first letter of its name on a square of a color
 * picked by that name, so the same site looks the same in every row. A page without a site (a
 * new Tab) gets an empty square. [dimmed] is for a Pinned Tab that is closed.
 */
@Composable
fun SiteIcon(url: String?, modifier: Modifier = Modifier, size: Dp = SITE_ICON_SIZE, dimmed: Boolean = false) {
    // The site's own icon, once the Profile has seen it. Until then, and for a site without one,
    // the letter stands in.
    val icons = LocalSiteIcons.current
    // Keyed, so a row that goes on to another site starts from that site's own icon or letter,
    // not from the icon of the site it showed before.
    val icon by key(url, icons) { produceState(icons?.cached?.invoke(url)) { value = icons?.load?.invoke(url) } }
    icon?.let { picture ->
        Image(
            bitmap = picture,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .alpha(if (dimmed) 0.45f else 1f)
                .clip(RoundedCornerShape(4.dp))
                .clearAndSetSemantics { testTag = "site-icon" },
        )
        return
    }
    val site = siteName(url)
    val color = if (site.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else SITE_COLORS[Math.floorMod(site.hashCode(), SITE_COLORS.size)]
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.45f else 1f)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            // The row names the page already.
            .clearAndSetSemantics { testTag = "site-icon" },
    ) {
        if (site.isNotEmpty()) {
            Text(
                site.first().uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.55f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = (size.value * 0.55f).sp,
            )
        }
    }
}

/**
 * What names the site of [url]: its registrable domain, so `mail.example.com` and
 * `www.example.com` share a letter and a color. Empty when the address isn't a web page's.
 */
internal fun siteName(url: String?): String {
    val host = displayHost(url)
    return if (host.isEmpty()) "" else WebAddress.splitAtRegistrableDomain(host).second.lowercase()
}

/**
 * Where the Active Profile's site icons come from. [cached] answers at once from memory, so a
 * row scrolled back into view doesn't flash its letter first; [load] reads the rest. A new one is
 * made whenever an icon is saved, so the rows look again.
 */
class SiteIconSource(val cached: (String?) -> ImageBitmap?, val load: suspend (String?) -> ImageBitmap?)

/** The Active Profile's site icons, or null where there are none to show (previews, tests). */
val LocalSiteIcons = staticCompositionLocalOf<SiteIconSource?> { null }

/** The mark's size in a list row. */
val SITE_ICON_SIZE = 20.dp

/**
 * The letter and color that stand for the site of [url] where it has no icon, as the Tab list
 * draws them, or null for an address that isn't a web page's.
 */
fun siteLetterMark(url: String?): Pair<String, Color>? {
    val site = siteName(url)
    if (site.isEmpty()) return null
    return site.first().uppercase() to SITE_COLORS[Math.floorMod(site.hashCode(), SITE_COLORS.size)]
}

/** Dark enough for a white letter on top, as the Profile colors are. */
private val SITE_COLORS = listOf(
    Color(0xFF3B5BA9), Color(0xFF00796B), Color(0xFF2E7D32), Color(0xFF8D5A00),
    Color(0xFFBF4A1F), Color(0xFFB3261E), Color(0xFFA1326A), Color(0xFF6B3FA0),
    Color(0xFF455A64), Color(0xFF5D4037),
)
