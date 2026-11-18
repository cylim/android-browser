package app.jrny.browser.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.MimeTypeMap
import android.webkit.WebView
import androidx.core.content.FileProvider
import app.jrny.browser.download.FetchedImage
import java.io.File

/**
 * Share, Copy and Print for a page, a link or an image (SPEC, "Share, Copy link and Print" and
 * "Link and image menu"). Only what is named goes along: an address with its title, or an image.
 */
object Sharing {

    /** Hands [address], and the page's [title] when it has one, to Android's share sheet. */
    fun shareAddress(context: Context, address: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, address)
            .apply { if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_TITLE, title) }
        start(context, Intent.createChooser(send, null))
    }

    /**
     * Puts [text] on the clipboard. From a Private Tab ([private]) it is marked sensitive, so
     * Android doesn't show it in its clipboard preview.
     */
    fun copy(context: Context, label: String, text: String, private: Boolean) {
        val clip = ClipData.newPlainText(label, text)
        if (private) clip.markSensitive()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    /** Hands [image] to Android's share sheet. */
    fun shareImage(context: Context, image: FetchedImage) {
        val file = imageFile(context, image) ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType(image.mimeType)
            .putExtra(Intent.EXTRA_STREAM, file)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(send, null))
    }

    /** Puts [image] on the clipboard. */
    fun copyImage(context: Context, image: FetchedImage, private: Boolean) {
        val file = imageFile(context, image) ?: return
        val clip = ClipData.newUri(context.contentResolver, "Image", file)
        if (private) clip.markSensitive()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    /** Opens Android's print dialog for the page in [webView], which can also save it as a PDF. */
    fun print(context: Context, webView: WebView, title: String) {
        val name = title.ifBlank { "Page" }
        context.getSystemService(PrintManager::class.java)
            .print(name, webView.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
    }

    /**
     * Writes [image] to the app's cache, where other apps can read it only through the address
     * they are handed. These files go the next time the app starts ([clearPageFiles]).
     */
    private fun imageFile(context: Context, image: FetchedImage) = try {
        val folder = File(context.cacheDir, SHARED_FOLDER).apply { mkdirs() }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(image.mimeType) ?: "img"
        val file = File(folder, "image-${System.currentTimeMillis()}.$extension").apply { writeBytes(image.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    } catch (_: Exception) {
        null
    }

    private fun ClipData.markSensitive() {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    const val SHARED_FOLDER = "shared"
}
