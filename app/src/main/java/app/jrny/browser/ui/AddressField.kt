package app.jrny.browser.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.address.AddressInput
import app.jrny.browser.tab.Tab

/**
 * The address and search field. It shows the page's address, selects all of it when tapped,
 * and loads what was typed (an address or a search) on Go.
 */
@Composable
fun AddressField(tab: Tab, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp)) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val current = shownAddress(tab.url)
    // Null while the field isn't focused: it then follows the page's address.
    var editing by remember { mutableStateOf<TextFieldValue?>(null) }
    // The tap that focuses the field also moves the cursor to where it landed, right after
    // focus selects everything. Keep the whole address selected through that first change,
    // so typing replaces it.
    var keepSelectAll by remember { mutableStateOf(false) }

    fun submit() {
        val address = AddressInput.toAddress(editing?.text.orEmpty()) ?: return
        tab.load(address)
        // Hand focus to the page, as browsers do. Only clearing it isn't enough: when the
        // landscape fullscreen keyboard closes, Android gives focus back to the field.
        focusManager.clearFocus(force = true)
        tab.webView.requestFocus()
    }

    TextField(
        value = editing ?: TextFieldValue(current),
        onValueChange = { value ->
            // Not focused: ignore. The landscape fullscreen keyboard sends a last, empty value
            // after Go, once the field has lost focus, which would blank the address.
            val previous = editing ?: return@TextField
            editing = if (keepSelectAll && value.text == previous.text) {
                value.copy(selection = TextRange(0, value.text.length))
            } else {
                value
            }
            keepSelectAll = false
        },
        placeholder = { Text(stringResource(R.string.address_hint)) },
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
                if (state.isFocused == (editing != null)) return@onFocusChanged
                // Read the Tab's address now, not the one captured at the last composition: a
                // load may have just started.
                val address = shownAddress(tab.url)
                editing = if (state.isFocused) TextFieldValue(address, TextRange(0, address.length)) else null
                keepSelectAll = state.isFocused
            }
            .testTag("address"),
    )

    // A new, empty Tab opens with the keyboard up, ready for an address.
    LaunchedEffect(Unit) {
        if (tab.url == null) focusRequester.requestFocus()
    }
}

private fun shownAddress(url: String?): String = if (url == null || url == "about:blank") "" else url
