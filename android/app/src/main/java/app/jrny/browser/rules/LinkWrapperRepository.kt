package app.jrny.browser.rules

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Keeps the person's changes to the default list of Link Wrappers ([LinkWrapperList.own] and
 * [LinkWrapperList.removed]) in the app's own preferences file, which never leaves the device
 * (backups are off). The default list itself is never stored: [defaults], this version's, applies,
 * so the defaults a later version adds reach everyone. Nothing is stored while the list is the
 * default one. What this version can't read is left out.
 */
class LinkWrapperRepository(private val preferences: SharedPreferences, private val defaults: List<LinkWrapper> = LinkWrappers.DEFAULT) {
    private val state = MutableStateFlow(read())

    val list: StateFlow<LinkWrapperList> = state.asStateFlow()

    /**
     * Changes the list as [change] says, at once for every reader, and keeps it. One change at a
     * time, so the list kept is always the newest.
     */
    @Synchronized
    fun update(change: (LinkWrapperList) -> LinkWrapperList) {
        val changed = change(state.value)
        state.value = changed
        preferences.edit {
            if (changed.isDefault) {
                remove(KEY)
            } else {
                putString(KEY, json.encodeToString(Stored.serializer(), Stored.of(changed)))
            }
        }
    }

    private fun read(): LinkWrapperList {
        val start = LinkWrapperList(defaults = defaults)
        val text = preferences.getString(KEY, null) ?: return start
        val stored = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return start
        val own = (stored["own"] as? JsonArray).orEmpty().mapNotNull(::wrapperOf)
        val removed = (stored["removed"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(SitePattern::parse) }
        return restore(start, own, removed)
    }

    private fun wrapperOf(row: JsonElement): LinkWrapper? =
        runCatching { json.decodeFromJsonElement(StoredWrapper.serializer(), row) }.getOrNull()?.toWrapper()

    @Serializable
    private data class Stored(val own: List<StoredWrapper>, val removed: List<String>) {
        companion object {
            fun of(list: LinkWrapperList) = Stored(list.own.map { StoredWrapper(it.pattern.text, it.parameters) }, list.removed.map { it.text })
        }
    }

    @Serializable
    private data class StoredWrapper(val pattern: String, val parameters: List<String>) {
        fun toWrapper(): LinkWrapper? = LinkWrapper.of(pattern, parameters)
    }

    companion object {
        private const val KEY = "link_wrappers"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * [start] with the changes [own] and [removed] made to it, as a person would make them, so
         * nothing that the editor refuses gets in: a wrapper for a site that has one is left out.
         */
        fun restore(start: LinkWrapperList, own: List<LinkWrapper>, removed: List<SitePattern>): LinkWrapperList {
            val withRemoved = removed.fold(start) { list, site ->
                list.all.firstOrNull { it.pattern == site }?.let(list::delete) ?: list
            }
            return own.distinctBy { it.pattern }.fold(withRemoved) { list, wrapper ->
                list.save(list.all.firstOrNull { it.pattern == wrapper.pattern }, wrapper)
            }
        }
    }
}
