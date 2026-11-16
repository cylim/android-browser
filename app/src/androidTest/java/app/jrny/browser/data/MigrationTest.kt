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

    private companion object {
        const val DB = "migration-test.db"
    }
}
