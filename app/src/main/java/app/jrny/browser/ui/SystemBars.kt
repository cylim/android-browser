package app.jrny.browser.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the status bar, so the page and controls run to the top edge with no reserved strip.
 * A swipe from the top edge shows it over the app for a moment.
 *
 * It stays visible when a camera cutout sits along the top edge (phones in portrait): the page
 * has to stay clear of the cutout anyway, so hiding the status bar would only lose the clock.
 */
@Composable
fun HideStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val topCutout = WindowInsets.displayCutout.getTop(LocalDensity.current) > 0
    DisposableEffect(window, topCutout) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (topCutout) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

/**
 * Dark mode doesn't recreate the activity (`configChanges` includes `uiMode`), so the status and
 * navigation bar icons are switched between light and dark here.
 */
@Composable
fun SystemBarsFollowTheme() {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val dark = isSystemInDarkTheme()
    DisposableEffect(activity, dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}

/** The most room the gesture handle gets below the controls. It's drawn about 14–18dp up. */
private val GESTURE_HANDLE_SPACE = 24.dp

/**
 * The space to leave below controls at the bottom edge:
 * - with the keyboard up, the keyboard's height plus [gap];
 * - with 3-button navigation, the buttons' height plus [gap];
 * - with gesture navigation, just enough for the handle, never less than [gap]. The handle
 *   draws over the app, so it needs no strip of its own.
 */
@Composable
fun bottomEdgeSpace(gap: Dp): Dp {
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    val buttons = WindowInsets.tappableElement.getBottom(density)
    val navigation = WindowInsets.navigationBars.getBottom(density)
    return with(density) {
        when {
            ime > 0 -> ime.toDp() + gap
            buttons > 0 -> buttons.toDp() + gap
            else -> navigation.toDp().coerceIn(gap, GESTURE_HANDLE_SPACE)
        }
    }
}
