package com.garasi.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.garasi.app.GarasiApp
import com.garasi.app.data.Category
import com.garasi.app.data.Condition
import com.garasi.app.data.Item
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.ListingLang
import com.garasi.app.data.Photo
import com.garasi.app.data.Platform
import com.garasi.app.data.PlatformListing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/**
 * Debug-only: `adb shell am broadcast -n com.garasi.app/.debug.DemoSeeder` fills the app with a few
 * demo items (synthetic photos) so the UI can be previewed and screenshotted.
 */
class DemoSeeder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as GarasiApp
        CoroutineScope(Dispatchers.IO).launch {
            try { seed(app) } finally { pending.finish() }
        }
    }

    private suspend fun seed(app: GarasiApp) {
        val dao = app.db.dao()
        if (dao.allItems().isNotEmpty()) return
        app.settings.update { it.copy(city = "Jakarta Selatan") }
        val day = 86_400_000L
        val now = System.currentTimeMillis()

        // 1) Headphones — Ready, full listing in both languages
        val sonyId = dao.insert(
            Item(
                createdAt = now - 2 * day, status = ItemStatus.READY, category = Category.ELECTRONICS,
                brand = "Sony", model = "WH-1000XM4", variant = "Hitam", condition = Condition.USED_FLAWS,
                yearBought = "2022", usageAge = "±1,5 tahun", flaws = "busa kiri agak ngelupas dikit",
                included = "box, kabel charger, kabel audio, pouch", reasonSelling = "udah jarang dipake sejak WFO",
                location = "Jakarta Selatan", shipping = "JNE / SiCepat / GoSend", weightGrams = 1200,
                listingLang = ListingLang.ID,
                titleId = "Sony WH-1000XM4 Hitam Bekas Mulus Noise Cancelling Normal",
                descId = """
                    Jual Sony WH-1000XM4 hitam, pemakaian ±1,5 tahun. Suara masih mantap, noise cancelling normal, baterai tahan ±25 jam.

                    • Minus: busa kiri agak ngelupas dikit (lihat foto ke-7)
                    • Kelengkapan: box, kabel charger, kabel audio, pouch
                    • Tahun beli: 2022

                    Dijual karena udah jarang dipake sejak WFO.
                    📍 Jakarta Selatan, bisa COD atau kirim via JNE / SiCepat / GoSend.
                    Bisa nego tipis, langsung chat aja ya 🙏
                """.trimIndent(),
                titleEn = "Sony WH-1000XM4 Black, used (bekas), noise cancelling works",
                descEn = """
                    Selling my black Sony WH-1000XM4, used for about 1.5 years. Sound is still great, noise cancelling works fine, battery lasts ~25 hours.

                    • Flaw: left ear cushion is peeling a little (see photo 7)
                    • Comes with: box, charging cable, audio cable, pouch

                    Selling because I rarely use it since going back to the office.
                    📍 South Jakarta — COD or shipping via JNE / SiCepat / GoSend.
                    Price slightly negotiable, just send me a message 🙏
                """.trimIndent(),
                askPrice = 2_150_000, newPrice = 4_999_000, aiUsedMin = 1_900_000, aiUsedMax = 2_300_000,
                compareLow = 1_750_000, compareMedian = 3_200_000, compareHigh = 3_600_000,
            )
        )
        val angles = listOf("front", "back", "sides", "screen_on", "label", "accessories", "flaw")
        angles.forEachIndexed { i, a -> addPhoto(app, sonyId, i, a, Kind.HEADPHONES, flaw = a == "flaw") }

        // 2) Lamp — Posted on Tokopedia
        val lampId = dao.insert(
            Item(
                createdAt = now - 6 * day, status = ItemStatus.POSTED, category = Category.HOME,
                brand = "IKEA", model = "Ranarp", variant = "Putih", condition = Condition.LIKE_NEW,
                titleId = "IKEA Ranarp Lampu Meja Putih Like New", askPrice = 350_000,
                descId = "Jual lampu meja IKEA Ranarp putih, baru dipakai 3 bulan. Bisa nego tipis, langsung chat aja ya 🙏",
                location = "Jakarta Selatan",
            )
        )
        listOf("front", "back", "detail", "in_use").forEachIndexed { i, a -> addPhoto(app, lampId, i, a, Kind.LAMP) }
        dao.upsertListing(PlatformListing(lampId, Platform.TOKOPEDIA, "https://www.tokopedia.com/garasi/ikea-ranarp", itemUid = dao.getItem(lampId)?.uid.orEmpty()))

        // 3) Books — Sold
        val booksId = dao.insert(
            Item(
                createdAt = now - 12 * day, status = ItemStatus.SOLD, category = Category.BOOKS,
                brand = "Gramedia", model = "Harry Potter 1–7", variant = "Bahasa Indonesia",
                condition = Condition.USED_GOOD, titleId = "Novel Harry Potter 1–7 Bahasa Indonesia Bekas Lengkap",
                askPrice = 900_000, soldPrice = 850_000, soldPlatform = Platform.SHOPEE, soldAt = now - 4 * day,
            )
        )
        listOf("front", "spine", "pages", "back").forEachIndexed { i, a -> addPhoto(app, booksId, i, a, Kind.BOOKS) }

        // 4) Draft — barely started, English listing
        dao.insert(
            Item(
                createdAt = now - day / 2, category = Category.TOYS, brand = "Nintendo", model = "Switch Lite",
                variant = "Turquoise", listingLang = ListingLang.EN, condition = Condition.USED_GOOD,
            )
        )
    }

    private enum class Kind { HEADPHONES, LAMP, BOOKS }

    private suspend fun addPhoto(app: GarasiApp, itemId: Long, pos: Int, angle: String, kind: Kind, flaw: Boolean = false) {
        val file = File(app.cacheDir, "demo_${itemId}_$pos.jpg")
        val bmp = draw(kind, pos, flaw)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val stored = app.photoStore.importFile(file)
        val dao = app.db.dao()
        val photo = Photo(
            itemId = itemId, itemUid = dao.getItem(itemId)?.uid.orEmpty(), originalPath = stored.originalPath, path = stored.path, position = pos,
            isCover = pos == 0, isFlaw = flaw, angle = angle, width = stored.width, height = stored.height,
        )
        val id = dao.insertPhoto(photo)
        runCatching { app.analyzer.analyze(photo.copy(id = id)) }.getOrNull()?.let { dao.updatePhotoRaw(it) }
    }

    /** Synthetic "product photo": soft daylight backdrop, a simple object, and a little sensor noise. */
    private fun draw(kind: Kind, variant: Int, flaw: Boolean): Bitmap {
        val w = 1400
        val h = if (variant % 3 == 1) 1050 else 1400
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.rgb(246, 243, 238), Color.rgb(214, 208, 200), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val cx = w / 2f
        val cy = h / 2f
        val s = if (flaw) 2.2f else 1f - (variant % 4) * 0.06f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (kind) {
            Kind.HEADPHONES -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 70f * s; p.color = Color.rgb(32, 32, 36)
                c.drawArc(RectF(cx - 330 * s, cy - 380 * s, cx + 330 * s, cy + 280 * s), 180f, 180f, false, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(cx - 420 * s, cy - 40 * s, cx - 230 * s, cy + 300 * s), 90 * s, 90 * s, p)
                c.drawRoundRect(RectF(cx + 230 * s, cy - 40 * s, cx + 420 * s, cy + 300 * s), 90 * s, 90 * s, p)
                p.color = Color.rgb(70, 70, 76)
                c.drawRoundRect(RectF(cx - 395 * s, cy - 10 * s, cx - 255 * s, cy + 270 * s), 70 * s, 70 * s, p)
                c.drawRoundRect(RectF(cx + 255 * s, cy - 10 * s, cx + 395 * s, cy + 270 * s), 70 * s, 70 * s, p)
                if (flaw) { p.color = Color.rgb(150, 140, 130); c.drawOval(RectF(cx - 380 * s, cy + 60 * s, cx - 300 * s, cy + 120 * s), p) }
                p.color = Color.rgb(200, 200, 205); p.textSize = 34f * s
                c.drawText("SONY", cx - 50 * s, cy - 300 * s, p)
            }
            Kind.LAMP -> {
                p.color = Color.rgb(245, 245, 245); p.style = Paint.Style.FILL
                val shade = android.graphics.Path().apply {
                    moveTo(cx - 120, cy - 360); lineTo(cx + 120, cy - 360); lineTo(cx + 260, cy - 80); lineTo(cx - 260, cy - 80); close()
                }
                c.drawPath(shade, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(150, 150, 150); c.drawPath(shade, p)
                p.style = Paint.Style.FILL; p.color = Color.rgb(90, 90, 95)
                c.drawRect(cx - 14, cy - 80, cx + 14, cy + 330, p)
                c.drawOval(RectF(cx - 200, cy + 300, cx + 200, cy + 380), p)
            }
            Kind.BOOKS -> {
                val colors = listOf(Color.rgb(160, 40, 40), Color.rgb(40, 90, 150), Color.rgb(60, 120, 70),
                    Color.rgb(200, 150, 40), Color.rgb(110, 60, 140), Color.rgb(30, 30, 30), Color.rgb(170, 90, 40))
                colors.forEachIndexed { i, col ->
                    p.color = col
                    c.drawRect(cx - 400 + i * 115f, cy - 330 + (i % 2) * 20f, cx - 300 + i * 115f, cy + 330, p)
                }
            }
        }
        // light sensor noise so it reads as a real (sharp) photo
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val r = Random(variant + kind.ordinal * 31)
        for (i in px.indices) {
            val n = r.nextInt(-10, 11)
            val col = px[i]
            val rr = ((col shr 16 and 0xFF) + n).coerceIn(0, 255)
            val gg = ((col shr 8 and 0xFF) + n).coerceIn(0, 255)
            val bb = ((col and 0xFF) + n).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }
}
