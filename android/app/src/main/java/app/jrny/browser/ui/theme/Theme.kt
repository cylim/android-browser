package app.jrny.browser.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import app.jrny.browser.settings.ThemeChoice

/** Whether [this] choice means dark right now: its own answer, or the device's. */
@Composable
fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.System -> isSystemInDarkTheme()
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}

/**
 * The colors of the browser while a Profile with [color] is active: the app's own, with the
 * accents taken from the Profile, so the selected choices, switches, buttons and links are in
 * its color too. The Profile's color is used as it is on a light surface, and lightened on a
 * dark one, where it would be too dim to read.
 */
@Composable
fun ProfileTheme(color: Color, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < 0.5f
    val accent = animateColorAsState(color, tween(250), label = "profile accent").value
    val colors = if (dark) {
        base.copy(
            primary = lerp(accent, Color.White, 0.6f),
            onPrimary = lerp(accent, Color.Black, 0.55f),
            primaryContainer = lerp(base.surface, accent, 0.4f),
            onPrimaryContainer = lerp(accent, Color.White, 0.85f),
            secondaryContainer = lerp(base.surface, accent, 0.28f),
            onSecondaryContainer = lerp(accent, Color.White, 0.88f),
            inversePrimary = accent,
        )
    } else {
        base.copy(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(base.surface, accent, 0.18f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.45f),
            secondaryContainer = lerp(base.surface, accent, 0.12f),
            onSecondaryContainer = lerp(accent, Color.Black, 0.5f),
            inversePrimary = lerp(accent, Color.White, 0.6f),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** The app's colors, from the device's own palette, light or dark as [theme] says. */
@Composable
fun JrnyTheme(theme: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (theme.isDark()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
