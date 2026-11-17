package app.jrny.browser.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.address.WebAddress
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.Tab

/** Which action the Profile picker is choosing a Profile for (SPEC, "Tabs"). */
enum class ProfilePick { Move, OpenIn }

/**
 * The page menu behind the "More" button, for the shown Tab: a new Tab, bookmarking the page, the
 * Active Profile's bookmarks and history, Move, Open in Profile, and making JRNY browser the
 * default browser while it isn't. [showForward] adds Forward, for the narrow Bottom Bar, which has
 * no room for its button.
 */
@Composable
fun PageMenu(
    tab: Tab,
    tabs: TabsUi,
    profiles: ProfilesUi,
    library: LibraryUi,
    showForward: Boolean,
    onPick: (ProfilePick) -> Unit,
    onPanel: (Panel) -> Unit,
    onSetDefaultBrowser: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    // Move and Open in Profile need an address to carry and another Profile to carry it to. A
    // Private Tab stays private, so it offers neither.
    val canUseOtherProfile = tab.url != null && profiles.all.size > 1 && !tabs.selected.isPrivate
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("page-menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(12.dp)) {
            @Composable
            fun Item(label: Int, icon: ImageVector, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    enabled = enabled,
                    onClick = {
                        open = false
                        onClick()
                    },
                    modifier = Modifier.testTag(tag),
                )
            }
            if (showForward) {
                Item(R.string.forward, Icons.AutoMirrored.Filled.ArrowForward, "menu-forward", enabled = tab.canGoForward, onClick = tab::goForward)
            }
            Item(R.string.new_tab, Icons.Filled.Add, "menu-new-tab", onClick = tabs.open)
            Item(R.string.new_private_tab, Icons.Filled.Lock, "menu-new-private-tab", onClick = tabs.openPrivate)
            HorizontalDivider()
            // Only web pages can be bookmarked.
            val url = tab.url
            val bookmarked = library.isBookmarked(url)
            Item(
                if (bookmarked) R.string.remove_bookmark else R.string.bookmark_page,
                if (bookmarked) Icons.Filled.Star else JrnyIcons.StarBorder,
                "menu-bookmark",
                enabled = WebAddress.isWebAddress(url),
            ) { library.toggleBookmark(url!!, tab.pageTitle.orEmpty()) }
            Item(R.string.bookmarks, Icons.Filled.Star, "menu-bookmarks") { onPanel(Panel.Bookmarks) }
            Item(R.string.history, JrnyIcons.History, "menu-history") { onPanel(Panel.History) }
            Item(R.string.downloads, JrnyIcons.Download, "menu-downloads") { onPanel(Panel.Downloads) }
            HorizontalDivider()
            Item(R.string.move_to_profile, Icons.AutoMirrored.Filled.ExitToApp, "menu-move", enabled = canUseOtherProfile) {
                onPick(ProfilePick.Move)
            }
            Item(R.string.open_in_profile, Icons.Filled.AccountCircle, "menu-open-in", enabled = canUseOtherProfile) {
                onPick(ProfilePick.OpenIn)
            }
            HorizontalDivider()
            Item(R.string.settings, Icons.Filled.Settings, "menu-settings") { onPanel(Panel.Settings) }
            if (onSetDefaultBrowser != null) {
                Item(R.string.set_default_browser, Icons.Filled.CheckCircle, "menu-default-browser", onClick = onSetDefaultBrowser)
            }
        }
    }
}

/**
 * Chooses the Profile to Move the Tab to, or to open its page in. For a Move it says first what
 * is lost (ADR 0003), so choosing a Profile is the confirmation. Rows are 56dp with the avatar in
 * a 48dp slot, like the Profile list's.
 */
