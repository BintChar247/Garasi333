package com.garasi.app.sync

import android.content.Context
import androidx.room.withTransaction
import com.garasi.app.data.GarasiDb
import com.garasi.app.data.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Phone side of PC sync: talks to the Garasi desktop app over the local network. */
class SyncManager(private val context: Context, private val db: GarasiDb) {
    sealed interface State {
        data object Idle : State
        data object Syncing : State
        data class Done(val at: Long, val items: Int) : State
        data class Failed(val reason: Reason) : State
    }
    enum class Reason { NOT_PAIRED, UNREACHABLE, BAD_CODE, ERROR }

    private val prefs = context.getSharedPreferences("garasi_sync", Context.MODE_PRIVATE)
    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val lock = Mutex()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pairing = MutableStateFlow(loadPairing())
    val pairing: StateFlow<SyncProtocol.Pairing?> = _pairing.asStateFlow()

    val lastSync: Long get() = prefs.getLong("last", 0)

    private fun loadPairing(): SyncProtocol.Pairing? {
        val hosts = prefs.getString("hosts", null)?.split(',')?.filter { it.isNotBlank() } ?: return null
        val token = prefs.getString("token", null) ?: return null
        return SyncProtocol.Pairing(hosts, prefs.getInt("port", SyncProtocol.DEFAULT_PORT), token)
    }

    fun pair(p: SyncProtocol.Pairing) {
        prefs.edit().putString("hosts", p.hosts.joinToString(",")).putInt("port", p.port).putString("token", p.token).apply()
        _pairing.value = p
    }

    fun unpair() {
        prefs.edit().clear().apply()
        _pairing.value = null
        _state.value = State.Idle
    }

    /** Full two-way sync. Never throws; the result is reported through [state]. */
    suspend fun sync(): State = lock.withLock {
        val p = _pairing.value ?: return State.Failed(Reason.NOT_PAIRED).also { _state.value = it }
        _state.value = State.Syncing
        val result = withContext(Dispatchers.IO) {
            try {
                val host = findHost(p) ?: return@withContext State.Failed(Reason.UNREACHABLE)
                val base = "http://$host:${p.port}"
                val client = Http(base, p.token)

                val local = exportSnapshot()
                val remote = SyncProtocol.decode(client.getText(SyncProtocol.PATH_SNAPSHOT))
                val merged = SyncMerge.merge(local.forWire(), remote)

                // Upload photo files the PC doesn't have yet.
                val pcFiles = SyncProtocol.json.decodeFromString(ListSerializer(String.serializer()), client.getText(SyncProtocol.PATH_FILES)).toSet()
                merged.fileNames().filter { it !in pcFiles }.forEach { name ->
                    val f = File(photoDir, name)
                    if (f.exists()) client.putFile(name, f)
                }
                // PC merges with anything that changed meanwhile and returns the final state.
                val final = SyncMerge.merge(merged, SyncProtocol.decode(client.postText(SyncProtocol.PATH_SNAPSHOT, SyncProtocol.encode(merged))))

                // Download photo files we don't have.
                final.fileNames().filter { !File(photoDir, it).exists() }.forEach { name ->
                    runCatching { client.getFile(name, File(photoDir, name)) }
                }
                applySnapshot(final)
                prefs.edit().putLong("last", System.currentTimeMillis()).putString("lastHost", host).apply()
                State.Done(System.currentTimeMillis(), final.items.size)
            } catch (e: AuthException) {
                State.Failed(Reason.BAD_CODE)
            } catch (e: IOException) {
                State.Failed(Reason.UNREACHABLE)
            } catch (e: Exception) {
                State.Failed(Reason.ERROR)
            }
        }
        _state.value = result
        result
    }

    private fun findHost(p: SyncProtocol.Pairing): String? {
        val last = prefs.getString("lastHost", null)
        val order = (listOfNotNull(last) + p.hosts).distinct()
        return order.firstOrNull { h ->
            runCatching { Http("http://$h:${p.port}", p.token, timeoutMs = 1500).getText(SyncProtocol.PATH_PING) == "garasi" }
                .getOrDefault(false)
        }
    }

    // ------------------------------------------------------------------ database <-> snapshot

