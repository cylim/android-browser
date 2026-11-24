package app.jrny.browser.tab

import android.content.Context
import android.util.Base64
import androidx.core.net.toUri
import app.jrny.browser.R
import org.mozilla.geckoview.WebRequestError

/**
 * The page Okapi shows in a Tab when a page can't be loaded (SPEC, "Error pages"): what went
 * wrong in plain words, and a way to try again or, for a dangerous site, to go back. It is a
 * `data:` page made here, so it needs nothing from the network and can't be mistaken for the site.
 */
object ErrorPages {
    /** Why a page didn't load, as the error page says it. */
    enum class Kind { Offline, NotFound, Insecure, Dangerous, Refused, Other }

    fun kindOf(error: WebRequestError): Kind = when {
        error.category == WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> Kind.Dangerous
        error.category == WebRequestError.ERROR_CATEGORY_SECURITY -> Kind.Insecure
        error.code == WebRequestError.ERROR_HTTPS_ONLY || error.code == WebRequestError.ERROR_BAD_HSTS_CERT -> Kind.Insecure
        error.code == WebRequestError.ERROR_UNKNOWN_HOST -> Kind.NotFound
        error.code == WebRequestError.ERROR_OFFLINE || error.code == WebRequestError.ERROR_NET_TIMEOUT ||
            error.code == WebRequestError.ERROR_NET_INTERRUPT || error.code == WebRequestError.ERROR_NET_RESET -> Kind.Offline
        error.code == WebRequestError.ERROR_CONNECTION_REFUSED || error.code == WebRequestError.ERROR_PORT_BLOCKED -> Kind.Refused
        else -> Kind.Other
    }

    /** The error page for [failed], an address that couldn't be loaded, as a `data:` address. */
    fun page(context: Context, failed: String, kind: Kind, dark: Boolean): String {
        val (title, body) = when (kind) {
            Kind.Offline -> R.string.error_offline_title to R.string.error_offline_body
            Kind.NotFound -> R.string.error_not_found_title to R.string.error_not_found_body
            Kind.Insecure -> R.string.error_insecure_title to R.string.error_insecure_body
            Kind.Dangerous -> R.string.error_dangerous_title to R.string.error_dangerous_body
            Kind.Refused -> R.string.error_refused_title to R.string.error_refused_body
            Kind.Other -> R.string.error_other_title to R.string.error_other_body
        }
        val host = failed.toUri().host.orEmpty()
        val action = if (kind == Kind.Dangerous) {
            """<a class="button" href="#" onclick="history.back();return false">${escape(context.getString(R.string.error_go_back))}</a>"""
        } else {
            """<a class="button" href="${escape(failed)}">${escape(context.getString(R.string.error_try_again))}</a>"""
        }
        val (background, text, muted, accent) = if (dark) {
            listOf("#141218", "#e6e0e9", "#cac4d0", "#d0bcff")
        } else {
            listOf("#fef7ff", "#1d1b20", "#49454f", "#6750a4")
        }
        val html = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(host.ifEmpty { context.getString(title) })}</title>
<style>
html,body{margin:0;background:$background;color:$text;font-family:sans-serif}
main{max-width:520px;margin:0 auto;padding:64px 24px}
h1{font-size:22px;font-weight:500;margin:0 0 12px}
p{font-size:16px;line-height:1.5;color:$muted;margin:0 0 8px;word-wrap:break-word}
.button{display:inline-block;margin-top:24px;padding:10px 24px;border-radius:20px;background:$accent;color:$background;text-decoration:none;font-weight:500}
</style></head><body><main>
<h1>${escape(context.getString(title))}</h1>
<p>${escape(context.getString(body, host.ifEmpty { failed }))}</p>
$action
</main></body></html>"""
        return PREFIX + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
    }

    /** The error page for [failed], which didn't load because of [error]. */
    fun page(context: Context, failed: String, error: WebRequestError, dark: Boolean): String = page(context, failed, kindOf(error), dark)

    /**
     * True for an address [page] made. Its media type carries a token made at each start of the
     * app, so a `data:` page that a site or the person opens can't pass for one.
     */
    fun isErrorPage(address: String?): Boolean = address != null && address.startsWith(PREFIX)

    private val PREFIX = "data:text/html;charset=utf-8;okapi-error=${java.util.UUID.randomUUID().toString().replace("-", "")};base64,"

    private fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
