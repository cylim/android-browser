package app.jrny.browser.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.tab.Tab

/**
 * Find in page (SPEC, "Find in page"): a field, how many matches there are and which one is
 * current, the arrows to the next and the one before, and a close button. It takes the focus as
 * it opens, so the keyboard comes up; Enter goes to the next match.
 */
@Composable
fun FindBar(tab: Tab, shape: Shape, modifier: Modifier = Modifier) {
    val state = tab.findInPage.state ?: return
    var text by remember { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.testTag("find-bar")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp).padding(start = 16.dp)) {
            val hint = stringResource(R.string.find_in_page)
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    tab.findInPage.find(it)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { tab.findInPage.next(forward = true) }),
                decorationBox = { field ->
                    if (text.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                },
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = hint }.testTag("find-field"),
            )
            if (state.query.isNotEmpty()) {
                Text(
                    stringResource(R.string.find_count, state.current, state.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("find-count"),
                )
            }
            IconButton(onClick = { tab.findInPage.next(forward = false) }, enabled = state.total > 0, modifier = Modifier.testTag("find-previous")) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous))
            }
            IconButton(onClick = { tab.findInPage.next(forward = true) }, enabled = state.total > 0, modifier = Modifier.testTag("find-next")) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next))
            }
            IconButton(onClick = tab.findInPage::close, modifier = Modifier.testTag("find-close")) {
                Icon(Icons.Filled.Close, stringResource(R.string.close_find))
            }
        }
    }
}
