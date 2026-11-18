package app.jrny.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jrny.browser.R
import app.jrny.browser.address.WebAddress
import app.jrny.browser.download.FetchedImage
import app.jrny.browser.profile.Profile
import app.jrny.browser.tab.PressedItem
import app.jrny.browser.tab.SavedTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the link and image menu and the page menu can do with a link or an image. */
class LinksUi(
    /** Opens the address as a new Tab behind the shown one, in its Profile (a Private Tab from a Private Tab). */
    val openInNewTab: (String) -> Unit = {},
    /** Opens the address in a new Private Tab and shows it. */
    val openInPrivateTab: (String) -> Unit = {},
    /** Opens the address as a new Tab in that Profile, which becomes the Active Profile. */
    val openInProfile: (Profile, String) -> Unit = { _, _ -> },
    /** The image at the address, with the cookies of the shown Tab's store, or null. */
    val fetchImage: suspend (String) -> FetchedImage? = { null },
    /** The Tab "Open in new Tab" just opened, to offer showing it, or null. */
    val openedBehind: SavedTab? = null,
    val dismissOpenedBehind: () -> Unit = {},
    /** The name of a file the page made and whether it was saved, once Okapi tried, or null. */
    val pageFileSaved: Pair<String, Boolean>? = null,
    val dismissPageFileSaved: () -> Unit = {},
    /** Desktop site on or off for the shown page's site, or null where it can't be set. */
    val setDesktopSite: ((Boolean) -> Unit)? = null,
    /** Pins a shortcut to the shown page, in its Profile, or null where there can be none (a Private Tab). */
    val addToHomeScreen: (() -> Unit)? = null,
    /** The Tab that just closed, while "Undo" is offered, or null. */
    val justClosed: app.jrny.browser.tab.ClosedTab? = null,
    val undoClose: () -> Unit = {},
    val dismissJustClosed: () -> Unit = {},
)

/**
 * The menu for a link or an image the person holds on the page (SPEC, "Link and image menu"). It
 * names what was held, then offers what can be done with it. A Private Tab ([private]) opens
 * links only in Private Tabs and never in another Profile. [canUseOtherProfile] is false with one
 * Profile.
 */
@Composable
fun LinkMenu(
    item: PressedItem,
    private: Boolean,
    canUseOtherProfile: Boolean,
    links: LinksUi,
    onSaveImage: (String) -> Unit,
    onPickProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Where fetching an image for Copy or Share runs: it outlives the menu, which closes at once. */
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val link = item.link
    val image = item.image
    val webLink = link?.takeIf(WebAddress::isWebAddress)
    val label = stringResource(R.string.link)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("link-menu"),
        title = {
            Text(
                link ?: image?.takeUnless { it.startsWith("data:") } ?: stringResource(R.string.image),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("link-menu-address"),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                fun act(action: () -> Unit): () -> Unit = {
                    onDismiss()
                    action()
                }
                if (webLink != null) {
                    MenuRow(Icons.Filled.Add, R.string.open_in_new_tab, "link-new-tab", act { links.openInNewTab(webLink) })
                    if (!private) {
                        MenuRow(Icons.Filled.Lock, R.string.open_in_private_tab, "link-private-tab", act { links.openInPrivateTab(webLink) })
                        if (canUseOtherProfile) MenuRow(Icons.Filled.AccountCircle, R.string.open_in_profile, "link-open-in", act { onPickProfile(webLink) })
                    }
                }
                if (link != null) {
                    MenuRow(JrnyIcons.ContentCopy, R.string.copy_link, "link-copy", act { Sharing.copy(context, label, link, private) })
                    MenuRow(Icons.Filled.Share, R.string.share_link, "link-share", act { Sharing.shareAddress(context, link, null) })
                }
                if (image != null) {
                    if (link != null) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    if (WebAddress.isWebAddress(image)) {
                        MenuRow(JrnyIcons.OpenInNew, R.string.open_image_in_new_tab, "image-new-tab", act { links.openInNewTab(image) })
                    }
                    MenuRow(JrnyIcons.Download, R.string.save_image, "image-save", act { onSaveImage(image) })
                    MenuRow(JrnyIcons.ContentCopy, R.string.copy_image, "image-copy", act {
                        scope.launch { links.fetchImage(image)?.let { Sharing.copyImage(context, it, private) } }
                    })
                    MenuRow(Icons.Filled.Share, R.string.share_image, "image-share", act {
                        scope.launch { links.fetchImage(image)?.let { Sharing.shareImage(context, it) } }
                    })
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
