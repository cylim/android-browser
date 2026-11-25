package app.jrny.browser.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages
import app.jrny.browser.TestServer
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What a page asks for, in the browser window (SPEC, "Pages"): a site's ask for the camera, a
 * sign-in, a file field, fullscreen video and the page's own dialogs. Pages come from a server on 127.0.0.1, a secure
 * origin, so they may ask for the camera.
 */
@RunWith(AndroidJUnit4::class)
class PageAsksUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var tab: Tab
    private var server: TestServer? = null

    /** The sites the Profile asks for their desktop version. */
    private val desktopSites = mutableSetOf<SiteOrigin>()

    /** The answers the window asked the Profile to keep: site, settings, allowed. */
    private val remembered = mutableListOf<Triple<SiteOrigin, Set<SiteSetting>, Boolean>>()

    @Before
    fun createTab() {
        // Android's own prompt is a system dialog a test can't answer, so the app already has the camera.
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
        compose.runOnUiThread {
            tab = Tab(
                instrumentation.targetContext,
                WebProfile.of(ProfileId("test-page-asks")),
                // As BrowserState does for the Tab on screen with nothing remembered: ask the person.
                onPermissionAsk = { tab, ask -> tab.showPermissionAsk(ask) },
                desktopFor = { it in desktopSites },
            )
        }
    }

    @After
    fun destroy() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
        server?.close()
        otherServer?.close()
    }

    /** A second site, for a popup of another site than its page. */
    private var otherServer: TestServer? = null

    private fun serve(html: String, loopback: Boolean = true): String {
        val started = TestServer(mapOf("/page" to html), loopback)
        server = started
        return started.url("/page")
    }

    /** The test's window, for the orientation fullscreen asks for. */
    private var activity: Activity? = null

    private fun show(private: Boolean = false) = compose.setContent {
        activity = LocalActivity.current
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE_PORTRAIT)) {
            JrnyTheme {
                val shown = SavedTab(TabId(if (private) -1 else 1), TestProfiles.personal.id, tab.url, "", 0, selected = true, isPrivate = private)
                BrowserWindow(
                    tab,
                    TestTabs.ui(all = listOf(shown)),
                    TestProfiles.ui(),
                    siteSettings = SiteSettingsUi(rememberPermission = { origin, settings, allowed -> remembered += Triple(origin, settings, allowed) }),
                )
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun load(url: String) {
        compose.runOnUiThread { tab.load(url) }
    }

    private fun awaitTitle(title: String) = compose.waitUntil(15_000) { tab.title == title }

    /**
     * Taps the middle of the page, as a person would: a page may open a file field or go
     * fullscreen only on a tap. The touch goes to the page's view itself, so it lands whatever the
     * test window's size.
     */
    private fun tapPage() {
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = SessionViews.viewOf(tab.session)!!
            val x = view.width / 2f
            val y = view.height / 2f
            val down = SystemClock.uptimeMillis()
            for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 50)) {
                val event = MotionEvent.obtain(down, time, action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                view.dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }

    private val askForCamera =
        "<title>asking</title><script>navigator.mediaDevices.getUserMedia({video:true})" +
            ".then(function(s){s.getTracks().forEach(function(t){t.stop()});document.title='granted'}," +
            "function(e){document.title='denied '+e.name})</script>"

    @Test
    fun allowOnceGivesTheCameraAndKeepsNothing() {
        show()
        load(serve(askForCamera))
        compose.waitUntil(15_000) { exists("permission-dialog") }
        compose.onNodeWithText("camera", substring = true).assertExists()
        compose.onNodeWithTag("permission-allow-once").performClick()
        awaitTitle("granted")
        assertEquals(emptyList<Any>(), remembered)
        val origin = SiteOrigin.of(tab.url)!!
        assertTrue(compose.runOnUiThread { tab.isAllowedOnce(origin, SiteSetting.Camera) })
    }

    @Test
    fun alwaysAllowIsKeptInTheProfile() {
        show()
        load(serve(askForCamera))
        compose.waitUntil(15_000) { exists("permission-dialog") }
        compose.onNodeWithTag("permission-always").performClick()
        awaitTitle("granted")
        assertEquals(listOf(Triple(SiteOrigin.of(tab.url)!!, setOf(SiteSetting.Camera), true)), remembered)
    }

    @Test
    fun blockRefusesTheSiteAndIsKept() {
        show()
        load(serve(askForCamera))
        compose.waitUntil(15_000) { exists("permission-dialog") }
        compose.onNodeWithTag("permission-block").performClick()
        awaitTitle("denied NotAllowedError")
        assertEquals(listOf(Triple(SiteOrigin.of(tab.url)!!, setOf(SiteSetting.Camera), false)), remembered)
    }

    @Test
    fun aPrivateTabOffersNoAlwaysAndKeepsNothing() {
        show(private = true)
        load(serve(askForCamera))
        compose.waitUntil(15_000) { exists("permission-dialog") }
        compose.onNodeWithTag("permission-always").assertDoesNotExist()
        compose.onNodeWithTag("permission-block").performClick()
        awaitTitle("denied NotAllowedError")
        assertEquals(emptyList<Any>(), remembered)
    }

    @Test
    fun aSignInGoesToTheSiteAndIsntKept() {
        show()
        load(serve("auth:ana:s3cret:<title>secret page</title>", loopback = false))
        compose.waitUntil(15_000) { exists("http-auth-dialog") }
        compose.onNodeWithText("Test realm", substring = true).assertExists()
        compose.onNodeWithTag("http-auth-username").performTextInput("ana")
        compose.onNodeWithTag("http-auth-password").performTextInput("s3cret")
        compose.onNodeWithTag("http-auth-sign-in").performClick()
        awaitTitle("secret page")
        assertNull(compose.runOnUiThread { tab.httpAuth })
    }

    @Test
    fun cancellingTheSignInShowsTheSitesOwnRefusal() {
        show()
        load(serve("auth:ana:s3cret:<title>secret page</title>", loopback = false))
        compose.waitUntil(15_000) { exists("http-auth-dialog") }
        compose.onNodeWithText("Cancel").performClick()
        awaitTitle("unauthorized")
    }

    @Test
    fun aFileFieldForImagesOffersThePickerAndTheCameraButNoVideo() {
        show()
        load(serve("<title>upload</title><input id=f type=file accept='image/*' style='position:fixed;inset:0;width:100%;height:100%'>"))
        awaitTitle("upload")
        tapPage()
        compose.waitUntil(15_000) { exists("file-source-dialog") }
        compose.onNodeWithTag("file-source-files").assertExists()
        compose.onNodeWithTag("file-source-photo").assertExists()
        compose.onNodeWithTag("file-source-video").assertDoesNotExist()

        // Cancelling answers the page, so the field can be used again.
        compose.onNodeWithText("Cancel").performClick()
        assertNull(compose.runOnUiThread { tab.fileChooser })
        tapPage()
        compose.waitUntil(15_000) { exists("file-source-dialog") }
        compose.onNodeWithText("Cancel").performClick()
    }

    /** A page whose file field shows the name and the text of the file it gets, as its title. */
    private fun fileField(): String = serve(
        "<title>upload</title><input id=f type=file accept='image/*' style='position:fixed;inset:0;width:100%;height:100%'>" +
            "<script>f.onchange = async () => { const file = f.files[0]; document.title = file ? 'got ' + file.name + ': ' + await file.text() : 'none' };" +
            "f.oncancel = () => { document.title = 'none' }</script>",
    )

    /** A file in the app's own cache, as the camera app leaves a photo there. */
    private fun cacheFile(folder: String, name: String, text: String): File =
        File(File(instrumentation.targetContext.cacheDir, folder).apply { mkdirs() }, name).apply { writeText(text) }

    @Test
    fun aFileAnotherAppSharesReachesThePageWithItsName() {
        // The picker hands over a content: address, which GeckoView can't read by itself.
        val shared = cacheFile("captures", "note.txt", "hello upload")
        val address = FileProvider.getUriForFile(instrumentation.targetContext, "${instrumentation.targetContext.packageName}.files", shared)
        show()
        load(fileField())
        awaitTitle("upload")
        tapPage()
        compose.waitUntil(15_000) { exists("file-source-dialog") }
        compose.runOnUiThread { tab.answerFileChooser(listOf(address)) }
        awaitTitle("got note.txt: hello upload")
    }

    @Test
    fun aPhotoTheCameraTookReachesThePage() {
        val photo = cacheFile("captures", "photo-1.jpg", "a photo")
        show()
        load(fileField())
        awaitTitle("upload")
        tapPage()
        compose.waitUntil(15_000) { exists("file-source-dialog") }
        compose.runOnUiThread { tab.answerFileChooser(listOf(Uri.fromFile(photo))) }
        awaitTitle("got photo-1.jpg: a photo")
    }

    @Test
    fun aPickerAppCantHandThePageOkapisOwnFiles() {
        // A file of the app's own data, as a picker app might name it.
        val own = File(instrumentation.targetContext.filesDir, "secret.txt").apply { writeText("not for pages") }
        try {
            show()
            load(fileField())
            awaitTitle("upload")
            tapPage()
            compose.waitUntil(15_000) { exists("file-source-dialog") }
            compose.runOnUiThread { tab.answerFileChooser(listOf(Uri.fromFile(own))) }
            awaitTitle("none")
        } finally {
            own.delete()
        }
    }

    @Test
    fun aVideoGoesFullscreenTurnedToItsShapeAndBackEndsIt() {
        // A video drawn from a canvas, shaped like the screen is held now: tall on a phone in
        // portrait, wide on a tablet in landscape. Asking for the orientation the screen already
        // has changes nothing, so the test's window isn't rebuilt by a rotation.
        val landscape = instrumentation.targetContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val (width, height) = if (landscape) 200 to 100 else 100 to 200
        show()
        load(
            serve(
                // Named "video" once the video knows its size: a slow emulator can take longer
                // than Okapi waits to measure it, and then the screen rightly turns to landscape.
                "<title>loading</title><canvas id=c width=$width height=$height></canvas>" +
                    "<video id=v muted autoplay playsinline style='position:fixed;inset:0;width:100%;height:100%'></video>" +
                    "<script>var c=document.getElementById('c');(function draw(){c.getContext('2d').fillRect(0,0,$width,$height);requestAnimationFrame(draw)})();" +
                    "var v=document.getElementById('v');v.srcObject=c.captureStream();" +
                    "v.onloadedmetadata=v.onresize=function(){if(v.videoWidth>0)document.title='video'};" +
                    "v.onclick=function(){v.requestFullscreen()}</script>",
            ),
        )
        awaitTitle("video")
        // A page that has only just loaded may not take touches yet: a person would tap again.
        repeat(5) {
            if (!exists("fullscreen-video")) {
                tapPage()
                runCatching { compose.waitUntil(3_000) { exists("fullscreen-video") } }
            }
        }
        compose.waitUntil(15_000) { exists("fullscreen-video") }
        val expected = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        compose.waitUntil(15_000) { activity?.requestedOrientation == expected }

        // Back as the system sends it. Android's own "Viewing full screen" hint can hold the
        // window's focus, which a key event would wait for.
        compose.runOnUiThread { (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) { !exists("fullscreen-video") }
        assertNull(compose.runOnUiThread { tab.fullscreen })
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity?.requestedOrientation)
    }

    @Test
    fun anAlertNamesItsSiteAndOkLetsThePageGoOn() {
        show()
        load(serve("<title>before</title><script>onload=function(){setTimeout(function(){alert('Hello there');document.title='after'},0)}</script>"))
        compose.waitUntil(15_000) { exists("page-alert") }
        compose.onNodeWithText("127.0.0.1 says").assertExists()
        compose.onNodeWithText("Hello there").assertExists()
        compose.onNodeWithTag("page-prompt-ok").performClick()
        awaitTitle("after")
        assertNull(compose.runOnUiThread { tab.prompt })
    }

    @Test
    fun aQuestionsAnswerReachesThePage() {
        show()
        load(serve("<title>before</title><script>onload=function(){setTimeout(function(){document.title='answered '+confirm('Leave?')},0)}</script>"))
        compose.waitUntil(15_000) { exists("page-confirm") }
        compose.onNodeWithTag("page-prompt-cancel").performClick()
        awaitTitle("answered false")
    }

    @Test
    fun aPopupsDialogNamesThePopupsOwnSite() {
        val other = TestServer(mapOf("/popup" to "<title>popup</title><script>onload=function(){setTimeout(function(){alert('From the popup')},0)}</script>"), loopback = false)
        otherServer = other
        val popupHost = other.url("/popup").toUri().host!!
        show()
        load(serve("<title>opener</title><button style='position:fixed;inset:0;width:100%;height:100%' onclick=\"window.open('${other.url("/popup")}')\">open</button>"))
        awaitTitle("opener")
        tapPage()
        compose.waitUntil(15_000) { exists("page-alert") }
        // The popup's site, not the page's that opened it.
        compose.onNodeWithText("$popupHost says").assertExists()
        compose.onNodeWithText("127.0.0.1 says").assertDoesNotExist()
        compose.onNodeWithTag("page-prompt-ok").performClick()
    }

    @Test
    fun aPopupOfADesktopSiteGetsItsDesktopVersion() {
        val other = TestServer(mapOf("/agent" to "echo-agent"), loopback = false)
        otherServer = other
        desktopSites += SiteOrigin.of(other.url("/"))!!
        show()
        load(serve("<title>opener</title><button style='position:fixed;inset:0;width:100%;height:100%' onclick=\"window.open('${other.url("/agent")}')\">open</button>"))
        awaitTitle("opener")
        tapPage()
        compose.waitUntil(15_000) { tab.popup != null && tab.popupUrl == other.url("/agent") }
        // The popup's page names the agent it was asked with.
        val deadline = System.currentTimeMillis() + 15_000
        var agent = TestPages.runInPopup(tab, "document.title").orEmpty()
        while (!agent.startsWith("\"agent: ") && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            agent = TestPages.runInPopup(tab, "document.title").orEmpty()
        }
        assertTrue(agent, "X11; Linux x86_64" in agent)
        // The Tab's own page stays as its site wants it.
        assertTrue(!tab.desktopSite)
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
    }
}
