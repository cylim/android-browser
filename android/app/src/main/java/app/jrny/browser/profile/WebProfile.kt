package app.jrny.browser.profile

import android.content.Context
import app.jrny.browser.engine.Engine

/**
 * The web data store of one Profile: a GeckoView session context holding its cookies, site
 * storage, cache and service workers (ADR 0004, ADR 0006). Nothing in one store is visible from
 * another. A private store keeps its data in memory ([isPrivate]), apart from site databases
 * (SPEC, "Private Tabs").
 */
class WebProfile private constructor(
    /** The session context's id. */
    val name: String,
    val isPrivate: Boolean,
) {
    /**
     * Deletes everything the store keeps. GeckoView can do this while the app runs. [handedOver]
     * runs once the engine has the wipe in hand; it gives no word when the wipe is done.
     */
    fun clear(context: Context, handedOver: () -> Unit = {}) = Engine.clearContext(context, name, handedOver)

    override fun equals(other: Any?): Boolean = other is WebProfile && other.name == name && other.isPrivate == isPrivate

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    companion object {
        /**
         * The store for the Profile [id], named `jrny-profile-<id>` (ADR 0004). No Profile uses
         * GeckoView's default context, so a page loaded outside any Profile can't see a
         * Profile's data.
         */
        fun of(id: ProfileId): WebProfile = WebProfile("jrny-profile-${id.value}", isPrivate = false)

        /**
         * The temporary store for one private session of the Profile [id] (SPEC, "Private Tabs").
         * Each session has a store of its own, so a new session never meets what an earlier one
         * left behind. It keeps its data in memory, apart from site databases, which are encrypted
         * on disk until no private store is left.
         */
        fun privateOf(id: ProfileId, session: Int): WebProfile = WebProfile("jrny-private-${id.value}-$session", isPrivate = true)
    }
}
