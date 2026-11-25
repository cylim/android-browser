package app.jrny.browser

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.engine.Engine
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What the installed app and its engine are set up to send, or not, as they really run (ADR 0002). */
@RunWith(AndroidJUnit4::class)
class ManifestTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nothingIsBackedUp() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun googlesFidoLibraryIsLeftOut() {
        // GeckoView declares it for passkeys; the build excludes it.
        val found = runCatching { Class.forName("com.google.android.gms.fido.Fido") }.isSuccess
        assertFalse("play-services-fido is in the app", found)
    }

    @Test
    fun theEngineReportsNoCrashesAndLogsNoPages() {
        val settings = onMain { Engine.runtime(context).settings }
        assertEquals("a crash reporter is set up", null, settings.crashHandler)
        assertFalse("page console output reaches the log", settings.consoleOutputEnabled)
        assertTrue("remote debugging outside debug builds", !settings.remoteDebuggingEnabled || BuildConfig.DEBUG)
    }

    @Test
    @androidx.annotation.OptIn(markerClass = [ExperimentalGeckoViewApi::class])
    fun theEngineOffersPdfAttachmentsAsDownloads() {
        onMain { Engine.runtime(context) }
        for (name in Engine.PDF_ATTACHMENT_PREFS) {
            val pref = onMain { GeckoPreferenceController.getGeckoPref(name) }.poll(10_000)
            assertEquals(name, false, pref?.value)
        }
    }
}
