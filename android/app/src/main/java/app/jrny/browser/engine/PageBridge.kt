package app.jrny.browser.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.coroutines.resume

/** A file fetched from a page: its bytes and its MIME type, which may be empty. */
class PageFile(val bytes: ByteArray, val mimeType: String)

/**
 * Okapi's line to the page shown in one GeckoSession, through the built-in extension's content
 * script (`assets/okapi/page.js`). It runs in the top frame of every page, apart from the page's
 * own scripts, so the page can't send anything over it. Okapi uses it to:
 * - hear the page's site icon ([onIcon]);
 * - darken a page without a dark theme ([dark]);
 * - fetch a file as the page would, with its Profile's cookies ([fetch]);
 * - measure the video the page shows fullscreen ([measureFullscreenVideo]);
 * - in debug builds only, run a test's script in the content script's world ([evaluate]).
 *
 * Use it on the main thread, where GeckoView calls it too.
 */
class PageBridge(private val onIcon: (page: String, icon: Bitmap) -> Unit = { _, _ -> }) {
    private var port: WebExtension.Port? = null
    private var nextId = 1
    /** What waits for the page's replies to each request, by id: each reply, or null when none will come. */
    private val pending = mutableMapOf<Int, (reply: JSONObject?) -> Unit>()
    private val waitingForPort = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Whether pages are darkened when they have no dark theme of their own. */
    var dark: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            port?.postMessage(JSONObject().put("type", "dark").put("on", value))
        }

    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            if (port == this@PageBridge.port) (message as? JSONObject)?.let(::receive)
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (port != this@PageBridge.port) return
            this@PageBridge.port = null
            endAll()
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            // Only the page itself: the content script runs in no frame.
            if (!port.sender.isTopLevel) return port.disconnect()
            this@PageBridge.port?.let { old ->
                this@PageBridge.port = null
                endAll()
                old.disconnect()
            }
            this@PageBridge.port = port
            port.setDelegate(portDelegate)
            port.postMessage(JSONObject().put("type", "dark").put("on", dark))
            val waiting = waitingForPort.toList()
            waitingForPort.clear()
            waiting.forEach { it() }
        }
    }

    /** Listens to the pages [session] shows, from now on. */
    fun attach(session: GeckoSession) {
        Engine.withBridge { extension -> session.webExtensionController.setMessageDelegate(extension, messageDelegate, NATIVE_APP) }
    }

    /** Forgets the page: what it was asked for gets no answer. */
    fun detach() {
        port?.disconnect()
        port = null
        endAll()
        waitingForPort.clear()
    }

    /**
     * Fetches [url] as the page would, with its Profile's cookies, or null when it can't be had:
     * the page is gone, the site refused, or the file is larger than [maxBytes].
     */
    suspend fun fetch(url: String, maxBytes: Int = MAX_FETCH_BYTES): PageFile? {
        val bytes = ByteArrayOutputStream()
        return request(JSONObject().put("type", "fetch").put("url", url).put("max", maxBytes)) { reply, finish ->
            if (reply.optString("type") != "fetched" || !reply.optBoolean("ok")) return@request finish(null)
            val piece = runCatching { Base64.getDecoder().decode(reply.optString("data")) }.getOrNull()
            if (piece == null || bytes.size() + piece.size > maxBytes) return@request finish(null)
            bytes.write(piece)
            if (reply.optBoolean("last")) finish(PageFile(bytes.toByteArray(), reply.optString("mime").substringBefore(';').trim().lowercase()))
        }
    }

    /**
     * The width and height of the video the page shows fullscreen, or of the first video inside
     * the fullscreen element, or null when there is none it can see, as in another site's frame.
     */
    suspend fun measureFullscreenVideo(): Pair<Int, Int>? {
        val value = ask(JSONObject().put("type", "measureVideo")) ?: return null
        // [width, height], or null when there is no video.
        val size = runCatching { JSONArray(value) }.getOrNull() ?: return null
        val width = size.optInt(0)
        val height = size.optInt(1)
        return if (size.length() == 2 && width > 0 && height > 0) width to height else null
    }

    /**
     * Runs [script] in the content script's world, which sees the page's DOM but not its own
     * scripts, and gives its result as JSON, or null when there is no page to run it in. Only
     * debug builds can: the release extension has no way to run code it is sent (`src/debug/assets`).
     */
    suspend fun evaluate(script: String): String? = ask(JSONObject().put("type", "eval").put("code", script))

    /** Sends [question] and waits for the page's answer, as JSON, or null. */
    private suspend fun ask(question: JSONObject): String? = request(question) { reply, finish ->
        finish(if (reply.optString("type") == "answer" && reply.optBoolean("ok")) reply.optString("value", "null") else null)
    }

    /**
     * Sends [message] with a new id and waits for its result, which [onReply] makes from the
     * page's replies and hands to `finish`. Null when the page goes, or never listens.
     */
    private suspend fun <T> request(message: JSONObject, onReply: (reply: JSONObject, finish: (T?) -> Unit) -> Unit): T? =
        suspendCancellableCoroutine { done ->
            val id = nextId++
            fun finish(result: T?) {
                pending.remove(id)
                if (done.isActive) done.resume(result)
            }
            pending[id] = { reply -> if (reply == null) finish(null) else onReply(reply, ::finish) }
            done.invokeOnCancellation { main.post { pending.remove(id) } }
            send(message.put("id", id)) { finish(null) }
        }

    /** Sends [message] once the page is listening, or runs [failed] when no page comes in time. */
    private fun send(message: JSONObject, failed: () -> Unit) {
        port?.let { return it.postMessage(message) }
        var sent = false
        val attempt = {
            if (!sent) {
                sent = true
                port?.postMessage(message) ?: failed()
            }
        }
        waitingForPort += attempt
        main.postDelayed({
            if (!sent) {
                waitingForPort.remove(attempt)
                attempt()
            }
        }, PORT_WAIT_MS)
    }

    private fun receive(message: JSONObject) {
        when (message.optString("type")) {
            "icon" -> {
                val page = message.optString("page")
                val data = message.optString("data")
                val icon = decodeDataImage(data) ?: return
                onIcon(page, icon)
            }
            "fetched", "answer" -> pending[message.optInt("id")]?.invoke(message)
        }
    }

    private fun endAll() {
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it(null) }
    }

    companion object {
        /** Where the built-in extension lives in the app's assets. */
        const val LOCATION = "resource://android/assets/okapi/"
        const val ID = "bridge@okapi.jrny.app"

        /** The name the content script connects to (`runtime.connectNative`). */
        private const val NATIVE_APP = "okapi"

        /** How long a request waits for the page's content script to start. */
        private const val PORT_WAIT_MS = 5_000L

        const val MAX_FETCH_BYTES = 25 * 1024 * 1024

        /** The image in a `data:image/...;base64,...` address, or null. */
        fun decodeDataImage(data: String): Bitmap? {
            if (!data.startsWith("data:image/", ignoreCase = true)) return null
            val comma = data.indexOf(',')
            if (comma < 0 || !data.substring(0, comma).endsWith(";base64", ignoreCase = true)) return null
            val bytes = runCatching { Base64.getDecoder().decode(data.substring(comma + 1)) }.getOrNull() ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}
