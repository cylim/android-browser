package app.jrny.browser.ui

import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId

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
