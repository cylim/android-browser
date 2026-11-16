package app.jrny.browser.ui

import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.TabId

/** Profiles for UI tests that don't need a database: two Profiles, recording what was asked. */
object TestProfiles {
    val personal = Profile(ProfileId("test-1"), "Personal", ProfileColor.Blue, ProfileIcon.Person)
    val work = Profile(ProfileId("test-2"), "Work", ProfileColor.Teal, ProfileIcon.Build)

    fun ui(
        active: Profile = personal,
        all: List<Profile> = listOf(personal, work),
        switched: MutableList<Profile> = mutableListOf(),
        edited: MutableList<Profile?> = mutableListOf(),
    ) = ProfilesUi(all, active, pendingDeletions = 0, switchTo = { switched += it }, edit = { edited += it })
}

/** Tabs for UI tests that don't need a database, recording what was asked. */
object TestTabs {
    fun tab(id: Long, title: String = "", url: String? = null, selected: Boolean = false) =
        SavedTab(TabId(id), TestProfiles.personal.id, url, title, scrollY = 0, selected = selected)

    val one = tab(1, "one navigate", "http://example.com/one", selected = true)

    fun ui(
        all: List<SavedTab> = listOf(one),
        selected: SavedTab = all.first { it.selected },
        selectedIds: MutableList<TabId> = mutableListOf(),
        closed: MutableList<TabId> = mutableListOf(),
        opened: MutableList<Unit> = mutableListOf(),
        moved: MutableList<Profile> = mutableListOf(),
        openedIn: MutableList<Profile> = mutableListOf(),
        incomingLink: Boolean = false,
        dismissIncomingLink: () -> Unit = {},
    ) = TabsUi(
        all,
        selected,
        select = { selectedIds += it },
        close = { closed += it },
        open = { opened += Unit },
        move = { moved += it },
        openIn = { openedIn += it },
        incomingLink = incomingLink,
        dismissIncomingLink = dismissIncomingLink,
    )
}
