package com.garasi.app

import com.garasi.app.data.Item
import com.garasi.app.data.Photo
import com.garasi.app.data.Platform
import com.garasi.app.data.PlatformListing
import com.garasi.app.data.Tombstone
import com.garasi.app.sync.Snapshot
import com.garasi.app.sync.SyncMerge
import com.garasi.app.sync.SyncProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    private val a = Item(uid = "a", brand = "Sony", updatedAt = 100)
    private val pa = Photo(itemId = 1, uid = "pa", itemUid = "a", originalPath = "/x/orig_1.jpg", path = "/x/img_1.jpg", position = 0, updatedAt = 100)

    @Test fun newestEditWins() {
        val phone = Snapshot(items = listOf(a.copy(brand = "Sony phone", updatedAt = 200)))
        val pc = Snapshot(items = listOf(a.copy(brand = "Sony pc", updatedAt = 300)))
        assertEquals("Sony pc", SyncMerge.merge(phone, pc).items.single().brand)
        assertEquals("Sony pc", SyncMerge.merge(pc, phone).items.single().brand)
    }

    @Test fun unionOfNewRecords() {
        val b = Item(uid = "b", brand = "IKEA", updatedAt = 50)
        val m = SyncMerge.merge(Snapshot(items = listOf(a), photos = listOf(pa)), Snapshot(items = listOf(b)))
        assertEquals(setOf("a", "b"), m.items.map { it.uid }.toSet())
        assertEquals(1, m.photos.size)
    }

    @Test fun deletionBeatsOlderEditButNotNewer() {
        val tomb = Tombstone("a", "item", deletedAt = 150)
        val m1 = SyncMerge.merge(Snapshot(items = listOf(a), photos = listOf(pa)), Snapshot(tombstones = listOf(tomb)))
        assertTrue(m1.items.isEmpty())
        assertTrue("photos of deleted items go too", m1.photos.isEmpty())
        val m2 = SyncMerge.merge(Snapshot(items = listOf(a.copy(updatedAt = 400))), Snapshot(tombstones = listOf(tomb)))
        assertEquals(1, m2.items.size)
    }

    @Test fun listingsKeyedByItemAndPlatform() {
        val l1 = PlatformListing(itemId = 1, platform = Platform.SHOPEE, url = "old", itemUid = "a", updatedAt = 10)
        val l2 = PlatformListing(itemId = 9, platform = Platform.SHOPEE, url = "new", itemUid = "a", updatedAt = 20)
        val m = SyncMerge.merge(Snapshot(items = listOf(a), listings = listOf(l1)), Snapshot(items = listOf(a), listings = listOf(l2)))
        assertEquals("new", m.listings.single().url)
        val gone = SyncMerge.merge(m, Snapshot(tombstones = listOf(Tombstone("a/SHOPEE", "listing", 30))))
        assertTrue(gone.listings.isEmpty())
    }

    @Test fun mergeIsIdempotentAndWireRoundTrips() {
        val s = Snapshot(items = listOf(a), photos = listOf(pa), tombstones = listOf(Tombstone("z", "item", 5)))
        assertEquals(SyncMerge.merge(s, s), SyncMerge.merge(SyncMerge.merge(s, s), s))
        val wire = s.forWire()
        assertEquals("img_1.jpg", wire.photos.single().path)
        val back = SyncProtocol.decode(SyncProtocol.encode(wire))
        assertEquals(wire, back)
        assertEquals(setOf("img_1.jpg", "orig_1.jpg"), wire.fileNames())
    }

    @Test fun pairingLinks() {
        val p = SyncProtocol.Pairing(listOf("192.168.1.5", "10.0.0.2"), 8737, "abc123XYZ")
        val parsed = SyncProtocol.parsePairing(p.toUri())
        assertNotNull(parsed)
        assertEquals(p, parsed)
        assertNull(SyncProtocol.parsePairing("https://example.com"))
        assertTrue(SyncProtocol.isSafeFileName("img_5f2.jpg"))
        assertTrue(!SyncProtocol.isSafeFileName("../etc/passwd"))
    }
}