@Composable
fun ProfilePickerDialog(pick: ProfilePick, others: List<Profile>, onChoose: (Profile) -> Unit, onDismiss: () -> Unit) {
    val move = pick == ProfilePick.Move
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("profile-picker"),
        title = { Text(stringResource(if (move) R.string.move_to_profile_title else R.string.open_in_profile_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(if (move) R.string.move_to_profile_body else R.string.open_in_profile_body))
                // Rows reach 8dp past the text on both sides, so each avatar's 32dp circle, centered
                // in its 48dp slot, starts on the same line as the title and the text above.
                Column(Modifier.padding(top = 16.dp).bleed(AVATAR_INSET)) {
                    others.forEach { profile ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { onChoose(profile) }
                                .testTag("pick-profile-${profile.id.value}"),
                        ) {
                            ProfileAvatar(profile)
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 4.dp, end = 16.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** How far an avatar's 32dp circle sits inside its 48dp slot. */
internal val AVATAR_INSET = 8.dp

/** Lets the content be [by] wider than its place on each side. */
internal fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + 2 * extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/**
 * Shown over a page an Incoming Link just opened: which Profile it opened in, whether a Site Rule
 * sent it there ([byRule]), and a one-tap "Move to Profile…" (SPEC, "Incoming Links").
 */
@Composable
fun IncomingLinkPrompt(profileName: String, onMove: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, byRule: Boolean = false) {
    PagePrompt(
        text = stringResource(if (byRule) R.string.incoming_link_opened_by_rule else R.string.incoming_link_opened_in, profileName),
        action = stringResource(R.string.move_to_profile),
        onAction = onMove,
        onDismiss = onDismiss,
        tag = "incoming-link",
        modifier = modifier,
    )
}

/**
 * Shown once an Incoming Link was moved to another Profile: offers the Site Rule that sends the
 * links for [site] there from now on (SPEC, "Site Rules").
 */
@Composable
fun SiteRuleOfferPrompt(site: String, profileName: String, onAlways: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = stringResource(R.string.always_open_offer, site, profileName),
        action = stringResource(R.string.always),
        onAction = onAlways,
        onDismiss = onDismiss,
        tag = "site-rule-offer",
        modifier = modifier,
    )
}

/** Shown once that rule is made: what it does now, and the way to the Site Rules. */
@Composable
fun SiteRuleSavedPrompt(site: String, profileName: String, onShow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = stringResource(R.string.rule_saved_prompt, site, profileName),
        action = stringResource(R.string.site_rules),
        onAction = onShow,
        onDismiss = onDismiss,
        tag = "site-rule-saved",
        modifier = modifier,
    )
}

/** Shown over the page once a download has started: the file's name, and a way to the Downloads list. */
@Composable
fun DownloadStartedPrompt(fileName: String, onShow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = stringResource(R.string.downloading_file, fileName),
        action = stringResource(R.string.show),
        onAction = onShow,
        onDismiss = onDismiss,
        tag = "download-started",
        modifier = modifier,
    )
}

/**
 * A short message floating over the page with one action and a close button. Text starts 16dp
 * in and takes a second line when it needs one; the close icon ends 16dp in.
 */
@Composable
private fun PagePrompt(text: String, action: String, onAction: () -> Unit, onDismiss: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 480.dp).testTag("$tag-prompt"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            )
            TextButton(onClick = onAction, modifier = Modifier.padding(start = 8.dp).testTag("$tag-action")) {
                Text(action, color = MaterialTheme.colorScheme.inversePrimary, maxLines = 1)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.dismiss))
            }
        }
    }
}

/**
 * Asks before a file is downloaded: its name, the site it comes from and its size. A page can't
 * start a download by itself. A file the download service can't fetch only gets an explanation.
 */
@Composable
fun DownloadDialog(request: DownloadRequest, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val site = WebAddress.hostOf(request.url).ifEmpty { request.url.substringBefore(':') }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("download-dialog"),
        title = { Text(stringResource(R.string.download_title, request.fileName)) },
        text = {
            Column {
                Text(
                    if (request.size > 0) {
                        stringResource(R.string.download_from_with_size, site, Formatter.formatShortFileSize(context, request.size))
                    } else {
                        stringResource(R.string.download_from, site)
                    },
                )
                Text(
                    stringResource(if (request.supported) R.string.download_destination else R.string.download_unsupported),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            if (request.supported) {
                TextButton(onClick = onDownload, modifier = Modifier.testTag("confirm-download")) { Text(stringResource(R.string.download)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (request.supported) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
