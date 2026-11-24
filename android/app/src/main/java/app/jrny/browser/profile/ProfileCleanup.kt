package app.jrny.browser.profile

/**
 * Wipes the web data of deleted Profiles. It runs at every cold start, once the engine has
 * started: a second pass after the wipe at deletion, for anything a page still on screen wrote
 * (ADR 0006). A deleted Profile has no Tabs, so no page of it loads meanwhile.
 *
 * [deleteWebData] returns once the engine has the wipe in hand, and throws if it can't take it.
 * A Profile whose data couldn't be handed over stays pending and is tried again at the next cold
 * start.
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
