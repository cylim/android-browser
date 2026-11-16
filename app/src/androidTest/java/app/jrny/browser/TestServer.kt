package app.jrny.browser

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * A tiny HTTP server on this device's own network address, for tests that need `http://` pages
 * without the network. It answers every path in [pages] with that HTML, and 404 otherwise.
 * Special pages:
 * - `redirect:/other` answers with a 302 redirect to `/other`;
 * - `set-cookie:name=value` sets that cookie, and its title is "cookie set";
 * - `echo-cookies` has the Cookie header it received as its title, "cookies: …".
 */
class TestServer(private val pages: Map<String, String>) : AutoCloseable {

    /**
     * The device's own network address, not 127.0.0.1: Android lets cleartext to localhost
     * through even when it blocks it everywhere else, so a loopback test would prove nothing.
     */
    private val host: InetAddress = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .first { it is Inet4Address }

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
            while (true) {
                val header = reader.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Cookie:", ignoreCase = true)) cookies = header.substringAfter(':').trim()
            }
            val page = pages[path]
            val redirect = page?.removePrefix("redirect:")?.takeIf { page.startsWith("redirect:") }
            val setCookie = page?.removePrefix("set-cookie:")?.takeIf { page.startsWith("set-cookie:") }
            val status = when {
                redirect != null -> "302 Found\r\nLocation: $redirect"
                setCookie != null -> "200 OK\r\nSet-Cookie: $setCookie; Path=/; Max-Age=600"
                page != null -> "200 OK"
                else -> "404 Not Found"
            }
            val body = when {
                redirect != null -> ""
                setCookie != null -> "<title>cookie set</title>"
                page == "echo-cookies" -> "<title>cookies: $cookies</title>"
                else -> page.orEmpty()
            }
            val bytes = body.toByteArray()
            client.getOutputStream().write(
                ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes,
            )
        }
    }

    override fun close() = socket.close()
}
