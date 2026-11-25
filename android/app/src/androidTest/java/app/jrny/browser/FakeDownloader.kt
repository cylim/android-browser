package app.jrny.browser

import app.jrny.browser.download.Download
import app.jrny.browser.download.DownloadStatus
import app.jrny.browser.download.Downloader

/** A [Downloader] that keeps files in memory and records what it was asked. */
class FakeDownloader : Downloader {
    /** A file a page offered, as it arrived: its name, type, the size the site gave, and its bytes. */
    class Received(val address: String, val fileName: String, val mimeType: String, val size: Long, val bytes: ByteArray)

    val received = java.util.Collections.synchronizedList(mutableListOf<Received>())
    val cancelled = java.util.Collections.synchronizedList(mutableListOf<String>())
    val opened = java.util.Collections.synchronizedList(mutableListOf<Download>())

    /** Where a file stands, when a test wants other than [DownloadStatus.Done]. */
    val status = java.util.concurrent.ConcurrentHashMap<String, DownloadStatus>()
    var canOpen = true

    private val next = java.util.concurrent.atomic.AtomicLong(1)

    override fun receive(fileName: String, mimeType: String, size: Long, body: java.io.InputStream): String {
        val address = "content://test/received/${next.getAndIncrement()}/$fileName"
        val bytes = body.use { it.readBytes() }
        received += Received(address, fileName, mimeType, size, bytes)
        return address
    }

    /** Downloads of Android's download service: none here. */
    override fun statuses(ids: List<Long>): Map<Long, DownloadStatus> = ids.associateWith { DownloadStatus.Gone }

    override fun cancel(downloadId: Long) {}

    override fun open(download: Download): Boolean {
        opened += download
        return canOpen
    }

    /** Files saved whole ("Save image"), by their address. */
    val saved = java.util.concurrent.ConcurrentHashMap<String, Pair<String, ByteArray>>()

    override fun save(fileName: String, mimeType: String, file: java.io.File): String {
        val address = "content://test/saved/${next.getAndIncrement()}/$fileName"
        saved[address] = mimeType to file.readBytes()
        return address
    }

    override fun savedStatus(fileUri: String): DownloadStatus {
        status[fileUri]?.let { return it }
        saved[fileUri]?.let { return DownloadStatus.Done(it.second.size.toLong()) }
        return received.firstOrNull { it.address == fileUri }?.let { DownloadStatus.Done(it.bytes.size.toLong()) } ?: DownloadStatus.Gone
    }

    override fun cancelSaved(fileUri: String) {
        cancelled += fileUri
        status.remove(fileUri)
    }
}
