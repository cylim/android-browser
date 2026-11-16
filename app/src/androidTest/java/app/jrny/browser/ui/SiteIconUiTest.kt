package app.jrny.browser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A row's site mark: the site's own icon once there is one, a letter until then. */
@RunWith(AndroidJUnit4::class)
class SiteIconUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val red = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.Red.toArgb()) }.asImageBitmap()

    /** The color in the middle of the mark tagged [tag]. */
    private fun middleOf(tag: String): Int {
        val picture = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return picture.getPixel(picture.width / 2, picture.height / 2)
    }

    @Test
    fun aSiteShowsItsOwnIconOnceTheProfileHasOneAndALetterUntilThen() {
        var icons by mutableStateOf(SiteIconSource(cached = { null }, load = { null }))
        compose.setContent {
            JrnyTheme {
                CompositionLocalProvider(LocalSiteIcons provides icons) {
                    Column {
                        Box(Modifier.testTag("known")) { SiteIcon("https://example.com/") }
                        Box(Modifier.testTag("unknown")) { SiteIcon("https://example.org/") }
                    }
                }
            }
        }
        // No icon yet: the letter's square, which is never plain red.
        assertNotEquals(Color.Red.toArgb(), middleOf("known"))

        // The page has shown its icon: a new source, and the row looks again.
        icons = SiteIconSource(cached = { null }, load = { url -> red.takeIf { url == "https://example.com/" } })
        compose.waitUntil(5_000) { middleOf("known") == Color.Red.toArgb() }
        assertNotEquals(Color.Red.toArgb(), middleOf("unknown"))

        // An icon already in memory shows at once, without the letter first.
        icons = SiteIconSource(cached = { url -> red.takeIf { url == "https://example.org/" } }, load = { url -> red.takeIf { url == "https://example.org/" } })
        compose.waitForIdle()
        assertEquals(Color.Red.toArgb(), middleOf("unknown"))
    }

    @Test
    fun withoutASourceThereIsOnlyTheLetter() {
        compose.setContent { JrnyTheme { Box(Modifier.testTag("mark")) { SiteIcon("https://example.com/") } } }
        assertNotEquals(Color.Red.toArgb(), middleOf("mark"))
    }
}
