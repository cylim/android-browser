package app.jrny.browser.profile

/**
 * Wipes the web data of deleted Profiles. It runs at every cold start, once the engine has
 * started: a second pass after the wipe at deletion, for anything a page still on screen wrote
 * (ADR 0006). A deleted Profile has no Tabs, so no page of it loads meanwhile.
 *
 * [deleteWebData] returns once the wipe has had time to finish, and throws if the engine can't
 * take it. A Profile whose wipe didn't get that far, because it threw or the app ended first,
 * stays pending and is wiped again at the next cold start.
 */
class ProfileCleanup(
    private val profiles: ProfileRepository,
    private val deleteWebData: suspend (ProfileId) -> Unit,
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
