package com.garasi.app.sync

import com.garasi.app.data.Item
import com.garasi.app.data.Photo
import com.garasi.app.data.PlatformListing
import com.garasi.app.data.Tombstone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Everything that syncs between phone and PC. On the wire, photo paths are bare file names and local
 * database ids are meaningless — records are matched by uid (items, photos) or itemUid/platform (listings).
 */
@Serializable
data class Snapshot(
    val items: List<Item> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val listings: List<PlatformListing> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
) {
    /** Copy with photo paths reduced to file names (for sending). */
    fun forWire(): Snapshot = copy(
        photos = photos.map { it.copy(path = File(it.path).name, originalPath = File(it.originalPath).name) },
    )

    /** Every photo file this snapshot refers to (file names). */
    fun fileNames(): Set<String> = photos.flatMap { listOf(File(it.path).name, File(it.originalPath).name) }
        .filter { it.isNotBlank() }.toSet()
}

object SyncMerge {
    /**
     * Merges two snapshots: union of records, newest updatedAt wins, deletions win over older edits.
     * Commutative and idempotent, so both devices end up identical whichever runs it.
     */
    fun merge(a: Snapshot, b: Snapshot): Snapshot {
        val tombs = (a.tombstones + b.tombstones).groupBy { it.uid }.mapValues { (_, v) -> v.maxBy { it.deletedAt } }

        fun deleted(uid: String, updatedAt: Long) = tombs[uid]?.let { it.deletedAt >= updatedAt } ?: false

        val items = (a.items + b.items).groupBy { it.uid }.values
            .map { v -> v.maxWith(compareBy<Item> { it.updatedAt }.thenBy { it.hashCode() }) }
            .filterNot { deleted(it.uid, it.updatedAt) }
        val liveItems = items.map { it.uid }.toSet()

        val photos = (a.photos + b.photos).groupBy { it.uid }.values
            .map { v -> v.maxWith(compareBy<Photo> { it.updatedAt }.thenBy { it.path }) }
            .filterNot { deleted(it.uid, it.updatedAt) }
            .filter { it.itemUid in liveItems }

        val listings = (a.listings + b.listings).groupBy { it.key }.values
            .map { v -> v.maxWith(compareBy<PlatformListing> { it.updatedAt }.thenBy { it.url }) }
            .filterNot { deleted(it.key, it.updatedAt) }
            .filter { it.itemUid in liveItems }

        return Snapshot(
            items = items.sortedBy { it.createdAt },
            photos = photos.sortedWith(compareBy({ it.itemUid }, { it.position })),
            listings = listings.sortedBy { it.key },
            tombstones = tombs.values.sortedBy { it.uid },
        )
    }
}

/** HTTP protocol between the phone (client) and the PC (server) on the local network. */
object SyncProtocol {
    const val DEFAULT_PORT = 8737
    const val TOKEN_HEADER = "X-Garasi-Token"
    const val PATH_PING = "/api/ping"           // GET  → "garasi"
    const val PATH_SNAPSHOT = "/api/snapshot"   // GET  → Snapshot JSON; POST Snapshot JSON → merged Snapshot JSON
    const val PATH_FILES = "/api/files"         // GET  → JSON list of file names the PC has
    const val PATH_FILE = "/api/file/"          // GET/PUT /api/file/<name> → raw bytes

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(s: Snapshot): String = json.encodeToString(Snapshot.serializer(), s)
    fun decode(text: String): Snapshot = json.decodeFromString(Snapshot.serializer(), text)

    /** Only plain photo file names may be transferred (no paths). */
    fun isSafeFileName(name: String) = name.matches(Regex("""[A-Za-z0-9._-]{1,120}""")) && !name.startsWith(".")

    /** Pairing link shown as a QR code on the PC: garasi://pair?h=ip1,ip2&p=8737&t=TOKEN */
    data class Pairing(val hosts: List<String>, val port: Int, val token: String) {
        fun toUri() = "garasi://pair?h=${hosts.joinToString(",")}&p=$port&t=$token"
    }

    fun parsePairing(text: String): Pairing? {
        val t = text.trim()
        if (!t.startsWith("garasi://pair?")) return null
        val q = t.substringAfter('?').split('&').mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val hosts = q["h"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val port = q["p"]?.toIntOrNull() ?: DEFAULT_PORT
        val token = q["t"].orEmpty()
        return if (hosts.isEmpty() || token.length < 6) null else Pairing(hosts, port, token)
    }
}
