package app.jrny.browser

import android.annotation.SuppressLint
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.tab.PrivateTabs
import app.jrny.browser.tab.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real activity: what survives recreation, and what reaches disk when the app goes to the background. */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireMultiProfile() {
    }

    /** The Active Profile's selected Tab, once the database has said which it is. */
    private fun tab(): Tab {
        var tab: Tab? = null
        compose.waitUntil(10_000) {
            compose.activityRule.scenario.onActivity { activity ->
                val browser = activity.browser!!
                tab = browser.active.value?.selected?.let(browser::tabFor)
            }
            tab != null
        }
        return tab!!
    }

    @Test
    fun theAddressComesBackAfterRecreationWithoutOpeningTheKeyboard() {
        val one = titled("one")
        val before = tab()
        compose.runOnUiThread { before.load(one) }
        await("loaded") { !before.isLoading && before.url == one && before.title == "one navigate" }

        compose.activityRule.scenario.recreate()

        val after = tab()
        assertTrue("a new Tab after recreation", after !== before)
        assertEquals(one, after.url)
        await("reloaded") { !after.isLoading && after.title == "one navigate" }
        // A restored Tab has an address, so the field doesn't grab focus and open the keyboard.
        compose.onNodeWithTag("address").assertIsNotFocused()
    }

    @Test
    fun privateTabsOutliveARebuiltWindowAndEndWhenTheWindowIsClosed() {
        tab()
        var profile: ProfileId? = null
        var privateTabs: PrivateTabs? = null
        compose.activityRule.scenario.onActivity { activity ->
            val browser = activity.browser!!
            profile = browser.active.value!!.profile.id
            privateTabs = (activity.application as JrnyApplication).privateTabs
            browser.openPrivateTab(browser.active.value!!.profile)
        }
        assertEquals(1, privateTabs!!.current(profile!!)!!.tabs.size)

        // Android rebuilds the window: the Private Tab is still there.
        compose.activityRule.scenario.recreate()
        assertEquals(1, privateTabs!!.current(profile!!)!!.tabs.size)

        // The person closes the window: it ends, though this process lives on.
        compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        assertEquals(null, privateTabs!!.current(profile!!))
    }
}
