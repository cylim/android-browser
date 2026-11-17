package app.jrny.browser.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import app.jrny.browser.R
import app.jrny.browser.address.AddressInput
import app.jrny.browser.tab.Tab
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the person is typing in the address field, or null while they aren't editing. BrowserScreen
 * keeps it above the layouts, so it survives a resize across 840dp, even into the Rail, which has
 * no address field.
 */
class AddressDraft {
    var value: TextFieldValue? by mutableStateOf(null)
}

/**
 * The address and search field. It shows the page's address, selects all of it when tapped,
 * and loads what was typed (an address or a search) on Go.
 *
 * BrowserScreen moves one instance of this field between the layouts (movableContentOf), and
 * passes the current [layout], so the field takes focus back after a move.
 */
@Composable
fun AddressField(
    tab: Tab,
    draft: AddressDraft,
    modifier: Modifier,
    shape: Shape,
    layout: BrowserLayout,
    private: Boolean = false,
    /** The chosen search engine's address for a search, with `%s` for the query. */
    searchTemplate: String = AddressInput.GOOGLE_SEARCH,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Null while not editing: the field then follows the page's address.
    var editing by draft::value
    // The tap that focuses the field also moves the cursor to where it landed, right after
    // focus selects everything. Keep the whole address selected through that first change,
    // so typing replaces it.
    var keepSelectAll by remember { mutableStateOf(false) }
    // Ends the edit a moment after focus leaves (see onFocusChanged below).
    val pendingBlur = remember { arrayOfNulls<Job>(1) }

    fun submit() {
        val address = AddressInput.toAddress(editing?.text.orEmpty(), searchTemplate) ?: return
        tab.load(address)
        editing = null
        // Hand focus to the page, as browsers do. Only clearing it isn't enough: when the
        // landscape fullscreen keyboard closes, Android gives focus back to the field.
        focusManager.clearFocus(force = true)
        tab.webView.requestFocus()
    }

    TextField(
        value = editing ?: TextFieldValue(shownAddress(tab.url)),
        onValueChange = { value ->
            // Not editing: ignore. The landscape fullscreen keyboard sends a last, empty value
            // after Go, once the edit has ended, which would blank the address.
            val previous = editing ?: return@TextField
            editing = if (keepSelectAll && value.text == previous.text) {
                value.copy(selection = TextRange(0, value.text.length))
            } else {
                value
            }
            keepSelectAll = false
        },
        // One line, cut short if it must be: in a phone's narrow field the hint would wrap and
        // make the whole bar taller.
        placeholder = { Text(stringResource(R.string.address_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // A lock marks a Private Tab's address field, in both layouts.
        leadingIcon = if (private) {
            { Icon(Icons.Filled.Lock, stringResource(R.string.private_tab), modifier = Modifier.testTag("private-mark")) }
        } else {
            null
        },
        // In the Bottom Bar, reload sits inside the field, which leaves room for the Tab count.
        // The Sidebar has it above, with back and forward. Hidden while typing.
        trailingIcon = if (layout == BrowserLayout.BottomBar && editing == null) {
            { ReloadOrStopButton(tab) }
        } else {
            null
        },
        singleLine = true,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    pendingBlur[0]?.cancel()
                    pendingBlur[0] = null
                    if (editing == null) {
                        // Read the Tab's address now, not one captured at the last composition:
                        // a load may have just started.
                        val address = shownAddress(tab.url)
                        editing = TextFieldValue(address, TextRange(0, address.length))
                        keepSelectAll = true
                    }
                } else if (editing != null && pendingBlur[0] == null) {
                    // Moving to the other layout detaches the field for a moment, which also
                    // ends focus. Wait two frames: a move takes focus back (LaunchedEffect below)
                    // and keeps the edit, and leaving the composition (the Rail) cancels this
                    // with the scope, so the draft waits. Any other loss of focus ends the edit.
                    pendingBlur[0] = scope.launch {
                        withFrameNanos {}
                        withFrameNanos {}
                        editing = null
                        pendingBlur[0] = null
                    }
                }
            }
            .testTag("address"),
    )

    // A new, empty Tab opens with the keyboard up, ready for an address. After a move to the
    // other layout, an unfinished edit takes focus back.
    LaunchedEffect(layout) {
        if (tab.url == null || editing != null) focusRequester.requestFocus()
    }
}

private fun shownAddress(url: String?): String = if (url == null || url == "about:blank") "" else url
