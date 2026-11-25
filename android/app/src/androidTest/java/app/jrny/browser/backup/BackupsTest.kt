package app.jrny.browser.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.BookmarkEntity
import app.jrny.browser.data.HistoryEntity
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.data.ProfileEntity
import app.jrny.browser.data.ShortcutEntity
import app.jrny.browser.data.SiteSettingEntity
import app.jrny.browser.settings.SearchEngine
import app.jrny.browser.settings.SettingsRepository
import app.jrny.browser.settings.ThemeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Backups of what the app keeps, and how they are merged or replace what is here (SPEC, "Backup"). */
@RunWith(AndroidJUnit4::class)
class BackupsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<String>()
    private val databases = mutableListOf<JrnyDatabase>()

    private inner class Device {
        val database = JrnyDatabase.inMemory(context).also { databases += it }
        val settings = SettingsRepository(context.getSharedPreferences("test-backup-${System.nanoTime()}".also { files += it }, Context.MODE_PRIVATE))
        val backups = Backups(database.backup(), settings, now = { 42 })

        suspend fun profile(name: String, color: String = "Blue", icon: String = "Person", tint: String = "Medium"): Long =
            database.profiles().insertActive(ProfileEntity(name = name, color = color, icon = icon, tint = tint))
    }

    @After
    fun close() {
        databases.forEach { it.close() }
        files.forEach { context.deleteSharedPreferences(it) }
    }

    /** What a Profile holds, without the places of its bookmarks and Folders, which a restore renumbers. */
    private fun BackupProfile.placeless() = copy(
        folders = folders.map { it.copy(position = 0) },
        bookmarks = bookmarks.map { it.copy(position = 0) },
    )

    /** A device with two Profiles that use everything a backup holds; Work is active. */
    private suspend fun filledDevice(): Device {
        val device = Device()
        val db = device.database
        val personal = device.profile("Personal", icon = "emoji:🏠")
        db.bookmarks().add(BookmarkEntity(profileId = personal, url = "https://news.example/", title = "News", createdAt = 1))
        val docs = db.bookmarks().addFolder(personal, "Docs", createdAt = 2)!!
        val manual = db.bookmarks().add(BookmarkEntity(profileId = personal, url = "https://docs.example/manual", title = "Manual", createdAt = 3))!!
        db.bookmarks().move(manual, docs)
        db.bookmarks().setCollapsed(docs, true)
        db.tabs().open(personal, null)
        val pinned = db.tabs().openPinned(manual)!!
        db.tabs().saveScroll(pinned, 640)
        db.tabs().open(personal, "https://shop.example/cart")
        db.history().record(HistoryEntity(profileId = personal, url = "https://news.example/", title = "News", visitedAt = 10), keep = 100)
        db.history().record(HistoryEntity(profileId = personal, url = "https://docs.example/manual", title = "Manual", visitedAt = 20), keep = 100)
        db.siteSettings().set(SiteSettingEntity(personal, "https://meet.example", "camera", true))
        db.siteSettings().set(SiteSettingEntity(personal, "https://meet.example", "desktop", false))

        val work = device.profile("Work", color = "Teal", icon = "Build", tint = "Strong")
        db.tabs().open(work, "https://mail.work.example/")
        db.siteRules().save(null, "work.example", work, createdAt = 7)
        device.settings.update { it.copy(searchEngine = SearchEngine.Brave, theme = ThemeChoice.Dark, compactTabs = true, textZoom = 130) }
        return device
    }

    /** A first launch: one empty Profile named Personal. */
    private suspend fun freshDevice(): Device = Device().apply {
        database.profiles().insertActive(ProfileEntity(id = 1, name = "Personal", color = "Blue", icon = "Person"))
        database.tabs().ensureOne(1)
    }

    @Test
    fun aBackupHoldsEveryProfileWithWhatItKeeps() = runBlocking {
        val backup = filledDevice().backups.snapshot()
        assertEquals(42L, backup.createdAt)
        assertEquals(listOf("Personal", "Work"), backup.profiles.map { it.name })
        val (personal, work) = backup.profiles
        assertEquals("🏠", personal.emoji)
        assertEquals("Person", personal.icon)
        assertEquals(false, personal.active)
        assertEquals(true, work.active)
        assertEquals("Strong", work.tint)
        assertEquals(listOf(BackupFolder("Docs", position = 2, collapsed = true, createdAt = 2)), personal.folders)
        assertEquals(listOf("https://news.example/" to null, "https://docs.example/manual" to 0), personal.bookmarks.map { it.url to it.folder })
        assertEquals(listOf(null, "https://docs.example/manual", "https://shop.example/cart"), personal.tabs.map { it.url })
        assertEquals("https://docs.example/manual", personal.tabs[1].pinned)
        assertEquals(640, personal.tabs[1].scrollY)
        assertEquals(listOf(false, false, true), personal.tabs.map { it.selected })
        assertEquals(listOf(10L, 20L), personal.history.map { it.visitedAt })
        assertEquals(2, personal.siteSettings.size)
        assertEquals(listOf(BackupSiteRule("work.example", createdAt = 7)), work.siteRules)
        assertEquals("Brave", backup.settings.searchEngine)
        assertEquals(130, backup.settings.textZoom)
    }

    @Test
    fun replacingOnAFreshInstallBringsBackEverythingAsItWas() = runBlocking {
        val source = filledDevice().backups.snapshot()
        // Through the file, as a person would carry it.
        val carried = (BackupFile.read(BackupFile.write(source, password = null)) as OpenedBackup.Plain).backup
        val target = freshDevice()

        val imported = target.backups.restore(carried, ImportMode.Replace)

        assertEquals(listOf(app.jrny.browser.profile.ProfileId("1")), imported.replaced)
        assertEquals(listOf(1L), target.database.profiles().pendingIds())
        val restored = target.backups.snapshot()
        assertEquals(source.profiles.map { it.placeless() }, restored.profiles.map { it.placeless() })
        assertEquals(source.settings, restored.settings)
        assertEquals(SearchEngine.Brave, target.settings.settings.value.searchEngine)
    }

    @Test
    fun replacingDeletesTheProfilesHereAndTellsTheirShortcuts() = runBlocking {
        val source = filledDevice().backups.snapshot()
        val target = freshDevice()
        val other = target.profile("Old")
        target.database.shortcuts().add(ShortcutEntity("shortcut-1", other, "https://old.example/", "Old", 1))
        target.database.siteRules().save(null, "work.example", other, createdAt = 1)

        val imported = target.backups.restore(source, ImportMode.Replace)

        assertEquals(listOf("1", other.toString()), imported.replaced.map { it.value })
        assertEquals(listOf("shortcut-1"), imported.shortcuts)
        // The backup's rule for the same pattern takes the place of the deleted Profile's.
        val work = target.backups.snapshot().profiles.single { it.name == "Work" }
        assertEquals(listOf("work.example"), work.siteRules.map { it.pattern })
    }

    @Test
    fun mergingAddsOnlyWhatIsMissingAndChangesNothingHere() = runBlocking {
        val target = Device()
        val db = target.database
        val personal = target.profile("Personal", color = "Red")
        db.bookmarks().add(BookmarkEntity(profileId = personal, url = "https://news.example/", title = "My news", createdAt = 1))
        val docs = db.bookmarks().addFolder(personal, "docs", createdAt = 1)!!
        db.tabs().open(personal, "https://shop.example/cart")
        db.history().record(HistoryEntity(profileId = personal, url = "https://news.example/", visitedAt = 10), keep = 100)
        db.siteRules().save(null, "work.example", personal, createdAt = 1)
        target.settings.update { it.copy(searchEngine = SearchEngine.Bing) }
        val before = target.backups.snapshot()

        val source = filledDevice().backups.snapshot()
        val imported = target.backups.restore(source, ImportMode.Merge)

        assertEquals(Imported(emptyList(), emptyList()), imported)
        val after = target.backups.snapshot()
        assertEquals(listOf("Personal", "Work"), after.profiles.map { it.name })
        val merged = after.profiles[0]
        // Its look, its bookmark's title, its Folder, its selected Tab and the Active Profile stay.
        assertEquals("Red", merged.color)
        assertEquals(null, merged.emoji)
        assertEquals(true, merged.active)
        assertEquals("My news", merged.bookmarks.single { it.url == "https://news.example/" }.title)
        assertEquals(listOf("docs"), merged.folders.map { it.name })
        val manual = merged.bookmarks.single { it.url == "https://docs.example/manual" }
        assertEquals(0, manual.folder)
        assertEquals(docs, db.bookmarks().observeFolders(personal).first().single().id)
        // Bookmarks that were here come first.
        assertEquals("https://news.example/", merged.bookmarks.first().url)
        // The same Tab isn't opened twice, an empty one isn't added, and the shown one stays shown.
        assertEquals(listOf("https://shop.example/cart", "https://docs.example/manual"), merged.tabs.map { it.url })
        assertEquals("https://shop.example/cart", merged.tabs.single { it.selected }.url)
        assertEquals("https://docs.example/manual", merged.tabs[1].pinned)
        assertEquals(listOf(10L, 20L), merged.history.map { it.visitedAt })
        assertEquals(2, merged.siteSettings.size)
        // A pattern names one Profile: the rule here wins.
        assertEquals(listOf("work.example"), merged.siteRules.map { it.pattern })
        assertEquals(emptyList<BackupSiteRule>(), after.profiles[1].siteRules)
        assertEquals("Teal", after.profiles[1].color)
        // Settings are taken only when replacing.
        assertEquals(before.settings, after.settings)
    }

    @Test
    fun mergingTheSameBackupTwiceAddsNothingTheSecondTime() = runBlocking {
        val source = filledDevice().backups.snapshot()
        val target = freshDevice()
        target.backups.restore(source, ImportMode.Merge)
        val once = target.backups.snapshot()
        target.backups.restore(source, ImportMode.Merge)
        assertEquals(once, target.backups.snapshot())
    }

    @Test
    fun whatOkapiWouldNotHaveStoredIsLeftOut() = runBlocking {
        val crafted = Backup(
            createdAt = 0,
            settings = BackupSettings(searchEngine = "Altavista", textZoom = 9_000),
            profiles = listOf(
                BackupProfile(
                    name = "   ",
                    color = "Ultraviolet",
                    icon = "Skull",
                    emoji = "abc",
                    tint = "Blinding",
                    bookmarks = listOf(
                        BackupBookmark("javascript:alert(1)"),
                        BackupBookmark("https://ok.example/", folder = 7),
                        BackupBookmark("https://ok.example/"),
                    ),
                    history = listOf(BackupVisit("file:///sdcard/secret", visitedAt = 1), BackupVisit("https://ok.example/", visitedAt = 2)),
                    tabs = listOf(BackupTab("content://evil/"), BackupTab("https://ok.example/", scrollY = -5, selected = true)),
                    siteRules = listOf(BackupSiteRule("mailto:x@y.example"), BackupSiteRule("ok.example")),
                    siteSettings = listOf(
                        BackupSiteSetting("ftp://ok.example", "camera", true),
                        BackupSiteSetting("https://ok.example", "teleport", true),
                        BackupSiteSetting("https://OK.example:443/", "camera", true),
                    ),
                ),
            ),
        )
        val target = freshDevice()
        target.backups.restore(crafted, ImportMode.Replace)

        val profile = target.backups.snapshot().profiles.single()
        assertEquals("Profile", profile.name)
        assertEquals("Blue", profile.color)
        assertEquals("Person", profile.icon)
        assertEquals(null, profile.emoji)
        assertEquals("Medium", profile.tint)
        assertEquals(listOf(BackupBookmark("https://ok.example/", position = 1)), profile.bookmarks)
        assertEquals(listOf("https://ok.example/"), profile.history.map { it.url })
        assertEquals(listOf(BackupTab("https://ok.example/", selected = true)), profile.tabs)
        assertEquals(listOf("ok.example"), profile.siteRules.map { it.pattern })
        assertEquals(listOf(BackupSiteSetting("https://ok.example", "camera", true)), profile.siteSettings)
        val settings = target.settings.settings.value
        assertEquals(SearchEngine.Google, settings.searchEngine)
        assertEquals(200, settings.textZoom)
    }

    @Test
    fun aProfileWithoutTabsGetsAnEmptyOne() = runBlocking {
        val target = freshDevice()
        target.backups.restore(Backup(createdAt = 0, profiles = listOf(BackupProfile("Bare", "Blue", "Person"))), ImportMode.Replace)
        assertEquals(listOf(BackupTab(selected = true)), target.backups.snapshot().profiles.single().tabs)
        assertTrue(target.database.profiles().pendingIds().contains(1L))
    }
}
