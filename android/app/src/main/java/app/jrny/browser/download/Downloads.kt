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
    /**
     * For a `blob:` file, the name under which the page keeps its own copy of it until the person
     * answers (SPEC, "Browsing features"). Null for any other file.
     */
    val pageCopy: String? = null,
) {
    /** A web address goes to Android's download service. */
    val fromWeb: Boolean get() = WebAddress.isWebAddress(url)

    /** A file the page made itself, which Okapi saves: a `data:` address, or a `blob:` the page kept a copy of. */
    val fromPage: Boolean get() = url.startsWith("data:", ignoreCase = true) || (url.startsWith("blob:", ignoreCase = true) && pageCopy != null)

    /** True when the file can be saved at all. */
    val supported: Boolean get() = fromWeb || fromPage

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

    /**
     * Saves [file], a file a page made itself, as a finished file named [fileName] in the
     * Downloads folder. Returns the saved file's address, or null when it couldn't be saved.
     */
    fun save(fileName: String, mimeType: String, file: java.io.File): String? = null

    /** Where a file [save] made stands: done, or gone when it was removed. */
    fun savedStatus(fileUri: String): DownloadStatus = DownloadStatus.Gone

    /**
     * The address a download of [url] ends up at after its redirects. [cookiesFor] gives the
     * cookies that belong to an address, so each hop gets only its own (see [DownloadRedirects]).
     */
    fun resolve(url: String, userAgent: String, cookiesFor: (String) -> String?): String = url
}

/** Each Profile's list of downloads (SPEC, "Browsing features"). */
class DownloadRepository(private val dao: DownloadDao, private val now: () -> Long = System::currentTimeMillis) {

    /** [profile]'s downloads, newest first. */
    fun downloads(profile: ProfileId): Flow<List<Download>> = dao.observe(profile.toLong()).map { rows ->
        rows.map { Download(it.id, it.downloadId, it.url, it.fileName, it.mimeType, it.startedAt, it.fileUri) }
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

    /**
     * Lists a file Okapi saved itself in [profile], at [fileUri]. Its address on the list is the
     * page's, not the file's: a `data:` address can be as long as the file. Returns false when
     * the Profile is gone.
     */
    suspend fun addSaved(profile: ProfileId, fileUri: String, request: DownloadRequest, page: String?): Boolean = dao.add(
        DownloadEntity(
            profileId = profile.toLong(),
            downloadId = 0,
            url = page.orEmpty(),
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
