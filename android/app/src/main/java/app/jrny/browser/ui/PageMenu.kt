package app.jrny.browser.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * The page menu behind the "More" button, for the shown Tab (SPEC, "Page menu"): a row of icons
 * (Forward, Bookmark this page, Share, Reload), then what can be done with the page, with its
 * Profile, and in the browser. [showForward] puts Forward in the row, for a Bottom Bar too narrow
 * for its own button.
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
    onFind: () -> Unit = {},
    /** Desktop site on or off for the page's site, or null where it can't be set. */
    onDesktopSite: ((Boolean) -> Unit)? = null,
    /** Add to Home screen, or null where it isn't offered: a Private Tab, or a launcher without pinned shortcuts. */
    onAddToHome: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val private = tabs.selected.isPrivate
    // Move and Open in Profile need an address to carry and another Profile to carry it to. A
    // Private Tab stays private, so it offers neither.
    val canUseOtherProfile = tab.url != null && profiles.all.size > 1 && !private
    // Only a web page can be shared, copied, printed or bookmarked.
    val url = tab.url?.takeIf(WebAddress::isWebAddress)
    val linkLabel = stringResource(R.string.link)
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

            @Composable
            fun RowIcon(label: String, icon: ImageVector, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
                IconButton(
                    onClick = {
                        open = false
                        onClick()
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag(tag),
                ) { Icon(icon, label) }
            }

            @Composable
            fun Section(label: Int) {
                HorizontalDivider()
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
                )
            }

            // The icons' centers sit 24dp in, on the same line as the items' icons below.
            Row {
                if (showForward) {
                    RowIcon(stringResource(R.string.forward), Icons.AutoMirrored.Filled.ArrowForward, "menu-forward", enabled = tab.canGoForward, onClick = tab::goForward)
                }
                val bookmarked = library.isBookmarked(tab.url)
                RowIcon(
                    stringResource(if (bookmarked) R.string.remove_bookmark else R.string.bookmark_page),
                    if (bookmarked) Icons.Filled.Star else JrnyIcons.StarBorder,
                    "menu-bookmark",
                    enabled = url != null,
                ) { library.toggleBookmark(url!!, tab.pageTitle.orEmpty()) }
                RowIcon(stringResource(R.string.share), Icons.Filled.Share, "menu-share", enabled = url != null) {
                    Sharing.shareAddress(context, url!!, tab.pageTitle)
                }
                RowIcon(stringResource(R.string.reload), Icons.Filled.Refresh, "menu-reload", onClick = tab::reload)
            }

            Section(R.string.menu_page)
            Item(R.string.copy_link, JrnyIcons.ContentCopy, "menu-copy-link", enabled = url != null) {
                Sharing.copy(context, linkLabel, url!!, private)
            }
            Item(R.string.find_in_page, Icons.Filled.Search, "menu-find", enabled = url != null, onClick = onFind)
            // A switch: it shows whether the site gets its desktop version, and changes it.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.site_setting_desktop)) },
                leadingIcon = { Icon(JrnyIcons.Desktop, contentDescription = null) },
                trailingIcon = { Switch(checked = tab.desktopSite, onCheckedChange = null, modifier = Modifier.scale(0.8f)) },
                enabled = url != null && onDesktopSite != null,
                onClick = {
                    open = false
                    onDesktopSite?.invoke(!tab.desktopSite)
                },
                modifier = Modifier.testTag("menu-desktop-site"),
            )
            Item(R.string.print, JrnyIcons.Print, "menu-print", enabled = url != null) {
                Sharing.print(context, tab, tab.pageTitle ?: tab.displayHost)
            }
            if (onAddToHome != null) {
                Item(R.string.add_to_home_screen, Icons.Filled.Home, "menu-add-to-home", enabled = url != null, onClick = onAddToHome)
            }

            Section(R.string.menu_profile)
            Item(R.string.move_to_profile, Icons.AutoMirrored.Filled.ExitToApp, "menu-move", enabled = canUseOtherProfile) {
                onPick(ProfilePick.Move)
            }
            Item(R.string.open_in_profile, Icons.Filled.AccountCircle, "menu-open-in", enabled = canUseOtherProfile) {
                onPick(ProfilePick.OpenIn)
            }

            Section(R.string.menu_browser)
            Item(R.string.new_tab, Icons.Filled.Add, "menu-new-tab", onClick = tabs.open)
            Item(R.string.new_private_tab, Icons.Filled.Lock, "menu-new-private-tab", onClick = tabs.openPrivate)
            Item(R.string.bookmarks, Icons.Filled.Star, "menu-bookmarks") { onPanel(Panel.Bookmarks) }
            Item(R.string.history, JrnyIcons.History, "menu-history") { onPanel(Panel.History) }
            Item(R.string.downloads, JrnyIcons.Download, "menu-downloads") { onPanel(Panel.Downloads) }
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
 * Shown under a page that opened a link for another app (SPEC, "Links to other apps"): the app it
 * would open, when Android names it, and "Open". Nothing leaves the browser before "Open". When
 * no app can open it, it says so instead.
 */
