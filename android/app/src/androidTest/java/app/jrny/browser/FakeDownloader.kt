package app.jrny.browser

import app.jrny.browser.download.Download
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadStatus
import app.jrny.browser.download.Downloader

/** A [Downloader] that fetches nothing and records what it was asked. */
class FakeDownloader : Downloader {
    class Started(val id: Long, val request: DownloadRequest, val cookies: String?)

    val started = java.util.Collections.synchronizedList(mutableListOf<Started>())
    val cancelled = java.util.Collections.synchronizedList(mutableListOf<Long>())
    val opened = java.util.Collections.synchronizedList(mutableListOf<Download>())
    val status = java.util.concurrent.ConcurrentHashMap<Long, DownloadStatus>()
    var canOpen = true

    /** Where an address "redirects" to. Addresses not listed here don't redirect. */
    val redirects = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun resolve(url: String, userAgent: String, cookiesFor: (String) -> String?): String = redirects[url] ?: url

    // Counted apart from the list: two downloads started at the same moment must not share an id.
    private val nextId = java.util.concurrent.atomic.AtomicLong(100)

    override fun start(request: DownloadRequest, cookies: String?): Long {
        val id = nextId.getAndIncrement()
        started += Started(id, request, cookies)
        status[id] = DownloadStatus.Running(null)
        return id
    }

    override fun statuses(ids: List<Long>): Map<Long, DownloadStatus> = ids.associateWith { status[it] ?: DownloadStatus.Gone }

    override fun open(download: Download): Boolean {
        opened += download
        return canOpen
    }

    /** Files saved as a page's own, by their address. */
    val saved = java.util.concurrent.ConcurrentHashMap<String, Pair<String, ByteArray>>()

    override fun save(fileName: String, mimeType: String, bytes: ByteArray): String {
        val address = "content://test/${saved.size + 1}/$fileName"
        saved[address] = mimeType to bytes
        return address
    }

    override fun savedStatus(fileUri: String): DownloadStatus =
        saved[fileUri]?.let { DownloadStatus.Done(it.second.size.toLong()) } ?: DownloadStatus.Gone

    override fun cancel(downloadId: Long) {
        cancelled += downloadId
        status.remove(downloadId)
    }
}
