package app.jrny.browser.site

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Site settings against a throwaway database: each Profile keeps its own, and they go with it. */
@RunWith(AndroidJUnit4::class)
class SiteSettingsRepositoryTest {

    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val profiles = ProfileRepository(database.profiles())
    private val sites = SiteSettingsRepository(database.siteSettings())
    private val personal = ProfileId("1")
    private var work = ProfileId("0")
    private val meet = SiteOrigin.of("https://meet.example.com/abc")!!
    private val maps = SiteOrigin.of("https://maps.example.com")!!

    @Before
    fun twoProfiles() = runBlocking {
        profiles.ensureFirstProfile("Personal")
        work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
    }

    @After
    fun close() = database.close()

    @Test
    fun aGrantInOneProfileIsntAGrantInAnother() = runBlocking {
        sites.set(work, meet, SiteSetting.Camera, true)
        assertEquals(true, sites.get(work, meet, SiteSetting.Camera))
        assertNull(sites.get(personal, meet, SiteSetting.Camera))
        assertNull(sites.get(work, meet, SiteSetting.Microphone))
    }

    @Test
    fun sitesAreListedWithTheirSettings() = runBlocking {
        sites.set(work, meet, SiteSetting.Camera, true)
        sites.set(work, meet, SiteSetting.Microphone, false)
        sites.set(work, maps, SiteSetting.Location, true)
        val listed = sites.sites(work).first()
        assertEquals(listOf(maps, meet), listed.map { it.origin })
        assertEquals(mapOf(SiteSetting.Camera to true, SiteSetting.Microphone to false), listed.single { it.origin == meet }.settings)
    }

    @Test
    fun aChangeReplacesTheOldAnswerAndAResetMakesTheSiteAskAgain() = runBlocking {
        sites.set(work, meet, SiteSetting.Camera, true)
        sites.set(work, meet, SiteSetting.Camera, false)
        assertEquals(false, sites.get(work, meet, SiteSetting.Camera))
        sites.reset(work, meet, SiteSetting.Camera)
        assertNull(sites.get(work, meet, SiteSetting.Camera))
        assertEquals(emptyList<SiteSettingsEntry>(), sites.sites(work).first())
    }

    @Test
    fun resetAllClearsOnlyThatProfile() = runBlocking {
        sites.set(work, meet, SiteSetting.Camera, true)
        sites.set(personal, meet, SiteSetting.Camera, false)
        sites.resetAll(work)
        assertNull(sites.get(work, meet, SiteSetting.Camera))
        assertEquals(false, sites.get(personal, meet, SiteSetting.Camera))
    }

    @Test
    fun deletingAProfileDeletesItsSiteSettingsAtOnce() = runBlocking {
        sites.set(work, meet, SiteSetting.Camera, true)
        profiles.delete(work)
        assertNull(sites.get(work, meet, SiteSetting.Camera))
        // A deleted Profile keeps nothing new either.
        sites.set(work, maps, SiteSetting.Location, true)
        assertNull(sites.get(work, maps, SiteSetting.Location))
    }
}
