package app.jrny.browser.profile

/**
 * Wipes the web data of deleted Profiles. It runs at every cold start, before any WebView loads a
 * Profile: WebView refuses to delete a Profile that's in use (ADR 0001).
 *
 * [deleteWebData] deletes one Profile's web data and throws if it can't. A Profile whose data
 * couldn't be deleted stays pending and is tried again at the next cold start.
 */
class ProfileCleanup(
    private val profiles: ProfileRepository,
    private val deleteWebData: (ProfileId) -> Unit,
) {
    /** Returns how many deleted Profiles were wiped and forgotten. */
    suspend fun run(): Int {
        var wiped = 0
        for (id in profiles.pendingDeletion()) {
            if (runCatching { deleteWebData(id) }.isSuccess) {
                profiles.purge(id)
                wiped++
            }
        }
        return wiped
    }
}
