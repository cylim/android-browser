package app.jrny.browser

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * A tiny HTTP server on this device's own network address, for tests that need `http://` pages
 * without the network. It answers every path in [pages] with that HTML, and 404 otherwise. A
 * page of `redirect:/other` answers with a 302 redirect to `/other`.
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
                    connection.use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        while (reader.readLine()?.isNotEmpty() == true) Unit
                        val page = pages[path]
                        val redirect = page?.removePrefix("redirect:")?.takeIf { page.startsWith("redirect:") }
                        val status = when {
                            redirect != null -> "302 Found\r\nLocation: $redirect"
                            page != null -> "200 OK"
                            else -> "404 Not Found"
                        }
                        val bytes = (page.takeIf { redirect == null } ?: "").toByteArray()
                        client.getOutputStream().write(
                            ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes,
                        )
                    }
                }
            }
        }
    }

    override fun close() = socket.close()
}
