package app.jrny.browser.ui

import android.graphics.Color
import android.view.WindowManager
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
 *
 * With [fullscreen], a video fills the screen, and the navigation bar hides too (SPEC,
 * "Fullscreen video").
 */
@Composable
fun HideStatusBar(fullscreen: Boolean = false) {
    val window = LocalActivity.current?.window ?: return
    val topCutout = WindowInsets.displayCutout.getTop(LocalDensity.current) > 0
    DisposableEffect(window, topCutout, fullscreen) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when {
            fullscreen -> controller.hide(WindowInsetsCompat.Type.systemBars())
            topCutout -> controller.show(WindowInsetsCompat.Type.systemBars())
            else -> {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * While [secure], the window can't be captured: no screenshots, no screen recording, and a blank
 * card in the recent-apps list (SPEC, "Private Tabs"). Dialogs, menus and sheets opened from the
 * window take the same protection.
 */
@Composable
fun SecureWindow(secure: Boolean) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window, secure) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Dark mode doesn't recreate the activity (`configChanges` includes `uiMode`), so the status and
 * navigation bar icons are switched between light and dark here.
 */
@Composable
fun SystemBarsFollowTheme(dark: Boolean = isSystemInDarkTheme()) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
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

/**
 * How far something floating over an edge-to-edge page stays from the bottom edge: [gap], or
 * enough to sit [gap] / 2 above the gesture handle. 3-button navigation and the keyboard already
 * push the page up, so they add nothing here.
 */
@Composable
fun floatingBottomSpace(gap: Dp): Dp {
    val density = LocalDensity.current
    if (WindowInsets.tappableElement.getBottom(density) > 0 || WindowInsets.ime.getBottom(density) > 0) return gap
    val handle = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }.coerceAtMost(GESTURE_HANDLE_SPACE)
    return maxOf(gap, handle + gap / 2)
}
