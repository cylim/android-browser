package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Folder
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.library.ProfilePages
import app.jrny.browser.library.Visit
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.tab.displayHost

/** What the Start Page shows (SPEC, "Start Page"). */
sealed interface StartPageContent {
    /** A Private Tab: what it keeps and what it discards, and nothing from the Profile's history or bookmarks. */
    data object Private : StartPageContent

    /** Nothing pinned and no history yet: how to pin a Tab, and what a Profile is. */
    data object FirstRun : StartPageContent

    /**
     * The Profile's Pinned Tabs and its latest pages. An empty one's section is left out, and
     * they are never both empty.
     */
    data class Sections(val pinned: List<PinnedGroup>, val recent: List<Visit>) : StartPageContent
}

/**
 * Pinned Tabs that the Start Page shows together: a run of top-level bookmarks, with no [folder],
 * or the bookmarks of one Folder, under its name.
 */
data class PinnedGroup(val folder: Folder?, val bookmarks: List<Bookmark>)

/**
 * What decides the Start Page: whether a Tab shows it, and what is on it. The rules live here,
 * apart from the drawing, so they can be checked without a device.
 */
object StartPage {

    /** How many pages "Recent" lists at most. */
    const val MAX_RECENT = 6

    /** Tiles are at least this wide, so a site's name has room under its icon. */
    val TILE_MIN_WIDTH: Dp = 88.dp

    /** Between tiles, across and down. */
    val TILE_GAP: Dp = 8.dp

    /**
     * True when a Tab at [url] has no web page, so the Start Page takes the page's place: a new
     * Tab, a restored Tab without an address, or `about:blank`. The address field is empty then.
     */
    fun shows(url: String?): Boolean = url.isNullOrEmpty() || url == BLANK

    /**
     * What the Start Page shows for a Tab of a Profile whose top level of bookmarks is [pinned]
     * and whose history is [visits], newest first. A Private Tab shows neither.
     */
    fun content(private: Boolean, pinned: List<PinnedItem>, visits: List<Visit>): StartPageContent {
        if (private) return StartPageContent.Private
        val groups = pinnedGroups(pinned)
        val recent = recent(visits)
        return if (groups.isEmpty() && recent.isEmpty()) StartPageContent.FirstRun else StartPageContent.Sections(groups, recent)
    }

    /**
     * What the Start Page of a Tab of [profile] shows, from [pages]. Null, so only the Profile's
     * name shows, while [pages] haven't loaded or are still another Profile's, as for a moment
     * after a switch: the page never shows one Profile's pages under another's name, and never
     * flashes the first-run note before the Profile's pages arrive. A Private Tab shows none of
     * them, so it needn't wait.
     */
    fun content(profile: ProfileId, private: Boolean, pages: ProfilePages?): StartPageContent? = when {
        private -> StartPageContent.Private
        pages == null || pages.profile != profile -> null
        else -> content(private = false, pinned = pages.pinned, visits = pages.visits)
    }

    /**
     * [items] in the Tab list's order: each run of top-level bookmarks is one group, and each
     * Folder another, even a collapsed one. A Folder without bookmarks is left out.
     */
    fun pinnedGroups(items: List<PinnedItem>): List<PinnedGroup> {
        val groups = mutableListOf<PinnedGroup>()
        var run = mutableListOf<Bookmark>()
        fun endRun() {
            if (run.isNotEmpty()) groups += PinnedGroup(null, run)
            run = mutableListOf()
        }
        items.forEach { item ->
            when (item) {
                is PinnedItem.Page -> run += item.bookmark
                is PinnedItem.Group -> {
                    endRun()
                    if (item.folder.bookmarks.isNotEmpty()) groups += PinnedGroup(item.folder, item.folder.bookmarks)
                }
            }
        }
        endRun()
        return groups
    }

    /**
     * The latest [limit] distinct pages of [visits], newest first. Two addresses that differ only
     * after `#` are one page. Each page is listed at its newest visit, with the newest title it
     * has had, so a visit that ended before the page named itself doesn't leave it unnamed.
     */
    fun recent(visits: List<Visit>, limit: Int = MAX_RECENT): List<Visit> =
        visits.groupBy { it.url.substringBefore('#') }
            .values
            .take(limit)
            .map { same -> same.first().copy(title = same.firstOrNull { it.title.isNotBlank() }?.title.orEmpty()) }

    /** How many tiles fit across [width], at least 2, and at most 8, so a tile never grows too wide. */
    fun columns(width: Dp): Int =
        ((width + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP)).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    private const val BLANK = "about:blank"
    private const val MIN_COLUMNS = 2
    private const val MAX_COLUMNS = 8
}

