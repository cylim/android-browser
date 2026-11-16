package app.jrny.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.jrny.browser.settings.ThemeChoice

/** Whether [this] choice means dark right now: its own answer, or the device's. */
@Composable
fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.System -> isSystemInDarkTheme()
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}

/** The app's colors, from the device's own palette, light or dark as [theme] says. */
@Composable
fun JrnyTheme(theme: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (theme.isDark()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
