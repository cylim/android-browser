package app.jrny.browser.tab

import android.content.Context
import android.net.Uri
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * A site asks for the camera, the microphone or the device's location (SPEC, "Site permissions
 * and Site settings"). It is answered exactly once: [grant] or [deny]. Answering a request that
 * the page has already withdrawn does nothing.
 *
 * [decided] is true when the person's choice is already known (a remembered or once-only
 * grant), so only Android's own permission is left to check before [grant].
 */
class PermissionAsk(
    val origin: SiteOrigin,
    val wanted: Set<SiteSetting>,
    private val onGrant: () -> Unit,
    private val onDeny: () -> Unit,
    val decided: Boolean = false,
) {
    /** True until the ask is answered or withdrawn. */
    var open: Boolean = true
        private set

    fun grant() = answer(onGrant)

    fun deny() = answer(onDeny)

    /** The page withdrew the ask: it gets no answer. */
    fun withdraw() {
        open = false
    }

    /** The same ask, with the person's choice already known. One the page withdrew stays withdrawn. */
    fun asDecided(): PermissionAsk {
        if (!open) return this
        return PermissionAsk(origin, wanted, onGrant, onDeny, decided = true).also { open = false }
    }

    private fun answer(action: () -> Unit) {
        if (!open) return
        open = false
        action()
    }
}

/**
 * Answers one of GeckoView's prompts exactly once: with what [answer] makes of the prompt, or
 * dismissed. A prompt the page already took back gets nothing.
 */
internal class PromptAnswer<P : PromptDelegate.BasePrompt>(val prompt: P) {
    val result = GeckoResult<PromptResponse>()
    private var answered = false

    fun confirm(answer: (P) -> PromptResponse) {
        if (answered || prompt.isComplete) return
        answered = true
        result.complete(answer(prompt))
    }

    fun dismiss() {
        if (answered || prompt.isComplete) return
        answered = true
        result.complete(prompt.dismiss())
    }
}

/**
 * A page's file field wants files (SPEC, "File upload"). [answer] hands the chosen files to the
 * page, or none when the person cancelled; the page waits until it is called, and can't ask again
 * before.
 */
class FileChooserAsk internal constructor(private val context: Context, private val reply: PromptAnswer<PromptDelegate.FilePrompt>) {
    private val field: PromptDelegate.FilePrompt = reply.prompt

    /** The kinds of file the page accepts: MIME types and `.ext` suffixes, as the page wrote them. */
    val acceptTypes: List<String> = field.mimeTypes.orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** True when the page takes several files. */
    val multiple: Boolean = field.type == PromptDelegate.FilePrompt.Type.MULTIPLE

    fun answer(files: List<Uri>?) {
        val chosen = files?.takeIf { it.isNotEmpty() }
        if (chosen == null) return reply.dismiss()
        reply.confirm { if (multiple) it.confirm(context, chosen.toTypedArray()) else it.confirm(context, chosen.first()) }
    }
}

/**
 * A site asks for a username and password before it sends the page (SPEC, "HTTP sign-in").
 * Nothing typed is kept.
 */
class HttpAuthAsk internal constructor(val host: String, val realm: String, private val prompt: PromptAnswer<PromptDelegate.AuthPrompt>) {
    fun signIn(username: String, password: String) = prompt.confirm { it.confirm(username, password) }

    fun cancel() = prompt.dismiss()
}

/** The page shows its video, or another element, fullscreen (SPEC, "Fullscreen video"). */
class FullscreenVideo(private val onEnd: () -> Unit) {
    private var ended = false

    /** Asks the page to leave fullscreen. */
    fun end() {
        if (ended) return
        ended = true
        onEnd()
    }
}

/**
 * Something a page's script asks the person, as a dialog: an alert, a question, text to type, a
 * choice from a list (a `<select>`), a date, time or color for a form field, or whether to leave
 * the page. Answered exactly once; dismissing is the page's "Cancel".
 */
sealed class PagePrompt {
    internal abstract val answer: PromptAnswer<*>