@Composable
fun HandoffPrompt(appName: String?, canOpen: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = when {
            !canOpen -> stringResource(R.string.handoff_none)
            appName != null -> stringResource(R.string.handoff_offer_named, appName)
            else -> stringResource(R.string.handoff_offer)
        },
        action = if (canOpen) stringResource(R.string.open_app) else null,
        onAction = onOpen,
        onDismiss = onDismiss,
        tag = "handoff",
        modifier = modifier,
    )
}

/** Shown once a Tab closed, with "Undo" to open it again in its place (SPEC, "Reopen closed Tab"). */
@Composable
fun JustClosedPrompt(title: String, onUndo: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = if (title.isBlank()) stringResource(R.string.tab_closed_untitled) else stringResource(R.string.tab_closed, title),
        action = stringResource(R.string.undo),
        onAction = onUndo,
        onDismiss = onDismiss,
        tag = "tab-closed",
        modifier = modifier,
    )
}

/** Shown once "Open in new Tab" opened a link behind the shown Tab, with a way to it. */
@Composable
fun OpenedBehindPrompt(onShow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = stringResource(R.string.opened_in_new_tab),
        action = stringResource(R.string.show),
        onAction = onShow,
        onDismiss = onDismiss,
        tag = "opened-behind",
        modifier = modifier,
    )
}

/** Shown once a file the page made was saved to the Downloads folder, or couldn't be. */
@Composable
fun PageFileSavedPrompt(fileName: String, saved: Boolean, onShow: (() -> Unit)?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PagePrompt(
        text = stringResource(if (saved) R.string.page_file_saved else R.string.page_file_failed, fileName),
        action = if (saved && onShow != null) stringResource(R.string.show) else null,
        onAction = { onShow?.invoke() },
        onDismiss = onDismiss,
        tag = "page-file",
        modifier = modifier,
    )
}

/**
 * A short message floating over the page with one action and a close button. Text starts 16dp
 * in and takes a second line when it needs one; the close icon ends 16dp in.
 */
@Composable
private fun PagePrompt(text: String, action: String?, onAction: () -> Unit, onDismiss: () -> Unit, tag: String, modifier: Modifier = Modifier) {
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
            if (action != null) {
                TextButton(onClick = onAction, modifier = Modifier.padding(start = 8.dp).testTag("$tag-action")) {
                    Text(action, color = MaterialTheme.colorScheme.inversePrimary, maxLines = 1)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.dismiss))
            }
        }
    }
}

/**
 * Asks before a file is downloaded: its name, the site it comes from and its size. A page can't
 * start a download by itself. A file Okapi can't read only gets an explanation.
 */
@Composable
fun DownloadDialog(request: DownloadRequest, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // A file the page made comes from the page's site: a blob: address carries it, and a data:
    // one was offered by the page it was linked from.
    val site = WebAddress.hostOf(request.url)
        .ifEmpty { WebAddress.hostOf(request.url.removePrefix("blob:")) }
        .ifEmpty { WebAddress.hostOf(request.page) }
        .ifEmpty { request.url.substringBefore(':') }
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
