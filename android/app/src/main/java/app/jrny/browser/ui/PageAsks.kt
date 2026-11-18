package app.jrny.browser.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.jrny.browser.R
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import app.jrny.browser.tab.FileChooserAsk
import app.jrny.browser.tab.FileTypes
import app.jrny.browser.tab.HttpAuthAsk
import app.jrny.browser.tab.PermissionAsk
import app.jrny.browser.tab.Tab
import kotlinx.coroutines.delay
import java.io.File

/**
 * Answers what [tab]'s page asks for: the camera, the microphone or location, files for a file
 * field, a sign-in, and fullscreen for a video. [profileName] is the Tab's Profile, and [private]
 * says the Tab is a Private Tab, which remembers nothing. [onRemember] keeps an answer in the
 * Profile's Site settings.
 */
@Composable
fun PageAsks(tab: Tab, profileName: String, private: Boolean, onRemember: (SiteOrigin, Set<SiteSetting>, Boolean) -> Unit) {
    PermissionAskHost(tab, profileName, private, onRemember)
    FileChooserHost(tab)
    tab.httpAuth?.let { ask ->
        HttpAuthDialog(
            ask,
            onSignIn = { username, password ->
                ask.signIn(username, password)
                tab.closeHttpAuth()
            },
            onCancel = {
                ask.cancel()
                tab.closeHttpAuth()
            },
        )
    }
}

/**
 * Asks the person about a site's ask, then Android for its own permission when the app doesn't
 * have it yet (SPEC, "Site permissions and Site settings"). Android is asked only once the person
 * allowed the site, never before. If Android refuses, the site is refused too, and a dialog says
 * where to allow it.
 */
@Composable
private fun PermissionAskHost(tab: Tab, profileName: String, private: Boolean, onRemember: (SiteOrigin, Set<SiteSetting>, Boolean) -> Unit) {
    val context = LocalContext.current
    // The ask waiting for Android's answer, with the Tab it came from: the person may show
    // another Tab while Android asks, and the answer still belongs to this one.
    var waiting by remember { mutableStateOf<Pair<Tab, PermissionAsk>?>(null) }
    // What Android refused, for the dialog that says so.
    var refused by remember { mutableStateOf<Set<SiteSetting>?>(null) }
    val androidAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val (owner, ask) = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (hasAndroidPermission(context, ask.wanted)) {
            ask.grant()
        } else {
            ask.deny()
            refused = ask.wanted
        }
        owner.closePermissionAsk()
    }
    val proceed: (PermissionAsk) -> Unit = { ask ->
        if (hasAndroidPermission(context, ask.wanted)) {
            ask.grant()
            tab.closePermissionAsk()
        } else {
            waiting = tab to ask
            androidAsk.launch(androidPermissions(ask.wanted).toTypedArray())
        }
    }
    val ask = tab.permissionAsk
    LaunchedEffect(ask) {
        if (ask != null && ask.decided) proceed(ask)
    }
    if (ask != null && !ask.decided && waiting?.second != ask) {
        PermissionDialog(
            ask,
            profileName,
            private,
            onAllowOnce = {
                tab.allowOnce(ask.origin, ask.wanted)
                proceed(ask)
            },
            onAlwaysAllow = {
                onRemember(ask.origin, ask.wanted, true)
                proceed(ask)
            },
            onBlock = {
                if (!private) onRemember(ask.origin, ask.wanted, false)
                ask.deny()
                tab.closePermissionAsk()
            },
            onDismiss = {
                ask.deny()
                tab.closePermissionAsk()
            },
        )
    }
    refused?.let { what ->
        AndroidRefusedDialog(what, onDismiss = { refused = null })
    }
}

/**
 * The site and what it asks for, with "Allow once", "Always allow" and "Block". A Private Tab
 * remembers nothing, so it offers "Allow" and "Block". Dismissing it refuses the site this time.
 */
@Composable
private fun PermissionDialog(
    ask: PermissionAsk,
    profileName: String,
    private: Boolean,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val site = ask.origin.host
    val title = when {
        SiteSetting.Location in ask.wanted -> R.string.permission_ask_location
        ask.wanted.containsAll(listOf(SiteSetting.Camera, SiteSetting.Microphone)) -> R.string.permission_ask_camera_microphone
        SiteSetting.Camera in ask.wanted -> R.string.permission_ask_camera
        else -> R.string.permission_ask_microphone
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("permission-dialog"),
        title = { Text(stringResource(title, site)) },
        text = {
            Text(if (private) stringResource(R.string.permission_ask_private) else stringResource(R.string.permission_ask_profile, profileName))
        },
        // Three answers don't fit side by side, so they stand one above the other, the safe one last.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (private) {
                    TextButton(onClick = onAllowOnce, modifier = Modifier.testTag("permission-allow-once")) { Text(stringResource(R.string.allow)) }
                } else {
                    TextButton(onClick = onAllowOnce, modifier = Modifier.testTag("permission-allow-once")) { Text(stringResource(R.string.allow_once)) }
                    TextButton(onClick = onAlwaysAllow, modifier = Modifier.testTag("permission-always")) { Text(stringResource(R.string.always_allow)) }
                }
                TextButton(onClick = onBlock, modifier = Modifier.testTag("permission-block")) { Text(stringResource(R.string.block)) }
            }
        },
    )
}

