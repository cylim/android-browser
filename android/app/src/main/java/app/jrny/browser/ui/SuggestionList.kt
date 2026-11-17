package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.settings.Suggestion
import app.jrny.browser.tab.displayHost

/**
 * What the address field offers while the person types: pages from the Active Profile's
 * bookmarks and history, then searches. Rows are 56dp; the icon starts 16dp in, on the layout's
 * text line, and the text follows 16dp after it.
 */
@Composable
fun SuggestionList(suggestions: List<Suggestion>, onChoose: (Suggestion) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("suggestions")) {
        items(suggestions) { suggestion ->
            val page = suggestion.url
            val search = stringResource(R.string.search_for, suggestion.text)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onChoose(suggestion) }
                    // A search says so to a screen reader; a page is read as its title and site.
                    .semantics(mergeDescendants = true) { if (page == null) contentDescription = search }
                    .padding(horizontal = 16.dp)
                    .testTag("suggestion"),
            ) {
                Icon(
                    when (suggestion.kind) {
                        Suggestion.Kind.Search -> Icons.Filled.Search
                        Suggestion.Kind.Bookmark -> Icons.Filled.Star
                        Suggestion.Kind.History -> JrnyIcons.History
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                    Text(suggestion.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val host = displayHost(page)
                    if (host.isNotEmpty() && host != suggestion.text) {
                        Text(
                            host,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
