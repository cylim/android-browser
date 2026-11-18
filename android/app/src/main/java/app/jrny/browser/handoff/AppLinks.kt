package app.jrny.browser.handoff

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.jrny.browser.address.WebAddress
import java.net.URISyntaxException

/**
 * A link a page opened that belongs to another app, ready to hand over (SPEC, "Links to other
 * apps"). Only the link goes: the intent is rebuilt so that a page can't name a component, add a
 * selector, or pass on access to the app's files.
 */
class AppLink private constructor(
    /** The address the page opened, as it was. */
    val url: String,
    val scheme: String,
    private val intent: Intent?,
    /** The web address an `intent:` link names for when no app can open it, or null. */
    val fallback: String?,
    /** The web address an `intent:` link carries itself, which stays in the Tab, or null. */
    val webAddress: String?,
) {
    /** The name of the app that would open it, when Android tells the app which one. */
    fun appName(context: Context): String? {
        val target = intent ?: return null
        val info = context.packageManager.resolveActivity(target, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        // Android's own chooser stands in when several apps could open it: no one app to name.
        if (info.activityInfo.packageName == "android") return null
        return info.loadLabel(context.packageManager).toString().takeIf { it.isNotBlank() }
    }

    /** False for a link that can't be handed to any app: a broken `intent:` link, or one that failed to open. */
    val openable: Boolean get() = intent != null

    /** The same link, known now to open in no app: the bar says so. */
    fun unopenable(): AppLink = AppLink(url, scheme, null, fallback = null, webAddress = null)

    /** Hands the link to its app. False when no app can open it. */
    fun open(context: Context): Boolean {
        val target = intent ?: return false
        return try {
            context.startActivity(target)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        /** The link at [url] as a handoff, or null when it is no link to another app. */
        fun of(url: String): AppLink? {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (!HandoffRules.isForAnotherApp(scheme)) return null
            if (scheme != "intent") {
                // As other browsers send them: apps that may be opened from a browser take phone,
                // mail, message and map links as a view of the link.
                return AppLink(url, scheme, browsable(Intent(Intent.ACTION_VIEW, uri)), fallback = null, webAddress = null)
            }
            val parsed = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (_: URISyntaxException) {
                return AppLink(url, scheme, null, fallback = null, webAddress = null)
            }
            val fallback = parsed.getStringExtra("browser_fallback_url")?.takeIf(WebAddress::isWebAddress)
            // An intent for a web page that names no app is just a web link.
            val web = parsed.dataString?.takeIf { WebAddress.isWebAddress(it) && parsed.`package` == null }
            val safe = Intent(parsed.action ?: Intent.ACTION_VIEW).apply {
                parsed.data?.let { data -> if (parsed.type != null) setDataAndType(data, parsed.type) else setData(data) }
                if (parsed.data == null && parsed.type != null) type = parsed.type
                parsed.`package`?.let { setPackage(it) }
                parsed.categories?.forEach(::addCategory)
            }
            return AppLink(url, scheme, browsable(safe), fallback, web)
        }

        /**
         * Only an activity that says it may be opened from a browser, and no flag that would pass
         * on access to the app's files.
         */
        private fun browsable(intent: Intent): Intent = intent.apply {
            component = null
            selector = null
            clipData = null
            flags = 0
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }
}