/**
 * The Start Page, in the place of a Tab with no web page ([StartPage.shows]). It belongs to the
 * Tab's Profile and wears its look: [profile]'s avatar and name at the top, and tiles and cards
 * tinted with its color, as strongly as the Profile says. The page itself sits on the app's plain
 * surface, so it stands apart from the tinted Sidebar next to it.
 *
 * Below that, "Pinned" holds the Profile's bookmarks as tiles, each Folder's under its name, and
 * "Recent" its latest pages, with a way to the whole history. With neither, a short note says how
 * to pin a Tab and what a Profile is. A Private Tab shows none of the Profile's pages: it says
 * what a Private Tab keeps and what it discards. Until [pages] are [profile]'s, only the name
 * shows ([StartPage.content]).
 *
 * Nothing here is fetched: the site marks come from the icons the Profile already has
 * ([LocalSiteIcons]), or are letters. The content is one column, at most [START_PAGE_MAX_WIDTH]
 * wide and centered, [START_PAGE_GUTTER] from the edges, with text on the [START_PAGE_TEXT_INSET]
 * line inside it, like the panels. It scrolls. [contentInsets] and [contentPadding] keep it clear
 * of the system's bars and cutouts and of what the layout has at the bottom edge, while the
 * page's surface runs edge to edge under them.
 */
@Composable
fun StartPage(
    profile: Profile,
    private: Boolean,
    /** The Pinned Tabs and history to show, if they are [profile]'s. */
    pages: ProfilePages?,
    /** Opens a bookmark as its Pinned Tab, from this Tab. */
    onOpenPinned: (Bookmark) -> Unit,
    /** Loads an address in this Tab. */
    onOpenPage: (String) -> Unit,
    /** Shows the whole history. */
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** What the system takes at the edges: the content stays clear of it, and the color runs under it. */
    contentInsets: WindowInsets = WindowInsets(0),
) {
    val content = StartPage.content(profile.id, private, pages)
    // The page's own surface, so the page still stands apart from the Sidebar next to it, with
    // tiles and cards tinted with the Profile's color, as strongly as the Profile says.
    val cardColor = profileTint(profile)
    Surface(modifier.fillMaxSize().testTag("start-page")) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(contentInsets)
                .padding(contentPadding),
        ) {
            Column(
                Modifier
                    .widthIn(max = START_PAGE_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = START_PAGE_GUTTER)
                    .padding(top = START_PAGE_GUTTER),
            ) {
                ProfileHeader(profile)
                when (content) {
                    StartPageContent.Private -> PrivateNote(profile, cardColor)
                    StartPageContent.FirstRun -> FirstRunNote(profile, cardColor)
                    is StartPageContent.Sections -> {
                        if (content.pinned.isNotEmpty()) PinnedSection(content.pinned, cardColor, onOpenPinned)
                        if (content.recent.isNotEmpty()) RecentSection(content.recent, cardColor, onOpenPage, onShowHistory)
                    }
                    // The Profile's pages are still loading.
                    null -> {}
                }
            }
        }
    }
}

/** The Profile's avatar and name, on the text line. A screen reader reads it as one heading. */
@Composable
private fun ProfileHeader(profile: Profile) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = START_PAGE_TEXT_INSET)
            .semantics(mergeDescendants = true) { heading() }
            .testTag("start-page-profile"),
    ) {
        ProfileBadge(profile.color, profile.icon, HEADER_AVATAR_SIZE, profile.emoji)
        Text(
            profile.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = LIST_ICON_GAP),
        )
    }
}

/** A section's name on the text line, with an optional [action] at the end. */
@Composable
private fun StartSectionLabel(text: String, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = START_PAGE_GUTTER)
            .heightIn(min = 48.dp)
            .padding(end = LIST_ICON_INSET),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = START_PAGE_TEXT_INSET).semantics { heading() },
        )
        action()
    }
}

/** "Pinned": each group of bookmarks as tiles, a Folder's under its name. */
@Composable
private fun PinnedSection(groups: List<PinnedGroup>, cardColor: Color, onOpen: (Bookmark) -> Unit) {
    Column(Modifier.testTag("start-page-pinned")) {
        StartSectionLabel(stringResource(R.string.pinned))
        groups.forEachIndexed { index, group ->
            val folder = group.folder
            if (folder != null) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = START_PAGE_TEXT_INSET, end = START_PAGE_TEXT_INSET, top = if (index > 0) 16.dp else 0.dp, bottom = 8.dp)
                        .testTag("start-page-folder-${folder.id}"),
                )
            } else if (index > 0) {
                Spacer(Modifier.height(StartPage.TILE_GAP))
            }
            TileGrid(group.bookmarks, cardColor, onOpen)
        }
    }
}

