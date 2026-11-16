package app.jrny.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import app.jrny.browser.profile.FirstProfile

/** An avatar's 32dp circle sits centered in a 48dp slot, the size of an icon button. */
val AVATAR_SLOT = 48.dp

/**
 * The Active Profile's avatar in an icon-button-sized slot, so it lines up with the icon
 * buttons around it. Tapping it switches Profiles from Milestone 2.
 */
@Composable
fun ProfileAvatar(modifier: Modifier = Modifier) {
    Box(modifier.size(AVATAR_SLOT).testTag("profile-avatar"), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colorResource(FirstProfile.colorRes)),
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
