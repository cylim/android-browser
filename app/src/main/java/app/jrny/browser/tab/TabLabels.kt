package app.jrny.browser.tab

import app.jrny.browser.address.WebAddress

/** The page's own title, or null when it has none (or only repeats the address). */
fun pageTitle(title: String, url: String?): String? = title.takeIf { it.isNotBlank() && it != url }

/** The site's host for display, without a leading `www.`, or empty for a non-web page. */
fun displayHost(url: String?): String = WebAddress.hostOf(url).removePrefix("www.")
