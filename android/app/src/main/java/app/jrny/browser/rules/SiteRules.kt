package app.jrny.browser.rules

import app.jrny.browser.data.SiteRuleDao
import app.jrny.browser.data.SiteRuleEntity
import app.jrny.browser.profile.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A person's rule that sends Incoming Links for the addresses [pattern] covers to the Profile
 * [profileId] instead of the Active Profile (GLOSSARY, "Site Rule").
 */
data class SiteRule(val id: Long, val pattern: SitePattern, val profileId: ProfileId)

/**
 * The rule for [url] among these: the one whose pattern covers it. When several do, the
 * narrowest wins ([SitePattern.weight]), and between equally narrow ones the older rule.
 */
fun List<SiteRule>.ruleFor(url: String): SiteRule? =
    filter { it.pattern.matches(url) }.maxWithOrNull(compareBy<SiteRule> { it.pattern.weight }.thenByDescending { it.id })

/** The Site Rules, which belong to the app and not to one Profile. [now] gives a rule's creation time. */
class SiteRuleRepository(private val dao: SiteRuleDao, private val now: () -> Long = System::currentTimeMillis) {

    /** Every rule, oldest first. */
    val rules: Flow<List<SiteRule>> = dao.observe().map { rows -> rows.mapNotNull(::toRule) }

    /** The rule that says where a link to [url] opens, as the rules stand now, or null when none covers it. */
    suspend fun ruleFor(url: String): SiteRule? = dao.all().mapNotNull(::toRule).ruleFor(url)

    /**
     * Saves a rule that sends [pattern] to [profile]: [existing] is changed, or a new rule is
     * added. A rule that already has this pattern is replaced, so a pattern names one Profile.
     * Returns false, with nothing saved, when the Profile is gone.
     */
    suspend fun save(existing: SiteRule?, pattern: SitePattern, profile: ProfileId): Boolean =
        dao.save(existing?.id, pattern.text, profile.value.toLong(), now()) != null

    suspend fun delete(rule: SiteRule) = dao.delete(rule.id)

    // A stored pattern always parses: only SitePattern.text is ever written.
    private fun toRule(row: SiteRuleEntity): SiteRule? =
        SitePattern.parse(row.pattern)?.let { SiteRule(row.id, it, ProfileId(row.profileId.toString())) }
}