/** Android refused the app [what]. Says so, with a way to the app's page in Android's settings. */
@Composable
private fun AndroidRefusedDialog(what: Set<SiteSetting>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val title = when {
        SiteSetting.Location in what -> R.string.android_refused_location
        what.containsAll(listOf(SiteSetting.Camera, SiteSetting.Microphone)) -> R.string.android_refused_camera_microphone
        SiteSetting.Camera in what -> R.string.android_refused_camera
        else -> R.string.android_refused_microphone
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("android-refused-dialog"),
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(R.string.android_refused_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val appPage = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                try {
                    context.startActivity(appPage)
                } catch (_: ActivityNotFoundException) {
                }
            }) { Text(stringResource(R.string.open_android_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** The Android permissions a site's ask needs. Location asks for both: Android's dialog lets the person choose approximate. */
private fun androidPermissions(settings: Set<SiteSetting>): List<String> = settings.flatMap {
    when (it) {
        SiteSetting.Camera -> listOf(Manifest.permission.CAMERA)
        SiteSetting.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
        SiteSetting.Location -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        SiteSetting.DesktopSite -> emptyList()
    }
}

/** True when the app may pass [settings] on: approximate location is enough for location. */
private fun hasAndroidPermission(context: Context, settings: Set<SiteSetting>): Boolean {
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return settings.all {
        when (it) {
            SiteSetting.Camera -> has(Manifest.permission.CAMERA)
            SiteSetting.Microphone -> has(Manifest.permission.RECORD_AUDIO)
            SiteSetting.Location -> has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
            SiteSetting.DesktopSite -> true
        }
    }
}

/** What the camera app is asked to make for a file field. */
private enum class Capture { Photo, Video }

/**
 * Lets the person choose files for the page's file field (SPEC, "File upload"): Android's file
 * picker, and when the field takes images or video, the camera app too. Whatever happens, the
 * page gets an answer, or it could never ask again.
 */
@Composable
private fun FileChooserHost(tab: Tab) {
    val context = LocalContext.current
    val ask = tab.fileChooser
    // The ask the picker or the camera is open for, with the Tab it came from, so the choice
    // isn't offered again meanwhile, and the files go to that Tab even if another one is shown
    // by the time they come back.
    var started by remember { mutableStateOf<Pair<Tab, FileChooserAsk>?>(null) }
    var capture by remember { mutableStateOf<Uri?>(null) }
    val answer: (List<Uri>?) -> Unit = { files -> started?.first?.answerFileChooser(files) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        answer(if (result.resultCode == Activity.RESULT_OK) pickedFiles(result.data) else null)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val made = capture
        capture = null
        answer(if (result.resultCode == Activity.RESULT_OK && made != null) listOf(made) else null)
    }
    val startCamera: (Capture) -> Unit = { kind ->
        val file = captureFile(context, kind)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        capture = uri
        val intent = Intent(if (kind == Capture.Photo) MediaStore.ACTION_IMAGE_CAPTURE else MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            camera.launch(intent)
        } catch (_: ActivityNotFoundException) {
            capture = null
            answer(null)
        }
    }
    // The app holds the camera permission for sites, so Android lets the camera app take a photo
    // for it only once that permission is granted.
    var cameraWanted by remember { mutableStateOf<Capture?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = cameraWanted
        cameraWanted = null
        if (granted && kind != null) startCamera(kind) else answer(null)
    }
    val choose: (Capture?) -> Unit = choose@{ kind ->
        val current = ask ?: return@choose
        started = tab to current
        when {
            kind == null -> try {
                picker.launch(pickerIntent(current))
            } catch (_: ActivityNotFoundException) {
                answer(null)
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera(kind)
            else -> {
                cameraWanted = kind
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }
    if (ask == null || started?.second == ask) return
    val types = remember(ask) { fileTypes(ask) }
    if (!types.takesImages && !types.takesVideos) {
        LaunchedEffect(ask) { choose(null) }
        return
    }
    FileSourceDialog(
        multiple = ask.multiple,
        photo = types.takesImages,
        video = types.takesVideos,
        onChoose = choose,
        onDismiss = { tab.answerFileChooser(null) },
    )
}

/** Where the person's files come from: the file picker, or the camera app for a photo or a video. */
@Composable
private fun FileSourceDialog(multiple: Boolean, photo: Boolean, video: Boolean, onChoose: (Capture?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("file-source-dialog"),
        title = { Text(stringResource(R.string.file_source_title)) },
        text = {
            Column {
                SourceRow(JrnyIcons.AttachFile, stringResource(if (multiple) R.string.choose_files else R.string.choose_file), "file-source-files") { onChoose(null) }
                if (photo) SourceRow(JrnyIcons.PhotoCamera, stringResource(R.string.take_photo), "file-source-photo") { onChoose(Capture.Photo) }
                if (video) SourceRow(JrnyIcons.Videocam, stringResource(R.string.record_video), "file-source-video") { onChoose(Capture.Video) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun fileTypes(ask: FileChooserAsk) =
    FileTypes(ask.acceptTypes) { suffix -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix) }

/** Android's file picker, showing the kinds of file the page accepts. */
private fun pickerIntent(ask: FileChooserAsk): Intent {
    val types = fileTypes(ask).mimeTypes
    return Intent(Intent.ACTION_GET_CONTENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(types.singleOrNull() ?: "*/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, ask.multiple)
        .apply { if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray()) }
}

/** The files the picker returned: several in its clip, or one as its data. */
private fun pickedFiles(data: Intent?): List<Uri>? {
    data ?: return null
    val clip = data.clipData
    if (clip != null && clip.itemCount > 0) return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    return data.data?.let(::listOf)
}

/**
 * A new file in the app's cache for the camera app to write into. These files are removed the
 * next time the app starts ([clearCaptures]); the page may read its file any time before.
 */
private fun captureFile(context: Context, kind: Capture): File {
    val folder = File(context.cacheDir, CAPTURE_FOLDER).apply { mkdirs() }
    val name = if (kind == Capture.Photo) "photo-${System.currentTimeMillis()}.jpg" else "video-${System.currentTimeMillis()}.mp4"
    return File(folder, name)
}

/**
 * Removes the files made for pages and other apps before this start: photos and videos the camera
 * app took for file fields, and images handed to other apps by Copy image and Share image.
 */
fun clearPageFiles(context: Context) {
    File(context.cacheDir, CAPTURE_FOLDER).deleteRecursively()
    File(context.cacheDir, Sharing.SHARED_FOLDER).deleteRecursively()
}

private const val CAPTURE_FOLDER = "captures"

/**
 * Asks for the username and password a site wants before it sends the page (SPEC, "HTTP
 * sign-in"). The fields say what they are, so a password manager can fill them. What the site
 * says about itself (its realm) is shown in quotes, as the site's words, not the browser's.
 */
@Composable
private fun HttpAuthDialog(ask: HttpAuthAsk, onSignIn: (String, String) -> Unit, onCancel: () -> Unit) {
    var username by remember(ask) { mutableStateOf("") }
    var password by remember(ask) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("http-auth-dialog"),
        title = { Text(stringResource(R.string.http_auth_title, ask.host)) },
        text = {
            Column {
                if (ask.realm.isNotBlank()) {
                    Text(stringResource(R.string.http_auth_realm, ask.realm.take(MAX_REALM_LENGTH)), modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username }.testTag("http-auth-username"),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSignIn(username, password) }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.Password }.testTag("http-auth-password"),
                )
                Text(
                    stringResource(R.string.http_auth_not_kept),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSignIn(username, password) }, modifier = Modifier.testTag("http-auth-sign-in")) { Text(stringResource(R.string.sign_in)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A realm is the site's own text. A long one is cut, so it can't push the fields off the dialog. */
private const val MAX_REALM_LENGTH = 120

/**
 * The Tab's fullscreen video over the whole window (SPEC, "Fullscreen video"). The screen turns
 * to match the video: a wide one shows in landscape, a tall one in portrait, and one that can't
 * be measured in landscape. Back ends fullscreen, and the orientation goes back to normal.
 */
@Composable
fun FullscreenVideoHost(tab: Tab) {
    val video = tab.fullscreen ?: return
    val activity = LocalActivity.current
    BackHandler { tab.exitFullscreen() }
    LaunchedEffect(video) {
        // The page marks its fullscreen element a moment after WebView hands over the view, and
        // the video may still be loading its size.
        repeat(MEASURE_ATTEMPTS) { attempt ->
            delay(MEASURE_INTERVAL_MS)
            var size: Pair<Int, Int>? = null
            var measured = false
            tab.measureFullscreenVideo {
                size = it
                measured = true
            }
            while (!measured) delay(MEASURE_POLL_MS)
            val found = size
            if (found != null || attempt == MEASURE_ATTEMPTS - 1) {
                activity?.requestedOrientation = if (found != null && found.second > found.first) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                return@LaunchedEffect
            }
        }
    }
    DisposableEffect(video) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    AndroidView(
        factory = { video.view },
        modifier = Modifier.fillMaxSize().background(Color.Black).testTag("fullscreen-video"),
    )
}

private const val MEASURE_ATTEMPTS = 4
private const val MEASURE_INTERVAL_MS = 250L
private const val MEASURE_POLL_MS = 20L
