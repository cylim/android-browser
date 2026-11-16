package app.jrny.browser.profile

import app.jrny.browser.data.ProfileDao
import app.jrny.browser.data.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The Profiles and which one is active (SPEC, "Profiles"). */
class ProfileRepository(private val dao: ProfileDao) {

    /** Live Profiles, in the order they were created. */
    val profiles: Flow<List<Profile>> = dao.observeLive().map { rows -> rows.map(::toProfile) }

    /** The Active Profile. Null only before [ensureFirstProfile] has run. */
    val activeProfile: Flow<Profile?> = dao.observeActive().map { it?.let(::toProfile) }

    /** How many deleted Profiles still wait for their web data to be wiped at the next cold start. */
    val pendingDeletionCount: Flow<Int> = dao.observePendingCount()

    /**
     * On first launch, creates the first Profile with the id 1, which existing web data is stored
     * under (ADR 0004). Does nothing once any Profile exists.
     */
    suspend fun ensureFirstProfile(name: String) {
        if (dao.countAll() > 0) return
        dao.insertActive(ProfileEntity(id = FIRST_ID, name = name, color = ProfileColor.Blue.name, icon = ProfileIcon.Person.name))
    }

    /** Creates a Profile and makes it the Active Profile. */
    suspend fun create(name: String, color: ProfileColor, icon: ProfileIcon): Profile {
        val clean = requireNotNull(ProfileName.clean(name)) { "Not a Profile name" }
        val id = dao.insertActive(ProfileEntity(name = clean, color = color.name, icon = icon.name))
        return Profile(ProfileId(id.toString()), clean, color, icon)
    }

    /** Renames, recolors or changes the icon of [profile]. */
    suspend fun update(profile: Profile) {
        val clean = requireNotNull(ProfileName.clean(profile.name)) { "Not a Profile name" }
        dao.update(profile.id.toLong(), clean, profile.color.name, profile.icon.name)
    }

    suspend fun activate(id: ProfileId) = dao.activate(id.toLong())

    /**
     * Deletes [id]: it disappears at once and its web data is wiped at the next cold start. If it
     * was the Active Profile, the one active before it takes over. Returns false, and does
     * nothing, for the last Profile, which can't be deleted.
     */
    suspend fun delete(id: ProfileId): Boolean = dao.markPendingUnlessLast(id.toLong())

    suspend fun pendingDeletion(): List<ProfileId> = dao.pendingIds().map { ProfileId(it.toString()) }

    /** Forgets a deleted Profile once its web data is gone. */
    suspend fun purge(id: ProfileId) = dao.purge(id.toLong())

    private fun toProfile(row: ProfileEntity) = Profile(
        id = ProfileId(row.id.toString()),
        name = row.name,
        color = enumValueOrNull<ProfileColor>(row.color) ?: ProfileColor.Blue,
        icon = enumValueOrNull<ProfileIcon>(row.icon) ?: ProfileIcon.Person,
    )

    private fun ProfileId.toLong(): Long = value.toLong()

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    companion object {
        /** The first Profile's id, kept from Milestone 1 (ADR 0004). */
        const val FIRST_ID = 1L
    }
}
