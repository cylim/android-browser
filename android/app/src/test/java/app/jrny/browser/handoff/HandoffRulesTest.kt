package app.jrny.browser.handoff

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffRulesTest {

    @Test
    fun aTappedPhoneMailMessageOrMapLinkGoesAtOnce() {
        for (scheme in listOf("tel", "mailto", "sms", "smsto", "geo", "MAILTO")) {
            assertTrue(scheme, HandoffRules.goesAtOnce(scheme, tapped = true, private = false))
        }
    }

    @Test
    fun anythingElseWaitsForOpen() {
        assertFalse("not tapped", HandoffRules.goesAtOnce("tel", tapped = false, private = false))
        assertFalse("private", HandoffRules.goesAtOnce("tel", tapped = true, private = true))
        assertFalse("intent", HandoffRules.goesAtOnce("intent", tapped = true, private = false))
        assertFalse("market", HandoffRules.goesAtOnce("market", tapped = true, private = false))
        assertFalse("app scheme", HandoffRules.goesAtOnce("zoomus", tapped = true, private = false))
    }

    @Test
    fun webAndPageSchemesStayInThePage() {
        for (scheme in listOf("http", "https", "about", "data", "blob", "javascript", "file", "content")) {
            assertFalse(scheme, HandoffRules.isForAnotherApp(scheme))
        }
        for (scheme in listOf("tel", "intent", "market", "spotify")) {
            assertTrue(scheme, HandoffRules.isForAnotherApp(scheme))
        }
        assertFalse(HandoffRules.isForAnotherApp(null))
    }
}
