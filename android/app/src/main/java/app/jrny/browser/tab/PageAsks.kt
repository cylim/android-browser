package app.jrny.browser.tab

import android.net.Uri
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting

/**
 * A site asks for the camera, the microphone or the device's location (SPEC, "Site permissions
 * and Site settings"). It is answered exactly once: [grant] or [deny]. Answering a request that
 * the page has already withdrawn does nothing.
 *
 * [decided] is true when the person's choice is already known (a remembered or once-only
 * grant), so only Android's own permission is left to check before [grant].
 */
class PermissionAsk(
    val origin: SiteOrigin,
    val wanted: Set<SiteSetting>,
    private val onGrant: () -> Unit,
    private val onDeny: () -> Unit,
    val decided: Boolean = false,
) {
    /** True until the ask is answered or withdrawn. */
    var open: Boolean = true
        private set

    fun grant() = answer(onGrant)

    fun deny() = answer(onDeny)

    /** The page withdrew the ask: it gets no answer. */
    fun withdraw() {
        open = false
    }

    /** The same ask, with the person's choice already known. */
    fun asDecided(): PermissionAsk = PermissionAsk(origin, wanted, onGrant, onDeny, decided = true).also { open = false }

    private fun answer(action: () -> Unit) {
        if (!open) return
        open = false
        action()
    }
}

/**
 * A page's file field wants files (SPEC, "File upload"). [answer] hands the chosen files to the
 * page, or none when the person cancelled; the page waits until it is called, and can't ask again
 * before.
 */
class FileChooserAsk(
    private val callback: ValueCallback<Array<Uri>>,
    private val params: WebChromeClient.FileChooserParams,
) {
    /** The kinds of file the page accepts: MIME types and `.ext` suffixes, as the page wrote them. */
    val acceptTypes: List<String> = params.acceptTypes.orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** True when the page takes several files. */
    val multiple: Boolean = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

    private var answered = false

    fun answer(files: List<Uri>?) {
        if (answered) return
        answered = true
        callback.onReceiveValue(files?.takeIf { it.isNotEmpty() }?.toTypedArray())
    }
}

/**
 * A site asks for a username and password before it sends the page (SPEC, "HTTP sign-in").
 * Nothing typed is kept.
 */
class HttpAuthAsk(val host: String, val realm: String, private val handler: HttpAuthHandler) {
    private var answered = false

    fun signIn(username: String, password: String) {
        if (answered) return
        answered = true
        handler.proceed(username, password)
    }

    fun cancel() {
        if (answered) return
        answered = true
        handler.cancel()
    }
}

/** A video, or the element holding it, shown fullscreen (SPEC, "Fullscreen video"). */
class FullscreenVideo(val view: View, private val callback: WebChromeClient.CustomViewCallback) {
    private var ended = false

    /** Tells the page that fullscreen ended. */
    fun end() {
        if (ended) return
        ended = true
        callback.onCustomViewHidden()
    }
}
