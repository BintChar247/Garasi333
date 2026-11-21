package com.garasi.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.garasi.app.sync.SyncProtocol
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.util.concurrent.Executors

/** Local-network sync server: the phone connects to this (see SyncProtocol). */
object SyncServer {
    var running by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var lastSync by mutableStateOf(0L); private set
    var lastDevice by mutableStateOf(""); private set
    private var server: HttpServer? = null

    fun start() {
        if (server != null) return
        try {
            val s = HttpServer.create(InetSocketAddress(Store.data.port), 0)
            s.executor = Executors.newFixedThreadPool(4)
            s.createContext("/api/") { ex -> runCatching { handle(ex) }.onFailure { runCatching { send(ex, 500, "error".toByteArray()) } } }
            s.start()
            server = s
            running = true
            error = null
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            running = false
        }
    }

    fun stop() { server?.stop(0); server = null; running = false }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.rawPath
        if (path == SyncProtocol.PATH_PING) { send(ex, 200, "garasi".toByteArray()); return }
        if (ex.requestHeaders.getFirst(SyncProtocol.TOKEN_HEADER) != Store.data.token) { send(ex, 401, "bad token".toByteArray()); return }
        when {
            path == SyncProtocol.PATH_SNAPSHOT && ex.requestMethod == "GET" ->
                sendJson(ex, SyncProtocol.encode(Store.snapshotForWire()))
            path == SyncProtocol.PATH_SNAPSHOT && ex.requestMethod == "POST" -> {
                val incoming = SyncProtocol.decode(ex.requestBody.readBytes().decodeToString())
                val merged = Store.mergeIncoming(incoming)
                val device = ex.remoteAddress.address.hostAddress
                javax.swing.SwingUtilities.invokeLater { lastSync = System.currentTimeMillis(); lastDevice = device }
                sendJson(ex, SyncProtocol.encode(merged))
            }
            path == SyncProtocol.PATH_FILES -> {
                val names = Store.photoDir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
                sendJson(ex, SyncProtocol.json.encodeToString(ListSerializer(String.serializer()), names))
            }
            path.startsWith(SyncProtocol.PATH_FILE) -> {
                val name = URLDecoder.decode(path.removePrefix(SyncProtocol.PATH_FILE), "UTF-8")
                if (!SyncProtocol.isSafeFileName(name)) { send(ex, 400, "bad name".toByteArray()); return }
                val f = File(Store.photoDir, name)
                when (ex.requestMethod) {
                    "GET" -> if (f.exists()) send(ex, 200, f.readBytes(), "image/jpeg") else send(ex, 404, ByteArray(0))
                    "PUT" -> {
                        val tmp = File(Store.photoDir, "$name.part")
                        ex.requestBody.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        f.delete(); tmp.renameTo(f)
                        send(ex, 200, "ok".toByteArray())
                    }
                    else -> send(ex, 405, ByteArray(0))
                }
            }
            else -> send(ex, 404, ByteArray(0))
        }
    }

    private fun sendJson(ex: HttpExchange, json: String) = send(ex, 200, json.toByteArray(), "application/json; charset=utf-8")

    private fun send(ex: HttpExchange, code: Int, body: ByteArray, type: String = "text/plain; charset=utf-8") {
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
    }

    /** This PC's local IPv4 addresses (Wi-Fi / Ethernet), most likely first. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { n -> val d = (n.displayName + n.name).lowercase(); if ("virtual" in d || "vmware" in d || "vbox" in d || "hyper-v" in d || "wsl" in d || "docker" in d) 1 else 0 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    fun pairing() = SyncProtocol.Pairing(localAddresses().ifEmpty { listOf("127.0.0.1") }, Store.data.port, Store.data.token)
}
