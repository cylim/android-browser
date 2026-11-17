package app.jrny.browser

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Settings the installed app declares in its manifest, as Android reads them. */
@RunWith(AndroidJUnit4::class)
class ManifestTest {

    @Test
    fun webViewMetricsAreOptedOut() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
        assertTrue(
            "android.webkit.WebView.MetricsOptOut must be true (ADR 0002)",
            info.metaData?.getBoolean("android.webkit.WebView.MetricsOptOut") == true,
        )
    }
}
