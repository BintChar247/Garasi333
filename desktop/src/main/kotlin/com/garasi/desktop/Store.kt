package com.garasi.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.garasi.app.data.AppSettings
import com.garasi.app.data.Item
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.Photo
import com.garasi.app.data.Platform
import com.garasi.app.data.PlatformListing
import com.garasi.app.data.Tombstone
import com.garasi.app.sync.Snapshot
import com.garasi.app.sync.SyncMerge
import com.garasi.app.sync.SyncProtocol
import kotlinx.serialization.Serializable
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

@Serializable
data class DesktopData(
    val snapshot: Snapshot = Snapshot(),
    val settings: AppSettings = AppSettings(),
    val token: String = "",
    val port: Int = SyncProtocol.DEFAULT_PORT,
)

/**
 * All data of the desktop app: a JSON file + a photos folder in %APPDATA%\Garasi (or ~/.garasi).
 * State is Compose state, so the UI updates when the phone syncs.
 */
object Store {
    val dir: File = run {
        val appData = System.getenv("APPDATA")
        (if (appData != null) File(appData, "Garasi") else File(System.getProperty("user.home"), ".garasi")).apply { mkdirs() }
    }
    val photoDir = File(dir, "photos").apply { mkdirs() }
    private val file = File(dir, "data.json")
    private val saver = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "garasi-save").apply { isDaemon = true } }
    private var pendingSave: ScheduledFuture<*>? = null

    var data by mutableStateOf(load())
        private set

    val snapshot get() = data.snapshot
    val settings get() = data.settings

    private fun load(): DesktopData {
        val d = runCatching { SyncProtocol.json.decodeFromString(DesktopData.serializer(), file.readText()) }.getOrNull() ?: DesktopData()
        return if (d.token.isBlank()) d.copy(token = newToken()) else d
    }

    fun newToken(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..8).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
    }

    /** All mutations go through here: run on the UI thread (which serialises them), then saved shortly after. */
    fun mutate(transform: (DesktopData) -> DesktopData) {
        val apply = {
            data = transform(data)
            scheduleSave()
        }
        if (SwingUtilities.isEventDispatchThread()) apply() else SwingUtilities.invokeAndWait(apply)
    }

    private fun scheduleSave() {
        pendingSave?.cancel(false)
        pendingSave = saver.schedule({ saveNow() }, 400, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun saveNow() {
        val tmp = File(dir, "data.json.tmp")
        tmp.writeText(SyncProtocol.json.encodeToString(DesktopData.serializer(), data))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun snap(f: (Snapshot) -> Snapshot) = mutate { it.copy(snapshot = f(it.snapshot)) }
    private fun now() = System.currentTimeMillis()

    // ------------------------------------------------------------------ items

    fun item(uid: String) = snapshot.items.firstOrNull { it.uid == uid }
    fun photos(itemUid: String) = snapshot.photos.filter { it.itemUid == itemUid }.sortedBy { it.position }
    fun listings(itemUid: String) = snapshot.listings.filter { it.itemUid == itemUid }

    fun newItem(): Item {
        val s = settings
        val item = Item(listingLang = s.defaultLang, location = s.city)
        snap { it.copy(items = it.items + item) }
        return item
    }

    fun updateItem(uid: String, transform: (Item) -> Item) = snap { s ->
        s.copy(items = s.items.map { if (it.uid == uid) transform(it).copy(updatedAt = now()) else it })
    }

    fun deleteItem(uid: String) = snap { s ->
        val gone = s.photos.filter { it.itemUid == uid }
        gone.forEach { File(it.path).delete(); File(it.originalPath).delete() }
        s.copy(
            items = s.items.filter { it.uid != uid },
            photos = s.photos.filter { it.itemUid != uid },
            listings = s.listings.filter { it.itemUid != uid },
            tombstones = s.tombstones + Tombstone(uid, "item") + gone.map { Tombstone(it.uid, "photo") },
        )
    }

    // ------------------------------------------------------------------ photos

    fun addPhotos(itemUid: String, files: List<File>): Int {
        var added = 0
        files.forEach { f ->
            if (photos(itemUid).size >= 10) return@forEach
            val stored = runCatching { PhotoTools.import(f) }.getOrNull() ?: return@forEach
            snap { s ->
                val existing = s.photos.filter { it.itemUid == itemUid }
                s.copy(photos = s.photos + stored.copy(itemId = 0, itemUid = itemUid, position = existing.size, isCover = existing.isEmpty()))
            }
            added++
        }
        return added
    }

    private fun reorder(itemUid: String, list: List<Photo>) = snap { s ->
        val t = now()
        val fixed = list.mapIndexed { i, p -> p.copy(position = i, isCover = i == 0, updatedAt = t) }
        s.copy(photos = s.photos.filter { it.itemUid != itemUid } + fixed)
    }

    fun movePhoto(p: Photo, delta: Int) {
        val list = photos(p.itemUid).toMutableList()
        val from = list.indexOfFirst { it.uid == p.uid }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.add(to, list.removeAt(from))
        reorder(p.itemUid, list)
    }

    fun makeCover(p: Photo) {
        val list = photos(p.itemUid).toMutableList()
        list.removeAll { it.uid == p.uid }
        reorder(p.itemUid, listOf(p) + list)
    }

    fun updatePhoto(uid: String, transform: (Photo) -> Photo) = snap { s ->
        s.copy(photos = s.photos.map { if (it.uid == uid) transform(it).copy(updatedAt = now()) else it })
    }

    fun deletePhoto(p: Photo) {
        File(p.path).delete(); File(p.originalPath).delete()
        snap { s -> s.copy(photos = s.photos.filter { it.uid != p.uid }, tombstones = s.tombstones + Tombstone(p.uid, "photo")) }
        reorder(p.itemUid, photos(p.itemUid))
    }

    // ------------------------------------------------------------------ posting

    fun markPosted(itemUid: String, platform: Platform, url: String) = snap { s ->
        val l = PlatformListing(itemId = 0, platform = platform, url = url.trim(), itemUid = itemUid)
        s.copy(
            listings = s.listings.filter { it.key != l.key } + l,
            items = s.items.map { if (it.uid == itemUid && it.status != ItemStatus.SOLD) it.copy(status = ItemStatus.POSTED, updatedAt = now()) else it },
        )
    }

    fun unmarkPosted(l: PlatformListing) = snap { s ->
        s.copy(listings = s.listings.filter { it.key != l.key }, tombstones = s.tombstones + Tombstone(l.key, "listing"))
    }

    fun setListingRemoved(l: PlatformListing, removed: Boolean) = snap { s ->
        s.copy(listings = s.listings.map { if (it.key == l.key) it.copy(removed = removed, updatedAt = now()) else it })
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = mutate { it.copy(settings = transform(it.settings)) }
    fun regenerateToken() = mutate { it.copy(token = newToken()) }

    // ------------------------------------------------------------------ sync

    fun snapshotForWire(): Snapshot = snapshot.forWire()

    /** Merges an incoming snapshot from the phone; returns the merged result (wire form). */
    fun mergeIncoming(incoming: Snapshot): Snapshot {
        var result = Snapshot()
        mutate { d ->
            val merged = SyncMerge.merge(d.snapshot.forWire(), incoming)
            result = merged
            val local = merged.copy(photos = merged.photos.map {
                it.copy(path = File(photoDir, File(it.path).name).path, originalPath = File(photoDir, File(it.originalPath).name).path)
            })
            // remove files of photos that are gone
            val keep = local.photos.flatMap { listOf(it.path, it.originalPath) }.toSet()
            d.snapshot.photos.flatMap { listOf(it.path, it.originalPath) }.filter { it !in keep }.forEach { File(it).delete() }
            d.copy(snapshot = local)
        }
        return result
    }
}
