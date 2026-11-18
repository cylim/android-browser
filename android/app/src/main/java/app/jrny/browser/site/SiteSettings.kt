package app.jrny.browser.site

import app.jrny.browser.data.SiteSettingDao
import app.jrny.browser.data.SiteSettingEntity
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What a Profile can remember about a site (GLOSSARY, "Site settings"). [key] is what the
 * database stores, so it never changes.
 */
enum class SiteSetting(val key: String) {
    Camera("camera"),
    Microphone("microphone"),
    Location("location"),

    /** The site gets its desktop version (SPEC, "Desktop site"). */
    DesktopSite("desktop");

    /** True for what a site asks the person for: the camera, the microphone and location. */
    val isPermission: Boolean get() = this != DesktopSite

    companion object {
        fun fromKey(key: String): SiteSetting? = entries.firstOrNull { it.key == key }

        val permissions: List<SiteSetting> = entries.filter { it.isPermission }
    }
}

/** One site of a Profile's Site settings, with each setting it has: on (allowed) or off (blocked). */
data class SiteSettingsEntry(val origin: SiteOrigin, val settings: Map<SiteSetting, Boolean>)

/**
 * Each Profile's Site settings. A setting a site doesn't have is unset: a permission is asked for,
 * and the site gets its mobile version.
 */
class SiteSettingsRepository(private val dao: SiteSettingDao) {

    /** [profile]'s sites with at least one setting, in alphabetical order of their origin. */
    fun sites(profile: ProfileId): Flow<List<SiteSettingsEntry>> = dao.observe(profile.rowId).map { rows ->
        rows.groupBy { it.origin }.mapNotNull { (origin, settings) ->
            val site = SiteOrigin.of(origin) ?: return@mapNotNull null
            SiteSettingsEntry(site, settings.mapNotNull { row -> SiteSetting.fromKey(row.setting)?.let { it to row.enabled } }.toMap())
        }
    }

    /** For every Profile, its sites that get their desktop version (SPEC, "Desktop site"). */
    fun desktopSites(): Flow<Map<ProfileId, Set<SiteOrigin>>> = dao.observeOn(SiteSetting.DesktopSite.key).map { rows ->
        rows.groupBy { ProfileId(it.profileId.toString()) }.mapValues { (_, sites) -> sites.mapNotNull { SiteOrigin.of(it.origin) }.toSet() }
    }

    /** What [profile] keeps for [setting] on [origin]: true (allowed), false (blocked), or null (unset). */
    suspend fun get(profile: ProfileId, origin: SiteOrigin, setting: SiteSetting): Boolean? =
        dao.get(profile.rowId, origin.text, setting.key)

    /** Keeps [enabled] for [setting] on [origin]. Nothing is kept for a deleted Profile. */
    suspend fun set(profile: ProfileId, origin: SiteOrigin, setting: SiteSetting, enabled: Boolean) {
        dao.set(SiteSettingEntity(profile.rowId, origin.text, setting.key, enabled))
    }

    /** Unsets [setting] on [origin]: a permission is asked for again. */
    suspend fun reset(profile: ProfileId, origin: SiteOrigin, setting: SiteSetting) =
        dao.delete(profile.rowId, origin.text, setting.key)

    /** Unsets every setting of every site of [profile]. */
    suspend fun resetAll(profile: ProfileId) = dao.deleteAll(profile.rowId)

    private val ProfileId.rowId: Long get() = value.toLong()
}
