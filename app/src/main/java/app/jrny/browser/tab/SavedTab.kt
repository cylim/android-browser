package app.jrny.browser.tab

import app.jrny.browser.profile.ProfileId

/** Identifies a Tab. */
@JvmInline
value class TabId(val value: Long)

/**
 * What is kept about a Tab so it can come back after Android kills the app: its address, title and
 * scroll position, never the page's content (SPEC, "Tabs").
 */
data class SavedTab(
    val id: TabId,
    val profileId: ProfileId,
    /** Null for a new Tab that hasn't loaded anything. */
    val url: String?,
    val title: String,
    val scrollY: Int,
    /** The Tab the Profile shows: its most recently selected one. */
    val selected: Boolean,
) {
    val pageTitle: String? get() = pageTitle(title, url)
    val displayHost: String get() = displayHost(url)
}
