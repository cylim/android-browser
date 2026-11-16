package app.jrny.browser.ui

import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Swiping between Profiles: which Profile is next to the Active Profile. */
class ProfileNeighbourTest {

    private val personal = Profile(ProfileId("1"), "Personal", ProfileColor.Blue, ProfileIcon.Person)
    private val work = Profile(ProfileId("2"), "Work", ProfileColor.Teal, ProfileIcon.Build)
    private val school = Profile(ProfileId("3"), "School", ProfileColor.Amber, ProfileIcon.Star)

    private fun ui(active: Profile, all: List<Profile>) =
        ProfilesUi(all, active, pendingDeletions = 0, switchTo = {}, edit = {})

    @Test
    fun neighboursAreTheProfilesEitherSideOfTheActiveOne() {
        val profiles = ui(work, listOf(personal, work, school))
        assertEquals(school, profiles.neighbour(1))
        assertEquals(personal, profiles.neighbour(-1))
    }

    @Test
    fun thereIsNoNeighbourPastEitherEnd() {
        assertNull(ui(personal, listOf(personal, work)).neighbour(-1))
        assertNull(ui(work, listOf(personal, work)).neighbour(1))
    }

    @Test
    fun aRenameThatReachedOnlyTheListStillFindsTheActiveProfile() {
        val renamed = work.copy(name = "Office")
        val profiles = ui(active = work, all = listOf(personal, renamed, school))
        assertEquals(school, profiles.neighbour(1))
        assertEquals(personal, profiles.neighbour(-1))
    }

    @Test
    fun anActiveProfileMissingFromTheListHasNoNeighbours() {
        val profiles = ui(active = school, all = listOf(personal, work))
        assertNull(profiles.neighbour(1))
        assertNull(profiles.neighbour(-1))
    }
}
