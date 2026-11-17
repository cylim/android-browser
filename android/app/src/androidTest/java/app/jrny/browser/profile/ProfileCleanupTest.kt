package app.jrny.browser.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The cold-start wipe of deleted Profiles, with a fake for WebView's ProfileStore. */
@RunWith(AndroidJUnit4::class)
class ProfileCleanupTest {

    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val repository = ProfileRepository(database.profiles())

    @After
    fun close() = database.close()

    @Test
    fun deletedProfilesAreWipedAndForgotten() = runBlocking {
        repository.ensureFirstProfile("Personal")
        val work = repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        repository.delete(work.id)
        val wiped = mutableListOf<ProfileId>()

        assertEquals(1, ProfileCleanup(repository) { wiped += it }.run())
        assertEquals(listOf(work.id), wiped)
        assertEquals(emptyList<ProfileId>(), repository.pendingDeletion())
    }

    @Test
    fun aProfileWhoseDataCantBeWipedStaysPendingForTheNextStart() = runBlocking {
        repository.ensureFirstProfile("Personal")
        val work = repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        repository.delete(work.id)

        assertEquals(0, ProfileCleanup(repository) { error("Cannot delete in-use profile") }.run())
        assertEquals(listOf(work.id), repository.pendingDeletion())
    }

    @Test
    fun liveProfilesAreNeverWiped() = runBlocking {
        repository.ensureFirstProfile("Personal")
        repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        val wiped = mutableListOf<ProfileId>()
        ProfileCleanup(repository) { wiped += it }.run()
        assertEquals(emptyList<ProfileId>(), wiped)
    }
}
