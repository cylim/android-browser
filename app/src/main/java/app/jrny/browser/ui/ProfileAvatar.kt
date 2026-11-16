package app.jrny.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jrny.browser.profile.Profile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon

/** An avatar's 32dp circle sits centered in a 48dp slot, the size of an icon button. */
val AVATAR_SLOT = 48.dp

val ProfileColor.color: Color get() = Color(argb)

val ProfileIcon.vector: ImageVector
    get() = when (this) {
        ProfileIcon.Person -> Icons.Filled.Person
        ProfileIcon.Home -> Icons.Filled.Home
        ProfileIcon.Build -> Icons.Filled.Build
        ProfileIcon.Email -> Icons.Filled.Email
        ProfileIcon.ShoppingCart -> Icons.Filled.ShoppingCart
        ProfileIcon.Star -> Icons.Filled.Star
        ProfileIcon.Favorite -> Icons.Filled.Favorite
        ProfileIcon.Face -> Icons.Filled.Face
    }

/**
 * A Profile's avatar, its icon in a circle of its color, centered in an icon-button-sized slot so
 * it lines up with the icon buttons around it. With [onClick] the whole slot is the touch target,
 * and [onClickLabel] says what a tap does.
 */
@Composable
fun ProfileAvatar(
    profile: Profile,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val slot = modifier
        .size(AVATAR_SLOT)
        .clip(CircleShape)
        .then(
            if (onClick != null) {
                // A tappable avatar stands alone, so it says whose it is. Inside a row that already
                // shows the name, it stays silent.
                Modifier
                    .semantics { contentDescription = profile.name }
                    .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )
        .testTag("profile-avatar")
    Box(slot, contentAlignment = Alignment.Center) {
        ProfileBadge(profile.color, profile.icon, 32.dp)
    }
}

/** A color circle with an icon on it, at [size]: avatars and the editor's preview. */
@Composable
fun ProfileBadge(color: ProfileColor, icon: ProfileIcon, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.color),
    ) {
        Icon(icon.vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.625f))
    }
}
