package app.jrny.browser.download

import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.OutputStream
import java.util.Base64
import java.util.UUID

/**
 * Gets a file a page made itself (a `blob:` address) out of the page, for the person to keep
 * (SPEC, "Browsing features"). There is no bridge the page could call: Okapi runs a script in
 * the page at two moments only.
 *
 * 1. When the page offers the file, the page keeps its own copy of it under a name only Okapi
 *    knows ([keep]), because pages often drop a `blob:` address right after offering it.
 * 2. Once the person agrees, Okapi reads that one copy out, piece by piece ([read]), and the copy
 *    goes. Declining drops it ([drop]).
 *
 * A script runs in the page's own world, so the page could tamper with its copy; but the copy is
 * the page's own file anyway, which the person asked to save.
 */
object PageFiles {
    /** Base64 characters per piece read out of the page: about 1.5 MB of file. */
    private const val PIECE = 2_000_000
    private const val POLL_MS = 50L
    private const val MAX_WAIT_MS = 60_000L

    /** A new, unguessable name for a page's copy of one file. */
    fun newCopyName(): String = "__okapi_" + UUID.randomUUID().toString().replace("-", "")

    /** The script that makes the page keep its own copy of the file at [blobUrl] under [name]. */
    fun keep(blobUrl: String, name: String): String =
        "(function(u,k){window[k]=null;fetch(u).then(function(r){return r.blob()})" +
            ".then(function(b){window[k]=b},function(){window[k]=false})})(${blobUrl.quoted()},'$name')"

    /** The script that finds the name a link on the page gives the file at [url] (`download`), or an empty one. */
    fun nameOf(url: String): String =
        "(function(u){var l=document.querySelectorAll('a[download]');for(var i=0;i<l.length;i++){if(l[i].href===u)return l[i].download}return ''})(${url.quoted()})"

    /** The script that drops the page's copy under [name]. */
    fun drop(name: String): String = "delete window['$name'];delete window['${name}_b'];void 0"

    /**
     * Reads the page's copy under [name] out of the page with [evaluate], which runs a script in
     * the page and gives its result as JSON, and writes it to [into] piece by piece, so a large
     * file never sits whole in the app's memory. [write] runs each write, off the main thread.
     * Returns false when the copy is gone (the page left, or never could read its file) or took
     * too long.
     */
    suspend fun read(
        name: String,
        evaluate: suspend (String) -> String?,
        into: OutputStream,
        write: suspend (() -> Unit) -> Unit = { it() },
    ): Boolean {
        try {
            var waited = 0L
            var length: Int
            while (true) {
                // Once the page has its copy, it turns it into base64, and Okapi waits for that.
                val state = evaluate(
                    "(function(k){var b=window[k],s=window[k+'_b'];if(b===undefined||b===false||s===false)return -1;" +
                        "if(typeof s==='string')return s.length;" +
                        "if(b&&s===undefined){window[k+'_b']=null;var r=new FileReader();" +
                        "r.onload=function(){var t=r.result;window[k+'_b']=t.substring(t.indexOf(',')+1)};" +
                        "r.onerror=function(){window[k+'_b']=false};r.readAsDataURL(b)}return -2})('$name')",
                )?.trim()?.toIntOrNull() ?: return false
                if (state == -1) return false
                if (state >= 0) {
                    length = state
                    break
                }
                if (waited >= MAX_WAIT_MS) return false
                delay(POLL_MS)
                waited += POLL_MS
            }
            var at = 0
            while (at < length) {
                // A piece is a whole number of base64 groups, so it decodes on its own.
                val piece = evaluate("window['${name}_b'].substr($at,$PIECE)") ?: return false
                val text = JSONArray("[$piece]").getString(0)
                if (text.isEmpty()) return false
                val bytes = Base64.getDecoder().decode(text)
                write { into.write(bytes) }
                at += text.length
            }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            evaluate(drop(name))
        }
    }

    private fun String.quoted(): String = JSONArray(listOf(this)).toString().removePrefix("[").removeSuffix("]")
}
