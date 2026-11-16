package app.jrny.browser.download

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri

/**
 * Downloads with Android's own download service. It keeps going when the app is closed, shows
 * its own progress notification, and saves into the device's shared Downloads folder, which
 * needs no storage permission.
 */
class SystemDownloader(private val context: Context) : Downloader {

    private val manager: DownloadManager get() = context.getSystemService(DownloadManager::class.java)

    override fun start(request: DownloadRequest, cookies: String?): Long {
        val download = DownloadManager.Request(request.url.toUri())
            .setTitle(request.fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
        if (request.mimeType.isNotBlank()) download.setMimeType(request.mimeType)
        if (request.userAgent.isNotBlank()) download.addRequestHeader("User-Agent", request.userAgent)
        if (!request.referrer.isNullOrBlank()) download.addRequestHeader("Referer", request.referrer)
        // The Profile's cookies for this address, so a file behind that Profile's sign-in downloads
        // as the page would get it. The address is the one the redirects end at (resolve): the
        // download service would pass this header on to wherever a redirect leads.
        if (!cookies.isNullOrBlank()) download.addRequestHeader("Cookie", cookies)
        return manager.enqueue(download)
    }

    override fun statuses(ids: List<Long>): Map<Long, DownloadStatus> {
        if (ids.isEmpty()) return emptyMap()
        val found = mutableMapOf<Long, DownloadStatus>()
        manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { rows ->
            val id = rows.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val status = rows.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val soFar = rows.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = rows.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (rows.moveToNext()) {
                val done = rows.getLong(soFar)
                val size = rows.getLong(total)
                found[rows.getLong(id)] = when (rows.getInt(status)) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Done(done)
                    DownloadManager.STATUS_FAILED -> DownloadStatus.Failed
                    else -> DownloadStatus.Running(if (size > 0) (done.toFloat() / size).coerceIn(0f, 1f) else null)
                }
            }
        }
        return ids.associateWith { found[it] ?: DownloadStatus.Gone }
    }

    override fun open(download: Download): Boolean {
        val file = manager.getUriForDownloadedFile(download.downloadId) ?: return false
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file, download.mimeType.ifBlank { manager.getMimeTypeForDownloadedFile(download.downloadId) })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(view)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    override fun cancel(downloadId: Long) {
        manager.remove(downloadId)
    }

    override fun resolve(url: String, userAgent: String, cookiesFor: (String) -> String?): String =
        DownloadRedirects.resolve(url, userAgent, cookiesFor)
}
