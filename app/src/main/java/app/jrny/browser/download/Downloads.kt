package app.jrny.browser.download

import app.jrny.browser.address.WebAddress
import app.jrny.browser.data.DownloadDao
import app.jrny.browser.data.DownloadEntity
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A file a page offers for download, before the person has agreed to it. */
data class DownloadRequest(
    val url: String,
    val userAgent: String,
    val fileName: String,
    val mimeType: String,
    /** The size in bytes, or -1 when the site didn't say. */
    val size: Long,
    /** The page the file was linked from, which some sites check before they hand the file out. */
    val referrer: String? = null,
) {
    /** Android's download service fetches only web addresses, so `blob:` and `data:` files can't be saved yet. */
    val supported: Boolean get() = WebAddress.isWebAddress(url)

    companion object {
        private const val MAX_NAME_LENGTH = 120

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
                .trim().trimStart('.').trim()
            if (cleaned.isEmpty()) return "download"
            if (cleaned.length <= MAX_NAME_LENGTH) return cleaned
            val extension = cleaned.substringAfterLast('.', "").takeIf { it.length in 1..10 }?.let { ".$it" }.orEmpty()
            return cleaned.take(MAX_NAME_LENGTH - extension.length) + extension
        }
    }
}

/** A download in a Profile's list. [downloadId] names it for the [Downloader]. */
data class Download(val id: Long, val downloadId: Long, val url: String, val fileName: String, val mimeType: String, val startedAt: Long)

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
 * What fetches and keeps the files. In the app it is Android's own download service
 * ([SystemDownloader]), which saves into the device's Downloads folder; tests use a fake.
 */
interface Downloader {
    /**
     * Starts fetching [request], sending [cookies] (the Profile's, for files behind a sign-in),
     * and returns the download's id.
     */
    fun start(request: DownloadRequest, cookies: String?): Long

    /** Where each of [ids] stands. */
    fun statuses(ids: List<Long>): Map<Long, DownloadStatus>

    /** Opens the finished file in an app that can show it. False when none can, or it isn't there. */
    fun open(download: Download): Boolean

    /** Stops a running download and discards what it fetched. */
    fun cancel(downloadId: Long)
}

/** Each Profile's list of downloads (SPEC, "Browsing features"). */
class DownloadRepository(private val dao: DownloadDao, private val now: () -> Long = System::currentTimeMillis) {

    /** [profile]'s downloads, newest first. */
    fun downloads(profile: ProfileId): Flow<List<Download>> = dao.observe(profile.toLong()).map { rows ->
        rows.map { Download(it.id, it.downloadId, it.url, it.fileName, it.mimeType, it.startedAt) }
    }

    /** Lists a started download in [profile]. Returns false when the Profile is gone. */
    suspend fun add(profile: ProfileId, downloadId: Long, request: DownloadRequest): Boolean = dao.add(
        DownloadEntity(
            profileId = profile.toLong(),
            downloadId = downloadId,
            url = request.url,
            fileName = request.fileName,
            mimeType = request.mimeType,
            startedAt = now(),
        ),
    )

    /** Takes a download off its Profile's list. The file stays where it is. */
    suspend fun remove(download: Download) = dao.delete(download.id)

    private fun ProfileId.toLong(): Long = value.toLong()
}