/** Bookmarks as tiles, as many to a row as fit, [StartPage.TILE_GAP] apart. */
@Composable
private fun TileGrid(bookmarks: List<Bookmark>, cardColor: Color, onOpen: (Bookmark) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = StartPage.columns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(StartPage.TILE_GAP)) {
            bookmarks.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(StartPage.TILE_GAP)) {
                    row.forEach { bookmark -> PinnedTile(bookmark, cardColor, onClick = { onOpen(bookmark) }, modifier = Modifier.weight(1f)) }
                    // The last row keeps the others' widths.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A bookmark's tile: its site's mark, and its name under it in up to two lines. */
@Composable
private fun PinnedTile(bookmark: Bookmark, cardColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = cardColor,
        shape = CARD_SHAPE,
        modifier = modifier
            .clip(CARD_SHAPE)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("start-tile-${bookmark.id}"),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            SiteIcon(bookmark.url, size = TILE_ICON_SIZE)
            Text(
                bookmarkLabel(bookmark),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** "Recent": the latest pages, each with its site's mark, and "Show all" for the history. */
@Composable
private fun RecentSection(visits: List<Visit>, cardColor: Color, onOpen: (String) -> Unit, onShowHistory: () -> Unit) {
    Column(Modifier.testTag("start-page-recent")) {
        StartSectionLabel(stringResource(R.string.start_page_recent)) {
            TextButton(onClick = onShowHistory, modifier = Modifier.testTag("start-page-show-history")) {
                Text(stringResource(R.string.start_page_show_all))
            }
        }
        Surface(color = cardColor, shape = CARD_SHAPE) {
            Column(Modifier.padding(vertical = 4.dp)) {
                visits.forEach { visit -> RecentRow(visit, onClick = { onOpen(visit.url) }) }
            }
        }
    }
}

/** A recent page: its site's mark, its title, and its site under the title. */
@Composable
private fun RecentRow(visit: Visit, onClick: () -> Unit) {
    val host = displayHost(visit.url)
    val name = visit.title.ifBlank { host.ifEmpty { visit.url } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = START_PAGE_TEXT_INSET, vertical = 8.dp)
            .testTag("start-recent-${visit.id}"),
    ) {
        SiteIcon(visit.url)
        Column(Modifier.weight(1f).padding(start = LIST_ICON_GAP)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (host.isNotEmpty() && host != name) {
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

/**
 * A card of short paragraphs under a [title], with an optional mark before the title. A screen
 * reader reads the card as one item.
 */
@Composable
private fun NoteCard(title: String, paragraphs: List<String>, cardColor: Color, tag: String, mark: (@Composable () -> Unit)? = null) {
    Surface(
        color = cardColor,
        shape = CARD_SHAPE,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = START_PAGE_GUTTER)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
    ) {
        Column(Modifier.padding(START_PAGE_TEXT_INSET), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                mark?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** What a Private Tab keeps and what it discards (SPEC, "Private Tabs"), with the lock that marks it in the Tab list. */
@Composable
private fun PrivateNote(profile: Profile, cardColor: Color) {
    NoteCard(
        title = stringResource(R.string.private_tab),
        paragraphs = listOf(
            stringResource(R.string.start_page_private_store, profile.name),
            stringResource(R.string.start_page_private_traces),
            stringResource(R.string.start_page_private_end),
            stringResource(R.string.start_page_private_downloads),
        ),
        cardColor = cardColor,
        tag = "start-page-private",
        mark = {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).testTag("start-page-lock"),
            )
        },
    )
}

/** Before anything is pinned or visited: how to pin a Tab, and what a Profile is. */
@Composable
private fun FirstRunNote(profile: Profile, cardColor: Color) {
    NoteCard(
        title = stringResource(R.string.start_page_first_run_title),
        paragraphs = listOf(
            stringResource(R.string.start_page_first_run_pin),
            stringResource(R.string.start_page_first_run_profiles, profile.name),
        ),
        cardColor = cardColor,
        tag = "start-page-first-run",
    )
}

/** The Start Page's column is at most this wide, centered, like a panel's. */
private val START_PAGE_MAX_WIDTH = 720.dp

/** Between the column and the edges of the page's place, and above the first row. */
private val START_PAGE_GUTTER = 8.dp

/** Where text starts inside the column, and inside a card. */
private val START_PAGE_TEXT_INSET = 16.dp

/** The avatar beside the Profile's name, larger than the bars' 32dp one. */
private val HEADER_AVATAR_SIZE = 40.dp

/** A site's mark on its tile. */
private val TILE_ICON_SIZE = 32.dp

/** Tiles and cards: the same corners as the Tab list's rows. */
private val CARD_SHAPE = RoundedCornerShape(12.dp)
