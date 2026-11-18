package app.jrny.browser

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Base64
import kotlin.concurrent.thread

/**
 * A tiny HTTP server on this device's own network address, for tests that need `http://` pages
 * without the network. It answers every path in [pages] with that HTML, and 404 otherwise.
 * Special pages:
 * - `redirect:/other` answers with a 302 redirect to `/other`;
 * - `set-cookie:name=value` sets that cookie, and its title is "cookie set";
 * - `echo-cookies` has the Cookie header it received as its title, "cookies: …";
 * - `echo-agent` has the User-Agent header it received as its title, "agent: …";
 * - `download:name:content` answers with `content` as a file to save under `name`;
 * - `auth:user:password:html` answers with `html` only to that HTTP sign-in, and asks for one
 *   (realm "Test realm") otherwise.
 *
 * With [loopback] the server listens on 127.0.0.1 instead, which pages treat as a secure origin:
 * only there may they ask for the camera or location.
 */
class TestServer(private val pages: Map<String, String>, loopback: Boolean = false) : AutoCloseable {

    /** Every request so far: its path and the `Cookie` header it carried (empty when none). */
    val requests: MutableList<Pair<String, String>> = java.util.concurrent.CopyOnWriteArrayList()

    /**
     * The device's own network address, not 127.0.0.1: Android lets cleartext to localhost
     * through even when it blocks it everywhere else, so a loopback test would prove nothing.
     */
    private val host: InetAddress = if (loopback) {
        InetAddress.getByName("127.0.0.1")
    } else {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .first { it is Inet4Address }
    }

    private val socket = ServerSocket(0, 16, host)

    /** The address of [path] on this server, for example `http://10.0.2.15:40949/popup`. */
    fun url(path: String): String = "http://${host.hostAddress}:${socket.localPort}$path"

    init {
        thread(isDaemon = true) {
            // WebView may open spare connections, so keep answering until the test closes us.
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                thread(isDaemon = true) {
                    // A client may hang up mid-request. Only that request ends: an exception
                    // escaping this thread would kill the test process.
                    try {
                        serve(connection)
                    } catch (_: IOException) {
                    }
                }
            }
        }
    }

    private fun serve(connection: Socket) {
        connection.use { client ->
            val reader = client.getInputStream().bufferedReader()
            val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
            var cookies = ""
            var authorization = ""
            var agent = ""
            while (true) {
                val header = reader.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Cookie:", ignoreCase = true)) cookies = header.substringAfter(':').trim()
                if (header.startsWith("Authorization:", ignoreCase = true)) authorization = header.substringAfter(':').trim()
                if (header.startsWith("User-Agent:", ignoreCase = true)) agent = header.substringAfter(':').trim()
            }
            requests += path to cookies
            val page = pages[path]
            val redirect = page?.removePrefix("redirect:")?.takeIf { page.startsWith("redirect:") }
            val setCookie = page?.removePrefix("set-cookie:")?.takeIf { page.startsWith("set-cookie:") }
            val download = page?.removePrefix("download:")?.takeIf { page.startsWith("download:") }
            val auth = page?.removePrefix("auth:")?.takeIf { page.startsWith("auth:") }?.split(':', limit = 3)
            val signedIn = auth != null &&
                authorization == "Basic " + Base64.getEncoder().encodeToString("${auth[0]}:${auth[1]}".toByteArray())
            val status = when {
                auth != null && !signedIn -> "401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Test realm\""
                redirect != null -> "302 Found\r\nLocation: $redirect"
                setCookie != null -> "200 OK\r\nSet-Cookie: $setCookie; Path=/; Max-Age=600"
                download != null -> "200 OK\r\nContent-Disposition: attachment; filename=\"${download.substringBefore(':')}\""
                page != null -> "200 OK"
                else -> "404 Not Found"
            }
            val body = when {
                redirect != null -> ""
                setCookie != null -> "<title>cookie set</title>"
                page == "echo-cookies" -> "<title>cookies: $cookies</title>"
                page == "echo-agent" -> "<title>agent: $agent</title>"
                auth != null -> if (signedIn) auth[2] else "<title>unauthorized</title>"
                download != null -> download.substringAfter(':')
                else -> page.orEmpty()
            }
            val bytes = body.toByteArray()
            client.getOutputStream().write(
                ("HTTP/1.1 $status\r\nContent-Type: ${if (download != null) "application/octet-stream" else "text/html; charset=utf-8"}\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes,
            )
        }
    }

    override fun close() = socket.close()
}
