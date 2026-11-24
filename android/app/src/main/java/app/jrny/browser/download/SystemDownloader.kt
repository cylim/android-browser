package app.jrny.browser.download

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Saves files into the device's shared Downloads folder, through MediaStore, which needs no
 * storage permission for the app's own files.
 *
 * A file a page offers comes as GeckoView's response, fetched with the page's Profile, so the
 * file arrives as the page would get it. Okapi writes it out in the background ([receive]) while
 * the file is marked pending, so no other app sees half of it. The writing belongs to the app's
 * process: if Android ends the process meanwhile, the file is left unfinished and is reported as
 * failed.
 *
 * Downloads Android's own download service made for earlier versions are still shown, opened
 * and removed through it.
 */
class SystemDownloader(private val context: Context) : Downloader {

    private val manager: DownloadManager get() = context.getSystemService(DownloadManager::class.java)

    /** How far each file being written has got. */
    private class Writing(val size: Long) {
        @Volatile var written = 0L
        @Volatile var cancelled = false
    }

    private val writing = ConcurrentHashMap<String, Writing>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val writer = Executors.newCachedThreadPool()

    override fun receive(fileName: String, mimeType: String, size: Long, body: InputStream): String? {
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            if (mimeType.isNotBlank()) put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val file = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, details) }.getOrNull()
        if (file == null) {
            runCatching { body.close() }
            return null
        }
        val address = file.toString()
        val progress = Writing(size)
        writing[address] = progress
        writer.execute {
            val done = try {
                resolver.openOutputStream(file)?.use { out ->
                    body.use { input ->
                        val buffer = ByteArray(BUFFER)
                        while (!progress.cancelled) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            progress.written += read
                        }
                    }
                } != null && !progress.cancelled
            } catch (_: Exception) {
                false
            }
            if (done) {
                resolver.update(file, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } else {
                runCatching { resolver.delete(file, null, null) }
                if (!progress.cancelled) failed += address
            }
            writing.remove(address)
        }
        return address
    }

    override fun cancelSaved(fileUri: String) {
        writing[fileUri]?.cancelled = true
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

    override fun save(fileName: String, mimeType: String, file: java.io.File): String? {
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            if (mimeType.isNotBlank()) put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val saved = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, details) ?: return null
        return try {
            resolver.openOutputStream(saved)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("No stream")
            resolver.update(saved, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            saved.toString()
        } catch (_: Exception) {
            resolver.delete(saved, null, null)
            null
        }
    }

    override fun savedStatus(fileUri: String): DownloadStatus {
        writing[fileUri]?.let { progress ->
            return DownloadStatus.Running(if (progress.size > 0) (progress.written.toFloat() / progress.size).coerceIn(0f, 1f) else null)
        }
        if (fileUri in failed) return DownloadStatus.Failed
        val columns = arrayOf(MediaStore.Downloads.SIZE, MediaStore.Downloads.IS_PENDING)
        return runCatching {
            context.contentResolver.query(fileUri.toUri(), columns, null, null, null)?.use { rows ->
                when {
                    !rows.moveToFirst() -> null
                    // Left unfinished when the app's process ended.
                    rows.getInt(1) == 1 -> DownloadStatus.Failed
                    else -> DownloadStatus.Done(rows.getLong(0))
                }
            }
        }.getOrNull() ?: DownloadStatus.Gone
    }

    override fun open(download: Download): Boolean {
        val saved = download.fileUri
        val file = saved?.toUri() ?: manager.getUriForDownloadedFile(download.downloadId) ?: return false
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file, download.mimeType.ifBlank { if (saved != null) context.contentResolver.getType(file) else manager.getMimeTypeForDownloadedFile(download.downloadId) })
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

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
