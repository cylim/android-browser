package app.jrny.browser.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrades keep every Profile, so nobody loses their sign-ins (ADR 0004). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), JrnyDatabase::class.java)

    @Test
    fun version2AddsTabsAndKeepsProfiles() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO profiles (id, name, color, icon, activation, pendingDeletion) VALUES (1, 'Personal', 'Blue', 'Person', 1, 0)")
            db.execSQL("INSERT INTO profiles (id, name, color, icon, activation, pendingDeletion) VALUES (2, 'Work', 'Teal', 'Build', 2, 0)")
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT id, name FROM profiles ORDER BY id").use { rows ->
                val names = buildList { while (rows.moveToNext()) add(rows.getLong(0) to rows.getString(1)) }
                assertEquals(listOf(1L to "Personal", 2L to "Work"), names)
            }
            db.query("SELECT COUNT(*) FROM tabs").use { rows ->
                rows.moveToFirst()
                assertEquals(0, rows.getInt(0))
            }
        }
    }

    @Test
    fun version3AddsHistoryAndBookmarksAndKeepsProfilesAndTabs() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO profiles (id, name, color, icon, activation, pendingDeletion) VALUES (1, 'Personal', 'Blue', 'Person', 1, 0)")
            db.execSQL("INSERT INTO tabs (id, profileId, url, title, scrollY, lastUsed) VALUES (7, 1, 'https://example.com/', 'Example', 120, 1)")
        }
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT url, title, scrollY FROM tabs WHERE id = 7").use { rows ->
                rows.moveToFirst()
                assertEquals(Triple("https://example.com/", "Example", 120), Triple(rows.getString(0), rows.getString(1), rows.getInt(2)))
            }
            for (table in listOf("history", "bookmarks")) {
                db.query("SELECT COUNT(*) FROM $table").use { rows ->
                    rows.moveToFirst()
                    assertEquals(0, rows.getInt(0))
                }
            }
        }
    }

    @Test
    fun version4AddsDownloadsAndKeepsHistoryAndBookmarks() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO profiles (id, name, color, icon, activation, pendingDeletion) VALUES (1, 'Personal', 'Blue', 'Person', 1, 0)")
            db.execSQL("INSERT INTO history (id, profileId, url, title, visitedAt) VALUES (1, 1, 'https://example.com/', 'Example', 5)")
            db.execSQL("INSERT INTO bookmarks (id, profileId, url, title, createdAt) VALUES (1, 1, 'https://example.org/', 'Org', 6)")
        }
        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            for ((table, expected) in listOf("history" to 1, "bookmarks" to 1, "downloads" to 0)) {
                db.query("SELECT COUNT(*) FROM $table").use { rows ->
                    rows.moveToFirst()
                    assertEquals(table, expected, rows.getInt(0))
                }
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
