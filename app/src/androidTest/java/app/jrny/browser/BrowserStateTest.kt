package app.jrny.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What BrowserState saves to restore after the activity is recreated. Creates no Tabs. */
@RunWith(AndroidJUnit4::class)
class BrowserStateTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val database = JrnyDatabase.inMemory(instrumentation.targetContext)
    private val repository = ProfileRepository(database.profiles())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun close() {
        // Wait for the state flows' queries to finish: closing the database under a running one
        // crashes the test process.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        database.close()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    @Test
    fun aProfileNotShownSinceTheLastRecreationKeepsItsAddressUntilItIsDeleted() {
        val work = runBlocking {
            repository.ensureFirstProfile("Personal")
            repository.create("Work", ProfileColor.Teal, ProfileIcon.Build)
        }
        val restored = mapOf(ProfileId("1") to "https://example.com/", work.id to "https://example.org/")
        val browser = onMain { BrowserState(instrumentation.targetContext, repository, scope, restored) }

        // Neither Profile has been shown, so a second recreation must still restore both.
        assertEquals(restored, onMain { browser.addresses() })

        onMain { browser.delete(work) }
        val deadline = System.currentTimeMillis() + 10_000
        while (onMain { browser.addresses() }.containsKey(work.id) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(mapOf(ProfileId("1") to "https://example.com/"), onMain { browser.addresses() })
    }
}