    /**
     * The site that asks, for display: the Tab's page or its popup, whichever made the dialog.
     * Empty when it has no web address.
     */
    var site: String = ""
        internal set

    fun dismiss() = answer.dismiss()

    /** `alert()`: a message and OK. */
    class Alert internal constructor(override val answer: PromptAnswer<PromptDelegate.AlertPrompt>) : PagePrompt() {
        val message: String = answer.prompt.message.orEmpty()

        fun ok() = answer.dismiss()
    }

    /** `confirm()`: a question with OK and Cancel. */
    class Confirm internal constructor(override val answer: PromptAnswer<PromptDelegate.ButtonPrompt>) : PagePrompt() {
        val message: String = answer.prompt.message.orEmpty()

        fun answer(ok: Boolean) = answer.confirm {
            it.confirm(if (ok) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE)
        }
    }

    /** `prompt()`: a question with a line of text to type. */
    class Text internal constructor(override val answer: PromptAnswer<PromptDelegate.TextPrompt>) : PagePrompt() {
        val message: String = answer.prompt.message.orEmpty()
        val defaultValue: String = answer.prompt.defaultValue.orEmpty()

        fun answer(text: String) = answer.confirm { it.confirm(text) }
    }

    /** The page wants to keep the person from leaving it, maybe with unsaved changes. */
    class LeavePage internal constructor(override val answer: PromptAnswer<PromptDelegate.BeforeUnloadPrompt>) : PagePrompt() {
        fun answer(leave: Boolean) = answer.confirm { it.confirm(if (leave) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
    }

    /** Reloading the page would send a form again. */
    class Resend internal constructor(override val answer: PromptAnswer<PromptDelegate.RepostConfirmPrompt>) : PagePrompt() {
        fun answer(resend: Boolean) = answer.confirm { it.confirm(if (resend) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
    }

    /** A choice from a list, such as a `<select>`: one item ([multiple] false) or several. */
    class Choice internal constructor(override val answer: PromptAnswer<PromptDelegate.ChoicePrompt>) : PagePrompt() {
        /** One entry of the list. A group has [items] and can't be chosen itself. */
        data class Item(val id: String, val label: String, val selected: Boolean, val disabled: Boolean, val items: List<Item>, val separator: Boolean)

        val multiple: Boolean = answer.prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
        val items: List<Item> = answer.prompt.choices.orEmpty().map(::item)

        fun choose(ids: List<String>) = answer.confirm { prompt ->
            if (multiple) prompt.confirm(ids.toTypedArray()) else prompt.confirm(ids.first())
        }

        private companion object {
            fun item(choice: PromptDelegate.ChoicePrompt.Choice): Item =
                Item(choice.id, choice.label.orEmpty(), choice.selected, choice.disabled, choice.items.orEmpty().map(::item), choice.separator)
        }
    }

    /** A date, time, month or week for a form field, as the field writes it (`2026-11-19`, `13:45`). */
    class DateTime internal constructor(override val answer: PromptAnswer<PromptDelegate.DateTimePrompt>) : PagePrompt() {
        enum class Kind { Date, Month, Week, Time, DateTimeLocal }

        val kind: Kind = when (answer.prompt.type) {
            PromptDelegate.DateTimePrompt.Type.MONTH -> Kind.Month
            PromptDelegate.DateTimePrompt.Type.WEEK -> Kind.Week
            PromptDelegate.DateTimePrompt.Type.TIME -> Kind.Time
            PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> Kind.DateTimeLocal
            else -> Kind.Date
        }
        val value: String = answer.prompt.defaultValue.orEmpty()
        val min: String = answer.prompt.minValue.orEmpty()
        val max: String = answer.prompt.maxValue.orEmpty()

        fun answer(value: String) = answer.confirm { it.confirm(value) }
    }

    /** A color for a form field, as `#rrggbb`. */
    class Color internal constructor(override val answer: PromptAnswer<PromptDelegate.ColorPrompt>) : PagePrompt() {
        val value: String = answer.prompt.defaultValue.orEmpty()

        fun answer(color: String) = answer.confirm { it.confirm(color) }
    }
}
