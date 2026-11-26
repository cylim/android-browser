package app.jrny.browser.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Writing and reading backup files, plain and encrypted (SPEC, "Backup"). */
class BackupFileTest {

    /** Far below the real work factor, so the tests are quick. */
    private val iterations = 1_000

    private val backup = Backup(
        createdAt = 1_700_000_000_000,
        settings = BackupSettings(searchEngine = "DuckDuckGo", textZoom = 120),
        profiles = listOf(
            BackupProfile(
                name = "Work",
                color = "Teal",
                icon = "Build",
                active = true,
                folders = listOf(BackupFolder("Docs", position = 1)),
                bookmarks = listOf(BackupBookmark("https://example.com/secret-page", "Secret", folder = 0, position = 2)),
                history = listOf(BackupVisit("https://example.com/visited", "Visited", visitedAt = 5)),
                tabs = listOf(BackupTab("https://example.com/open", "Open", selected = true)),
                siteRules = listOf(BackupSiteRule("example.com")),
                siteSettings = listOf(BackupSiteSetting("https://example.com", "camera", true)),
            ),
        ),
    )

    private fun problemOf(block: () -> Unit): BackupProblem? = try {
        block()
        null
    } catch (e: BackupException) {
        e.problem
    }

    @Test
    fun aPlainBackupIsReadableJsonAndReadsBackTheSame() {
        val bytes = BackupFile.write(backup, password = null)
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("{"))
        assertTrue(text.contains("\"format\":\"okapi-backup\""))
        assertTrue(text.contains("https://example.com/secret-page"))
        assertEquals(OpenedBackup.Plain(backup), BackupFile.read(bytes))
    }

    @Test
    fun anEncryptedBackupHidesItsContentAndOpensWithItsPassword() {
        val bytes = BackupFile.write(backup, "correct horse".toCharArray(), iterations)
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("\"format\":\"okapi-backup-encrypted\""))
        assertFalse(text.contains("example.com"))
        assertFalse(text.contains("Work"))
        val opened = BackupFile.read(bytes)
        assertTrue(opened is OpenedBackup.Locked)
        assertEquals(backup, BackupFile.unlock(opened as OpenedBackup.Locked, "correct horse".toCharArray()))
    }

    @Test
    fun twoEncryptionsOfTheSameBackupDiffer() {
        val password = "correct horse"
        val first = BackupFile.write(backup, password.toCharArray(), iterations).toString(Charsets.UTF_8)
        val second = BackupFile.write(backup, password.toCharArray(), iterations).toString(Charsets.UTF_8)
        assertTrue(first != second)
    }

    @Test
    fun aWrongPasswordIsTold() {
        val locked = BackupFile.read(BackupFile.write(backup, "correct horse".toCharArray(), iterations)) as OpenedBackup.Locked
        assertEquals(BackupProblem.WrongPassword, problemOf { BackupFile.unlock(locked, "wrong horse".toCharArray()) })
    }

    @Test
    fun aChangedFileFailsLikeAWrongPassword() {
        val text = BackupFile.write(backup, "correct horse".toCharArray(), iterations).toString(Charsets.UTF_8)
        // The iteration count is bound to the ciphertext: changing it doesn't open the file.
        val changedHeader = text.replace("\"iterations\":$iterations", "\"iterations\":${iterations + 1}")
        val locked = BackupFile.read(changedHeader.toByteArray()) as OpenedBackup.Locked
        assertEquals(BackupProblem.WrongPassword, problemOf { BackupFile.unlock(locked, "correct horse".toCharArray()) })

        val data = Regex("\"data\":\"([^\"]+)\"").find(text)!!.groupValues[1]
        val flipped = (if (data[10] == 'A') 'B' else 'A').toString()
        val changedData = text.replace(data, data.substring(0, 10) + flipped + data.substring(11))
        val lockedData = BackupFile.read(changedData.toByteArray()) as OpenedBackup.Locked
        assertEquals(BackupProblem.WrongPassword, problemOf { BackupFile.unlock(lockedData, "correct horse".toCharArray()) })
    }

    @Test
    fun whatIsNotABackupIsTold() {
        assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read("not json".toByteArray()) })
        assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read("[1,2]".toByteArray()) })
        assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read("{\"format\":\"something\",\"version\":1}".toByteArray()) })
        // A backup has at least one Profile.
        assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read("{\"format\":\"okapi-backup\",\"version\":1,\"createdAt\":0,\"profiles\":[]}".toByteArray()) })
        assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read("{\"format\":\"okapi-backup\",\"version\":1,\"createdAt\":0,\"profiles\":3}".toByteArray()) })
    }

    @Test
    fun aBackupFromALaterVersionIsTold() {
        val later = BackupFile.write(backup.copy(version = Backup.VERSION + 1), password = null)
        assertEquals(BackupProblem.TooNew, problemOf { BackupFile.read(later) })
    }

    @Test
    fun aVersionBelowOneIsNoBackup() {
        listOf(0, -1).forEach { version ->
            val plain = BackupFile.write(backup.copy(version = version), password = null)
            assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read(plain) })
            val sealed = BackupFile.write(backup.copy(version = version), "correct horse".toCharArray(), iterations)
            val locked = BackupFile.read(sealed) as OpenedBackup.Locked
            assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.unlock(locked, "correct horse".toCharArray()) })
        }
    }

    @Test
    fun noFileIsWrittenThatReadingWouldRefuse() {
        val longPage = "https://example.com/" + "a".repeat(2_000)
        val huge = backup.copy(profiles = listOf(backup.profiles[0].copy(history = List(9_000) { BackupVisit(longPage, visitedAt = it.toLong()) })))
        assertEquals(BackupProblem.TooLarge, problemOf { BackupFile.write(huge, password = null) })
        assertEquals(BackupProblem.TooLarge, problemOf { BackupFile.write(huge, "correct horse".toCharArray(), iterations) })
    }

    @Test
    fun fieldsALaterVersionAddsAreIgnored() {
        val text = BackupFile.write(backup, password = null).toString(Charsets.UTF_8).replaceFirst("{", "{\"addedLater\":{\"x\":1},")
        assertEquals(OpenedBackup.Plain(backup), BackupFile.read(text.toByteArray()))
    }

    @Test
    fun anEncryptedFileThatAsksForEndlessWorkIsRefused() {
        val text = BackupFile.write(backup, "correct horse".toCharArray(), iterations).toString(Charsets.UTF_8)
        val greedy = text.replace("\"iterations\":$iterations", "\"iterations\":2000000000")
        assertEquals(BackupProblem.NotABackup, problemOf { BackupFile.read(greedy.toByteArray()) })
    }

    @Test
    fun aFileLargerThanAnyBackupIsRefusedUnread() {
        assertEquals(BackupProblem.TooLarge, problemOf { BackupFile.read(ByteArray(BackupFile.MAX_BYTES + 1)) })
    }

    @Test
    fun theSummarySaysWhatTheBackupHolds() {
        val withEmptyTab = backup.copy(profiles = backup.profiles.map { it.copy(tabs = it.tabs + BackupTab()) })
        assertEquals(BackupSummary(listOf("Work"), bookmarks = 1, visits = 1, tabs = 1), withEmptyTab.summary)
    }

    @Test
    fun theRealWorkFactorIsUsedByDefault() {
        val text = BackupFile.write(backup, "correct horse".toCharArray()).toString(Charsets.UTF_8)
        if (!text.contains("\"iterations\":${BackupFile.ITERATIONS}")) fail(text.take(200))
    }
}
