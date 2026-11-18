package app.jrny.browser.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import app.jrny.browser.MainActivity
import app.jrny.browser.data.ShortcutDao
import app.jrny.browser.data.ShortcutEntity
import app.jrny.browser.profile.ProfileId
import java.util.UUID

/** A Home Screen Shortcut's page and the Profile it opens in (GLOSSARY, "Home Screen Shortcut"). */
data class HomeShortcut(val id: String, val profileId: ProfileId, val url: String, val title: String)

/** The Home Screen Shortcuts the app made, by the random id the launcher holds. */
class ShortcutRepository(private val dao: ShortcutDao, private val now: () -> Long = System::currentTimeMillis) {

    /** Keeps a new shortcut to [url] in [profile], and returns it, or null when the Profile is gone. */
    suspend fun add(profile: ProfileId, url: String, title: String): HomeShortcut? {
        val shortcut = HomeShortcut("okapi-" + UUID.randomUUID(), profile, url, title)
        val kept = dao.add(ShortcutEntity(shortcut.id, profile.value.toLong(), url, title, now()))
        return shortcut.takeIf { kept }
    }

    suspend fun find(id: String): HomeShortcut? =
        dao.get(id)?.let { HomeShortcut(it.id, ProfileId(it.profileId.toString()), it.url, it.title) }

    /** Forgets [shortcut], whose pin request never reached the launcher. */
    suspend fun remove(shortcut: HomeShortcut) = dao.delete(shortcut.id)

    /** The ids of [profile]'s shortcuts, to disable when it is deleted. */
    suspend fun idsOf(profile: ProfileId): List<String> = dao.idsOf(profile.value.toLong())
}

/**
 * Pins shortcuts to the launcher (SPEC, "Add to Home screen"). A shortcut's intent names only its
 * id; [MainActivity] looks it up.
 */
object HomeShortcuts {
    const val ACTION_OPEN = "app.jrny.browser.action.OPEN_SHORTCUT"
    const val EXTRA_ID = "shortcut"
    private const val ICON_PX = 192

    /** True when the launcher can pin shortcuts at all. */
    fun supported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Asks the launcher to pin [shortcut], with the site's [siteIcon] (or its first letter on
     * [siteColor]) and a badge in the Profile's [profileColor] with its [profileMark].
     */
    fun pin(context: Context, shortcut: HomeShortcut, siteIcon: Bitmap?, siteLetter: String, siteColor: Int, profileColor: Int, profileMark: String): Boolean {
        val intent = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN).putExtra(EXTRA_ID, shortcut.id)
        val info = ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(shortcut.title.ifBlank { shortcut.url }.take(25))
            .setLongLabel(shortcut.title.ifBlank { shortcut.url })
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(siteIcon, siteLetter, siteColor, profileColor, profileMark)))
            .setIntent(intent)
            .build()
        // Android refuses (throws) when the app has just left the foreground.
        return try {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** Stops [ids] from opening anything, after their Profile was deleted: the launcher says [message] instead. */
    fun disable(context: Context, ids: List<String>, message: String) {
        if (ids.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, ids, message)
    }

    /**
     * An adaptive icon: the site's icon, or its letter, in the middle of a white square, and the
     * Profile's badge at its lower left: the launcher puts the app's own badge on the lower right.
     * A launcher may mask an adaptive icon to a circle of two thirds its width, so the site's square
     * and the badge both sit inside that circle.
     */
    private fun icon(siteIcon: Bitmap?, letter: String, siteColor: Int, profileColor: Int, mark: String): Bitmap {
        val bitmap = createBitmap(ICON_PX, ICON_PX)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val center = ICON_PX / 2f
        // The circle a launcher keeps: two thirds of the icon wide.
        val visible = ICON_PX / 3f
        val half = visible * 0.6f
        val inner = RectF(center - half, center - half, center + half, center + half)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (siteIcon != null) {
            canvas.drawBitmap(siteIcon, null, inner, paint)
        } else {
            paint.color = siteColor
            canvas.drawRoundRect(inner, half * 0.25f, half * 0.25f, paint)
            drawCentered(canvas, letter, inner.centerX(), inner.centerY(), inner.height() * 0.55f, android.graphics.Color.WHITE)
        }
        // The Profile's badge, on the square's lower left corner, inside the kept circle.
        val radius = visible * 0.26f
        val offset = (visible - radius - BADGE_RING) / Math.sqrt(2.0).toFloat()
        val cx = center - offset
        val cy = center + offset
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(cx, cy, radius + BADGE_RING, paint)
        paint.color = profileColor
        canvas.drawCircle(cx, cy, radius, paint)
        drawCentered(canvas, mark, cx, cy, radius * 1.1f, android.graphics.Color.WHITE)
        return bitmap
    }

    /** The white ring around the badge, in pixels. */
    private const val BADGE_RING = 3f

    private fun drawCentered(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, x, y - bounds.exactCenterY(), paint)
    }
}
