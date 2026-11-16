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

    override fun start(request: DownloadRequest, cookies: String?): Long {
        val id = 100L + started.size
        started += Started(id, request, cookies)
        status[id] = DownloadStatus.Running(null)
        return id
    }

    override fun statuses(ids: List<Long>): Map<Long, DownloadStatus> = ids.associateWith { status[it] ?: DownloadStatus.Gone }

    override fun open(download: Download): Boolean {
        opened += download
        return canOpen
    }

    override fun cancel(downloadId: Long) {
        cancelled += downloadId
        status.remove(downloadId)
    }
}
