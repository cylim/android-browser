package app.jrny.browser.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressInputTest {

    private fun address(input: String) = AddressInput.toAddress(input, searchTemplate = "https://search.test/?q=%s")

    private fun search(query: String) = "https://search.test/?q=$query"

    @Test
    fun `blank input loads nothing`() {
        assertNull(address(""))
        assertNull(address("   "))
    }

    @Test
    fun `web addresses keep their scheme`() {
        assertEquals("https://example.com/a?b=c#d", address("https://example.com/a?b=c#d"))
        assertEquals("http://example.com", address("http://example.com"))
        assertEquals("HTTPS://Example.com", address("HTTPS://Example.com"))
    }

    @Test
    fun `an explicit web address is never sent to the search engine`() {
        assertEquals("https://user:secret@example.com/", address("https://user:secret@example.com/"))
        // Intranet hosts: a single label, or an underscore.
        assertEquals("https://user:pw@intranet/", address("https://user:pw@intranet/"))
        assertEquals("http://nas/?token=x", address("http://nas/?token=x"))
        assertEquals("https://build_server.corp/", address("https://build_server.corp/"))
        assertEquals("https://example.com/a b", address("https://example.com/a b"))
    }

    @Test
    fun `an explicit web address without a host loads nothing instead of searching`() {
        assertNull(address("https://"))
        assertNull(address("https:///path"))
        assertNull(address("http://exa mple.com"))
        assertNull(address("https://example.com:abc/"))
    }

    @Test
    fun `addresses without a scheme get https`() {
        assertEquals("https://example.com", address("example.com"))
        assertEquals("https://www.example.co.uk/path?q=1", address("www.example.co.uk/path?q=1"))
        assertEquals("https://example.com:8443/x", address("example.com:8443/x"))
        assertEquals("https://xn--bcher-kva.example", address("xn--bcher-kva.example"))
        assertEquals("https://bücher.de", address("bücher.de"))
    }

    @Test
    fun `credentials typed without a scheme load, never search`() {
        assertEquals("https://user:pw@intranet", address("user:pw@intranet"))
        assertEquals("https://user:pw@example.com/x", address("user:pw@example.com/x"))
    }

    @Test
    fun `a bare email address is searched for, not loaded`() {
        assertEquals(search("me%40example.com"), address("me@example.com"))
    }

    @Test
    fun `scheme-like credentials load nothing and are never searched for`() {
        // Each could be a mail link or a user name with a password: never send it to the search engine.
        assertNull(address("mailto:me@example.com"))
        assertNull(address("mailto:secret@example.com"))
        assertNull(address("MAILTO:me@example.com"))
        assertNull(address("sms:a:b@example.com"))
    }

    @Test
    fun `other names before a colon and an at sign are credentials and load`() {
        assertEquals("https://ftp:me@example.com", address("ftp:me@example.com"))
        // Spaces after the host are part of the path, which loads.
        assertEquals("https://user:secret@example.com/a b", address("user:secret@example.com/a b"))
    }

    @Test
    fun `other schemes with credentials load nothing and are never searched for`() {
        assertNull(address("ftp://user:secret@example.com"))
        assertNull(address("FTP://user:secret@example.com/file"))
        assertNull(address("intent://user:pw@example.com#Intent;end"))
        // Without credentials they are still searched for.
        assertEquals(search("ftp%3A%2F%2Fexample.com"), address("ftp://example.com"))
    }

    @Test
    fun `credentials with a space before the path load nothing and are never searched for`() {
        assertNull(address("user:two words@example.com"))
        assertNull(address("user:two words@example.com/path"))
        assertNull(address("mailto:a b@example.com"))
    }

    @Test
    fun `other schemes without credentials are searched for`() {
        assertEquals(search("tel%3A%2B15551234"), address("tel:+15551234"))
        assertEquals(search("geo%3A1.5%2C2.5"), address("geo:1.5,2.5"))
    }

    @Test
    fun `single-label hosts load when a port or slash makes them an address`() {
        assertEquals("https://nas:8080", address("nas:8080"))
        assertEquals("https://nas/", address("nas/"))
        assertEquals("https://router/admin", address("router/admin"))
        assertEquals(search("news"), address("news"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("https://example.com", address("  example.com \n"))
    }

    @Test
    fun `localhost and IP addresses are addresses`() {
        assertEquals("https://localhost:8080", address("localhost:8080"))
        assertEquals("https://192.168.1.1/admin", address("192.168.1.1/admin"))
        assertEquals("https://[::1]:8080/", address("[::1]:8080/"))
    }

    @Test
    fun `text that is not an address is searched for`() {
        assertEquals(search("weather"), address("weather"))
        assertEquals(search("how+do+profiles+work"), address("how do profiles work"))
        assertEquals(search("example.com+login"), address("example.com login"))
        assertEquals(search("999.1.1.1"), address("999.1.1.1"))
        assertEquals(search("1.5"), address("1.5"))
        assertEquals(search("a%26b%3Dc"), address("a&b=c"))
    }

    @Test
    fun `schemes other than http and https are searched for, never loaded`() {
        assertEquals(search("javascript%3Aalert%281%29"), address("javascript:alert(1)"))
        assertEquals(search("file%3A%2F%2F%2Fsdcard%2Fx.html"), address("file:///sdcard/x.html"))
        assertEquals(search("intent%3A%2F%2Fexample.com"), address("intent://example.com"))
        assertEquals(search("ftp%3A%2F%2Fexample.com"), address("ftp://example.com"))
    }

    @Test
    fun `a malformed host or port without a scheme is searched for`() {
        assertEquals(search("example.com%3Aabc"), address("example.com:abc"))
        assertEquals(search("-example.com"), address("-example.com"))
    }

    @Test
    fun `the default search engine is Google`() {
        assertEquals("https://www.google.com/search?q=jrny", AddressInput.toAddress("jrny"))
    }
}