    private suspend fun exportSnapshot(): Snapshot {
        val dao = db.dao()
        val items = dao.allItems()
        val uidById = items.associate { it.id to it.uid }
        return Snapshot(
            items = items,
            photos = dao.allPhotos().map { if (it.itemUid.isBlank()) it.copy(itemUid = uidById[it.itemId].orEmpty()) else it },
            listings = dao.allListings().map { if (it.itemUid.isBlank()) it.copy(itemUid = uidById[it.itemId].orEmpty()) else it },
            tombstones = dao.allTombstones(),
        )
    }

    private suspend fun applySnapshot(s: Snapshot) = db.withTransaction {
        val dao = db.dao()
        val localItems = dao.allItems().associateBy { it.uid }
        val keepItems = s.items.map { it.uid }.toSet()
        localItems.values.filter { it.uid !in keepItems }.forEach { dao.delete(it) }

        val idByUid = mutableMapOf<String, Long>()
        s.items.forEach { item ->
            val existing = localItems[item.uid]
            if (existing != null) {
                if (existing != item.copy(id = existing.id)) dao.update(item.copy(id = existing.id))
                idByUid[item.uid] = existing.id
            } else {
                idByUid[item.uid] = dao.insert(item.copy(id = 0))
            }
        }

        val localPhotos = dao.allPhotos().associateBy { it.uid }
        val keepPhotos = s.photos.map { it.uid }.toSet()
        localPhotos.values.filter { it.uid !in keepPhotos }.forEach { dao.deletePhotoByUid(it.uid) }
        s.photos.forEach { p ->
            val itemId = idByUid[p.itemUid] ?: return@forEach
            val local: Photo = p.copy(
                itemId = itemId,
                path = File(photoDir, File(p.path).name).path,
                originalPath = File(photoDir, File(p.originalPath).name).path,
            )
            val existing = localPhotos[p.uid]
            if (existing != null) {
                val withId = local.copy(id = existing.id)
                if (existing != withId) dao.updatePhotoRaw(withId)
            } else dao.insertPhoto(local.copy(id = 0))
        }

        val keepListings = s.listings.map { it.key }.toSet()
        val uidByIdNow = idByUid.entries.associate { it.value to it.key }
        dao.allListings().forEach { l ->
            val key = "${l.itemUid.ifBlank { uidByIdNow[l.itemId].orEmpty() }}/${l.platform.name}"
            if (key !in keepListings) dao.deleteListing(l.itemId, l.platform)
        }
        s.listings.forEach { l -> idByUid[l.itemUid]?.let { dao.upsertListingRaw(l.copy(itemId = it)) } }
        s.tombstones.forEach { dao.upsertTombstone(it) }
    }

    // ------------------------------------------------------------------ tiny HTTP client

    class AuthException : IOException("bad pairing code")

    private class Http(private val base: String, private val token: String, private val timeoutMs: Int = 15_000) {
        private fun open(path: String, method: String): HttpURLConnection =
            (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = if (timeoutMs < 5000) timeoutMs else 60_000
                setRequestProperty(SyncProtocol.TOKEN_HEADER, token)
            }

        private fun check(c: HttpURLConnection) {
            val code = c.responseCode
            if (code == 401 || code == 403) throw AuthException()
            if (code !in 200..299) throw IOException("HTTP $code")
        }

        fun getText(path: String): String {
            val c = open(path, "GET")
            try { check(c); return c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
        }

        fun postText(path: String, body: String): String {
            val c = open(path, "POST")
            try {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray()) }
                check(c)
                return c.inputStream.bufferedReader().readText()
            } finally { c.disconnect() }
        }

        fun putFile(name: String, file: File) {
            val c = open(SyncProtocol.PATH_FILE + URLEncoder.encode(name, "UTF-8"), "PUT")
            try {
                c.doOutput = true
                c.setFixedLengthStreamingMode(file.length())
                c.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
                check(c)
            } finally { c.disconnect() }
        }

        fun getFile(name: String, dest: File) {
            val c = open(SyncProtocol.PATH_FILE + URLEncoder.encode(name, "UTF-8"), "GET")
            try {
                check(c)
                val tmp = File(dest.path + ".part")
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(dest)
            } finally { c.disconnect() }
        }
    }
}
