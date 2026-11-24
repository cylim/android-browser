package app.jrny.browser.download

import android.webkit.URLUtil
import app.jrny.browser.address.WebAddress
import app.jrny.browser.data.DownloadDao
import app.jrny.browser.data.DownloadEntity
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mozilla.geckoview.WebResponse

/** Where an offered file's bytes come from, once the person agrees. */
sealed interface DownloadSource {
    /**
     * The response GeckoView already started for it, with the page's cookies. Its [body] isn't read
     * until the person agrees, and is closed if they decline. Null when there is nothing to read.
     */
    class Response(val body: java.io.InputStream?) : DownloadSource {
        fun discard() {
            runCatching { body?.close() }
        }
    }

    /** Fetched from the page, as the page would fetch it: "Save image" (SPEC, "Link and image menu"). */
    data object FromPage : DownloadSource
}

/** A file a page offers for download, before the person has agreed to it. */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String,
    /** The size in bytes, or -1 when the site didn't say. */
    val size: Long,
    /** The web page that offered it, for the list when the file's own address isn't a web one. */
    val page: String? = null,
    val source: DownloadSource = DownloadSource.Response(null),
) {
    /** True when the file can be saved at all. */
    val supported: Boolean get() = source !is DownloadSource.Response || source.body != null

    /**
     * True for a file the engine fetched as the page offered it, which Okapi saves in the
     * background, with its progress on the list. One fetched from the page ("Save image") is saved
     * first, and only then does Okapi say so.
     */
    val savedInBackground: Boolean get() = source is DownloadSource.Response

    /** The address the list shows: the file's own web address, else the page's. */
    val listedAddress: String get() = url.takeIf(WebAddress::isWebAddress) ?: page.orEmpty()

    companion object {
        /**
         * The offer for the file GeckoView's [response] brings, named as its headers say. [page] is
         * the web page the link to it was on, if any.
         */
        fun of(response: WebResponse, page: String?): DownloadRequest {
            fun header(name: String) = response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            val disposition = header("Content-Disposition")
            val mimeType = header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
            val size = header("Content-Length")?.trim()?.toLongOrNull() ?: -1
            val suggested = suggestedFileName(disposition)
                ?: URLUtil.guessFileName(response.uri, disposition, mimeType.ifBlank { null })
            return DownloadRequest(response.uri, safeFileName(suggested), mimeType, size, page, DownloadSource.Response(response.body))
        }

        private const val MAX_NAME_LENGTH = 120
        private val LEADING_DOTS = Regex("^[._]*\\.[._]*")

        // RFC 6266: filename*=UTF-8''percent-encoded, filename="quoted" or filename=token.
        private val ENCODED_NAME = Regex("""filename\*\s*=\s*[^';]*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
        private val QUOTED_NAME = Regex("""filename\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        private val PLAIN_NAME = Regex("""filename\s*=\s*([^;"]+)""", RegexOption.IGNORE_CASE)

        /**
         * The file name a site gave in its `Content-Disposition` header, or null when it gave
         * none. Android's own guess would swap the extension for one that fits the content type
         * (`notes.txt` becomes `notes.bin`), so the site's name is taken as it is.
         */
        fun suggestedFileName(contentDisposition: String?): String? {
            if (contentDisposition.isNullOrBlank()) return null
            val encoded = ENCODED_NAME.find(contentDisposition)?.groupValues?.get(1)?.trim()
            if (encoded != null) {
                val decoded = runCatching { java.net.URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded
            }
            val name = QUOTED_NAME.find(contentDisposition)?.groupValues?.get(1)
                ?: PLAIN_NAME.find(contentDisposition)?.groupValues?.get(1)
            return name?.trim()?.takeIf { it.isNotEmpty() }
        }

        /**
         * A file name that is safe to save under, from the name a site suggests: no folders, no
         * characters a file system rejects, no leading dots (hidden files), and not too long. The
         * extension is kept when the name has to be cut.
         */
        fun safeFileName(suggested: String): String {
            val cleaned = suggested
                .substringAfterLast('/').substringAfterLast('\\')
                .map { if (it.isISOControl() || it in "\"*:<>?|") '_' else it }
                .joinToString("")
                .trim()
                // Leading dots (hidden files), and the "_" GeckoView leaves where a "../" was.
                .replace(LEADING_DOTS, "").trim()
            if (cleaned.isEmpty()) return "download"
            if (cleaned.length <= MAX_NAME_LENGTH) return cleaned
            val extension = cleaned.substringAfterLast('.', "").takeIf { it.length in 1..10 }?.let { ".$it" }.orEmpty()
            return cleaned.take(MAX_NAME_LENGTH - extension.length) + extension
        }
    }
}

/**
 * A download in a Profile's list. [downloadId] names it for the [Downloader]. A file Okapi saved
 * itself, one the page made, has [fileUri] instead, its address in the Downloads folder.
 */
data class Download(
    val id: Long,
    val downloadId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val startedAt: Long,
    val fileUri: String? = null,
)

/** Where a download stands. */
sealed interface DownloadStatus {
    /** Still downloading. [fraction] is 0..1, or null while the size isn't known. */
    data class Running(val fraction: Float?) : DownloadStatus
    data class Done(val bytes: Long) : DownloadStatus
    data object Failed : DownloadStatus

    /** The download service no longer knows it: the file was removed outside the app. */
    data object Gone : DownloadStatus
}

/**
 * What keeps the files, in the device's Downloads folder ([SystemDownloader]); tests use a fake.
 * Downloads that Android's download service started before Okapi used GeckoView still have a
 * [Download.downloadId] and are asked about with [statuses].
 */
interface Downloader {
    /**
     * Starts writing [body], a file of [size] bytes (or -1), into a new file named [fileName] in
     * the Downloads folder, and returns that file's address at once, or null when it can't be made.
     * The writing goes on in the background, and [savedStatus] tells how far it got.
     */
    fun receive(fileName: String, mimeType: String, size: Long, body: java.io.InputStream): String?

    /** Where each of [ids], downloads of Android's download service, stands. */
    fun statuses(ids: List<Long>): Map<Long, DownloadStatus>

    /** Opens the finished file in an app that can show it. False when none can, or it isn't there. */
    fun open(download: Download): Boolean

    /** Stops a running download of Android's download service and discards what it fetched. */
    fun cancel(downloadId: Long)

    /** Stops writing the file at [fileUri], if it is still being written, and deletes it. */
    fun cancelSaved(fileUri: String)

    /**
     * Saves [file] as a finished file named [fileName] in the Downloads folder. Returns the saved
     * file's address, or null when it couldn't be saved.
     */
    fun save(fileName: String, mimeType: String, file: java.io.File): String?

    /** Where the file at [fileUri] stands: still being written, done, failed, or gone. */
    fun savedStatus(fileUri: String): DownloadStatus
}

/** Each Profile's list of downloads (SPEC, "Browsing features"). */
class DownloadRepository(private val dao: DownloadDao, private val now: () -> Long = System::currentTimeMillis) {

    /** [profile]'s downloads, newest first. */
    fun downloads(profile: ProfileId): Flow<List<Download>> = dao.observe(profile.toLong()).map { rows ->
        rows.map { Download(it.id, it.downloadId, it.url, it.fileName, it.mimeType, it.startedAt, it.fileUri) }
    }

    /**
     * Lists a file in [profile], saved or being saved at [fileUri]. Its address on the list is the
     * file's web address, or the page's when it has none: a `data:` address can be as long as the
     * file. Returns false when the Profile is gone.
     */
    suspend fun addSaved(profile: ProfileId, fileUri: String, request: DownloadRequest): Boolean = dao.add(
        DownloadEntity(
            profileId = profile.toLong(),
            downloadId = 0,
            url = request.listedAddress,
            fileName = request.fileName,
            mimeType = request.mimeType,
            startedAt = now(),
            fileUri = fileUri,
        ),
    )

    /** Takes a download off its Profile's list. The file stays where it is. */
    suspend fun remove(download: Download) = dao.delete(download.id)

    private fun ProfileId.toLong(): Long = value.toLong()
}

/** An image's bytes and its MIME type, for "Copy image" and "Share image". */
class FetchedImage(val bytes: ByteArray, val mimeType: String)
