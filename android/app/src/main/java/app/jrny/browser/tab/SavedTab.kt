package app.jrny.browser.tab

import app.jrny.browser.profile.ProfileId

/** Identifies a Tab. Saved Tabs have the id of their database row; Private Tabs have negative ids. */
@JvmInline
value class TabId(val value: Long)

/**
 * What is kept about a Tab while it isn't live: its address, title and scroll position, never the
 * page's content (SPEC, "Tabs"). A normal Tab's is in the database, so it comes back after
 * Android kills the app. A Private Tab's is only in memory ([PrivateTabs]).
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
    val isPrivate: Boolean = false,
    /** The bookmark this Tab is the open Pinned Tab of, or null for an ordinary Tab. */
    val bookmarkId: Long? = null,
) {
    /** True for a bookmark's open Pinned Tab, which the Sidebar lists under "Pinned". */
    val isPinned: Boolean get() = bookmarkId != null

    val pageTitle: String? get() = pageTitle(title, url)
    val displayHost: String get() = displayHost(url)
}
