package app.jrny.browser.backup

/** Where an export or an import of a backup stands, for the window to show (SPEC, "Backup"). */
sealed interface BackupStep {
    /** Writing the file the person chose. */
    data object Exporting : BackupStep

    data object Exported : BackupStep

    /** Reading the file the person chose, or trying their password on it. */
    data object Reading : BackupStep

    /** The file is encrypted. [wrong] says the last password didn't open it. */
    data class NeedsPassword(val wrong: Boolean = false) : BackupStep

    /** The file is open: the person sees what it holds and chooses how to import it. */
    data class Ready(val summary: BackupSummary) : BackupStep

    data object Importing : BackupStep

    data class Imported(val mode: ImportMode) : BackupStep

    data class Failed(val failure: BackupFailure) : BackupStep
}

enum class BackupFailure {
    /** The chosen place couldn't be written to. */
    CantWrite,

    /** The chosen file couldn't be read. */
    CantRead,

    /** The database refused the import, so nothing was imported. */
    CantImport,

    /** It isn't an Okapi backup, or it is damaged. */
    NotABackup,

    /** A later version of Okapi made it. */
    TooNew,

    /** Larger than any backup Okapi makes. */
    TooLarge;

    companion object {
        fun of(problem: BackupProblem): BackupFailure = when (problem) {
            BackupProblem.NotABackup, BackupProblem.WrongPassword -> NotABackup
            BackupProblem.TooNew -> TooNew
            BackupProblem.TooLarge -> TooLarge
        }
    }
}
