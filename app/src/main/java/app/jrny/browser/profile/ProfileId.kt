package app.jrny.browser.profile

/** Identifies a Profile. It names the Profile's web data store on disk, so it never changes (ADR 0004). */
@JvmInline
value class ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "Profile ID is blank" }
    }
}
