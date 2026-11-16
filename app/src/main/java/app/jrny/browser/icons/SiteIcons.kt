package app.jrny.browser.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.tab.displayHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The icons of the sites a Profile has shown, for its Tab list (SPEC, "Site icons"). One small
 * picture per site, in a folder of its own for each Profile under [root], which is the app's
 * private storage: never backed up, and never visible from another Profile.
 *
 * A file is named by a digest of the site's name, so the folder doesn't read as a list of sites.
 */
class SiteIconStore(private val root: File, private val io: CoroutineDispatcher = Dispatchers.IO) {

    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val changes = MutableStateFlow(0)

    /** Grows whenever an icon is saved or removed, so a list showing icons looks again. */
    val version: StateFlow<Int> = changes.asStateFlow()

    /** The icon kept in memory for the site of [url] in [profile], without touching the disk. */
    fun cached(profile: ProfileId, url: String?): Bitmap? = key(profile, url)?.let(memory::get)

    /** The icon of the site of [url] in [profile], or null when it has none yet. */
    suspend fun load(profile: ProfileId, url: String?): Bitmap? {
        val key = key(profile, url) ?: return null
        memory.get(key)?.let { return it }
        return withContext(io) {
            val file = file(profile, displayHost(url))
            if (!file.exists()) null else BitmapFactory.decodeFile(file.path)?.also { memory.put(key, it) }
        }
    }

    /**
     * Keeps [icon] as the icon of the site of [url] in [profile], scaled down to [MAX_SIZE]. An
     * address that isn't a web page's has no site, and nothing is kept.
     */
    suspend fun save(profile: ProfileId, url: String, icon: Bitmap) {
        val key = key(profile, url) ?: return
        val small = if (icon.width <= MAX_SIZE && icon.height <= MAX_SIZE) {
            icon
        } else {
            val scale = MAX_SIZE.toFloat() / maxOf(icon.width, icon.height)
            Bitmap.createScaledBitmap(icon, (icon.width * scale).toInt().coerceAtLeast(1), (icon.height * scale).toInt().coerceAtLeast(1), true)
        }
        withContext(io) {
            val file = file(profile, displayHost(url))
            file.parentFile?.mkdirs()
            // Written beside the file and moved over it, so a reader never sees half a picture.
            val draft = File(file.path + ".tmp")
            draft.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            draft.renameTo(file)
        }
        memory.put(key, small)
        changes.update { it + 1 }
    }

    /**
     * Removes [profile]'s icons except those of the sites of [keep]: the pages it still has on
     * record. Called when its history is cleared, so no visited site stays behind as a picture.
     */
    suspend fun keepOnly(profile: ProfileId, keep: Collection<String>) {
        val names = keep.map { displayHost(it) }.filter { it.isNotEmpty() }.map(::fileName).toSet()
        withContext(io) { folder(profile).listFiles()?.filter { it.name !in names }?.forEach(File::delete) }
        forget(profile)
    }

    /** Removes every icon of [profile], when the Profile is deleted. */
    suspend fun clear(profile: ProfileId) {
        withContext(io) { folder(profile).deleteRecursively() }
        forget(profile)
    }

    private fun forget(profile: ProfileId) {
        memory.snapshot().keys.filter { it.startsWith(profile.value + "/") }.forEach(memory::remove)
        changes.update { it + 1 }
    }

    private fun key(profile: ProfileId, url: String?): String? =
        displayHost(url).takeIf { it.isNotEmpty() }?.let { profile.value + "/" + it.lowercase() }

    private fun folder(profile: ProfileId) = File(root, profile.value)

    private fun file(profile: ProfileId, host: String) = File(folder(profile), fileName(host))

    private fun fileName(host: String): String =
        MessageDigest.getInstance("SHA-256").digest(host.lowercase().toByteArray()).take(16).joinToString("") { "%02x".format(it) } + ".png"

    companion object {
        /** An icon's longer side, in pixels: enough for a 20dp mark on any screen. */
        const val MAX_SIZE = 96

        private const val MEMORY_ENTRIES = 256
    }
}
