package app.jrny.browser.rules

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The changes to the default list of Link Wrappers are kept on the device and come back as they were left. */
@RunWith(AndroidJUnit4::class)
class LinkWrapperRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = "test-link-wrappers-${System.nanoTime()}"
    private val preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    private fun wrapper(pattern: String, parameters: String) = requireNotNull(LinkWrapper.parse(pattern, parameters))

    private val proofpoint = wrapper("urldefense.example", "u")
    private val slack = LinkWrappers.DEFAULT.single { it.pattern.text == "slack-redir.net/link" }
    private val googleQ = wrapper("google.com/url", "q")

    @After
    fun delete() {
        context.deleteSharedPreferences(file)
    }

    @Test
    fun theDefaultListIsUsedUntilThePersonChangesItAndNothingIsStoredForIt() {
        val repository = LinkWrapperRepository(preferences)
        assertEquals(LinkWrapperList.DEFAULT, repository.list.value)
        repository.update { it.save(null, proofpoint) }
        repository.update { it.useDefault() }
        assertEquals(LinkWrapperList.DEFAULT, LinkWrapperRepository(preferences).list.value)
        assertFalse(preferences.contains("link_wrappers"))
    }

    @Test
    fun theChangesAreKept() {
        val repository = LinkWrapperRepository(preferences)
        repository.update { it.save(null, proofpoint).delete(slack).save(LinkWrappers.DEFAULT.single { w -> w.pattern == googleQ.pattern }, googleQ) }
        val changed = repository.list.value
        // As after the app restarts: read again from the same file.
        assertEquals(changed, LinkWrapperRepository(preferences).list.value)
        assertEquals(listOf(proofpoint), changed.own - googleQ)
        assertEquals(setOf(slack.pattern), changed.removed)
    }

    @Test
    fun theDefaultsOfALaterVersionReachAChangedList() {
        val earlier = LinkWrappers.DEFAULT.drop(1)
        LinkWrapperRepository(preferences, defaults = earlier).update { it.save(null, proofpoint).delete(slack) }

        val later = LinkWrapperRepository(preferences, defaults = LinkWrappers.DEFAULT).list.value
        assertEquals(LinkWrappers.DEFAULT - slack + proofpoint, later.all)
    }

    @Test
    fun anEmptiedListStaysEmpty() {
        LinkWrapperRepository(preferences).update { list -> LinkWrappers.DEFAULT.fold(list) { it, wrapper -> it.delete(wrapper) } }
        assertEquals(emptyList<LinkWrapper>(), LinkWrapperRepository(preferences).list.value.all)
    }

    @Test
    fun whatThisVersionCannotReadIsLeftOut() {
        preferences.edit {
            putString(
                "link_wrappers",
                """{"own":[{"pattern":"urldefense.example","parameters":["u"]},{"pattern":"*.com","parameters":["u"]},""" +
                    """{"pattern":"x.example","parameters":["a b"]},{"other":1}],"removed":["slack-redir.net/link","*",7],"later":true}""",
            )
        }
        val read = LinkWrapperRepository(preferences).list.value
        assertEquals(listOf(proofpoint), read.own)
        assertEquals(setOf(slack.pattern), read.removed)
    }

    @Test
    fun aDamagedStoreFallsBackToTheDefaultList() {
        preferences.edit { putString("link_wrappers", "not json") }
        assertEquals(LinkWrapperList.DEFAULT, LinkWrapperRepository(preferences).list.value)
    }
}
