package app.jrny.browser.tab

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The files a page's file field gets (SPEC, "File upload"), as GeckoView can read them: files on
 * disk only. The camera app writes into the app's cache, so its photos go as they are. A file
 * another app shares is a `content:` address, so it is copied into the app's cache first, under
 * its own name. Both folders are emptied the next time the app starts.
 */
object UploadFiles {
    /** Where the camera app writes the photos and videos it takes for a file field. */
    const val CAPTURE_FOLDER = "captures"

    /** Where the copies of shared files go. */
    const val COPY_FOLDER = "uploads"

    /**
     * [file] as the page may read it, or null when it can't be read. A file on disk is taken only
     * from the camera's folder: an app answering the picker can't hand the page Okapi's own data.
     */
    fun readable(context: Context, file: Uri): Uri? = when (file.scheme) {
        ContentResolver.SCHEME_FILE -> file.takeIf { inCaptures(context, it) }
        ContentResolver.SCHEME_CONTENT -> copy(context, file)
        else -> null
    }

    private fun inCaptures(context: Context, file: Uri): Boolean {
        val path = file.path ?: return false
        // A path that can't be resolved, such as one with a NUL in it, is no camera photo.
        return try {
            val folder = File(context.cacheDir, CAPTURE_FOLDER).canonicalFile
            val target = File(path).canonicalFile
            target.parentFile == folder && target.isFile
        } catch (_: IOException) {
            false
        }
    }

    private fun copy(context: Context, file: Uri): Uri? {
        val resolver = context.contentResolver
        // A folder for each copy, so two files of the same name don't meet.
        val folder = File(File(context.cacheDir, COPY_FOLDER), UUID.randomUUID().toString())
        return try {
            val input = resolver.openInputStream(file) ?: return null
            folder.mkdirs()
            val copy = File(folder, nameOf(resolver, file))
            input.use { source -> copy.outputStream().use { source.copyTo(it) } }
            Uri.fromFile(copy)
        } catch (_: IOException) {
            folder.deleteRecursively()
            null
        } catch (_: SecurityException) {
            folder.deleteRecursively()
            null
        }
    }

    /** The name the sharing app gives the file, which the page sees, with a suffix for its kind when it has none. */
    private fun nameOf(resolver: ContentResolver, file: Uri): String {
        val given = try {
            resolver.query(file, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { row ->
                if (row.moveToFirst() && !row.isNull(0)) row.getString(0) else null
            }
        } catch (_: RuntimeException) {
            null
        }
        val name = given.orEmpty().substringAfterLast('/').filterNot { it.isISOControl() }.trim().take(MAX_NAME)
            .takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "file"
        if ('.' in name.drop(1)) return name
        val suffix = resolver.getType(file)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (suffix == null) name else "$name.$suffix"
    }

    private const val MAX_NAME = 120
}
