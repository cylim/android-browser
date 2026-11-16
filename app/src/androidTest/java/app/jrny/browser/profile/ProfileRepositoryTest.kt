package app.jrny.browser.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Profile rules (SPEC, "Profiles") against a throwaway database. */
@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {

    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val repository = ProfileRepository(database.profiles())

    @After
    fun close() = database.close()

    private suspend fun names() = repository.profiles.first().map { it.name }
    private suspend fun active() = repository.activeProfile.first()!!

    @Test
    fun firstLaunchCreatesOneActiveProfileWithTheIdOne() = runBlocking {
        repository.ensureFirstProfile("Personal")
        repository.ensureFirstProfile("Personal again")
        assertEquals(listOf("Personal"), names())
        assertEquals(ProfileId("1"), active().id)
    }

    @Test
    fun aNewProfileBecomesTheActiveProfile() = runBlocking {
        repository.ensureFirstProfile("Personal")
        val work = repository.create("  Work ", ProfileColor.Teal, ProfileIcon.Build)
        assertEquals("Work", work.name)
        assertEquals(work, active())
        assertEquals(listOf("Personal", "Work"), names())
    }

    @Test
    fun switchingMakesAProfileActive() = runBlocking {
        repository.ensureFirstProfile("Personal")
        repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        repository.activate(ProfileId("1"))
        assertEquals("Personal", active().name)
    }

    @Test
    fun aProfileCanBeRenamedRecoloredAndGivenAnotherIcon() = runBlocking {
        repository.ensureFirstProfile("Personal")
        repository.update(active().copy(name = "Home", color = ProfileColor.Purple, icon = ProfileIcon.Home))
        val profile = active()
        assertEquals("Home", profile.name)
        assertEquals(ProfileColor.Purple, profile.color)
        assertEquals(ProfileIcon.Home, profile.icon)
    }

    @Test
    fun deletingTheActiveProfileHandsOverToTheOneActiveBeforeIt() = runBlocking {
        repository.ensureFirstProfile("Personal")
        val work = repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        val school = repository.create("School", ProfileColor.Amber, ProfileIcon.Star)
        repository.activate(work.id)
        repository.activate(school.id)

        assertTrue(repository.delete(school.id))
        assertEquals("Work", active().name)
        assertEquals(listOf("Personal", "Work"), names())
        assertEquals(listOf(school.id), repository.pendingDeletion())
        assertEquals(1, repository.pendingDeletionCount.first())
    }

    @Test
    fun theLastProfileCantBeDeleted() = runBlocking {
        repository.ensureFirstProfile("Personal")
        assertFalse(repository.delete(ProfileId("1")))
        assertEquals(listOf("Personal"), names())
    }

    @Test
    fun idsAreNeverReusedEvenAfterAPurge() = runBlocking {
        repository.ensureFirstProfile("Personal")
        val work = repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        repository.delete(work.id)
        repository.purge(work.id)
        val next = repository.create("Next", ProfileColor.Red, ProfileIcon.Face)
        assertTrue("id ${next.id.value} reuses ${work.id.value}", next.id.value.toLong() > work.id.value.toLong())
    }
}
