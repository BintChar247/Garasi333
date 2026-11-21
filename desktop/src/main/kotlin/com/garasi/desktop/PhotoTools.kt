package com.garasi.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.garasi.app.data.Photo
import com.garasi.app.ml.PhotoMetrics
import com.garasi.app.quality.QualityChecker
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Import, compress (< 1 MB, max 1600 px), analyse and display photos on the desktop. */
object PhotoTools {
    private const val MAX_SIDE = 1600
    private const val MAX_BYTES = 1_000_000

    fun import(src: File): Photo {
        val img = ImageIO.read(src) ?: error("Not an image")
        val id = UUID.randomUUID().toString()
        val original = File(Store.photoDir, "orig_$id.${src.extension.lowercase().ifBlank { "jpg" }}")
        src.copyTo(original, overwrite = true)
        val scaled = scale(toRgb(img), MAX_SIDE)
        val out = File(Store.photoDir, "img_$id.jpg")
        out.writeBytes(compress(scaled))
        val base = Photo(itemId = 0, originalPath = original.path, path = out.path, position = 0,
            width = scaled.width, height = scaled.height)
        return analyze(base, scaled)
    }

    /** Pixel metrics (no object detection on desktop; framing checks come from phone photos). */
    fun analyze(p: Photo, image: BufferedImage? = null): Photo {
        val img = image ?: ImageIO.read(File(p.path)) ?: return p
        val small = scale(toRgb(img), 512)
        val w = small.width; val h = small.height
        val px = small.getRGB(0, 0, w, h, null, 0, w)
        val m = PhotoMetrics.compute(px, w, h, null)
        val updated = p.copy(
            width = img.width, height = img.height, sharpness = m.sharpness, brightness = m.brightness,
            contrast = m.contrast, edgeDensity = m.edgeDensity, colorVariance = m.colorVariance,
        )
        return updated.copy(score = QualityChecker.photoScore(updated))
    }

    /** Centre square crop of the listing copy (desktop "Crop to square"). */
    fun cropSquare(p: Photo): Photo? {
        val src = ImageIO.read(File(p.originalPath).takeIf { it.exists() } ?: File(p.path)) ?: return null
        val s = minOf(src.width, src.height)
        val sq = toRgb(src).getSubimage((src.width - s) / 2, (src.height - s) / 2, s, s)
        val scaled = scale(sq, MAX_SIDE)
        val out = File(Store.photoDir, "img_${UUID.randomUUID()}.jpg")
        out.writeBytes(compress(scaled))
        File(p.path).takeIf { it.path != p.originalPath }?.delete()
        thumbs.remove(p.path)
        return analyze(p.copy(path = out.path, width = scaled.width, height = scaled.height), scaled)
    }

    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB) return img
        val rgb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply { drawImage(img, 0, 0, java.awt.Color.WHITE, null); dispose() }
        return rgb
    }

    private fun scale(img: BufferedImage, maxSide: Int): BufferedImage {
        val long = maxOf(img.width, img.height)
        if (long <= maxSide) return img
        val s = maxSide.toDouble() / long
        val w = (img.width * s).toInt().coerceAtLeast(1); val h = (img.height * s).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(img, 0, 0, w, h, null); dispose()
        }
        return out
    }

    private fun compress(img: BufferedImage): ByteArray {
        var quality = 0.9f
        var current = img
        while (true) {
            val bytes = jpeg(current, quality)
            if (bytes.size < MAX_BYTES) return bytes
            if (quality > 0.6f) quality -= 0.1f else current = scale(current, (maxOf(current.width, current.height) * 0.85).toInt())
        }
    }

    private fun jpeg(img: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val bos = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(bos).use { ios ->
            writer.output = ios
            val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = quality }
            writer.write(null, IIOImage(img, null, null), param)
        }
        writer.dispose()
        return bos.toByteArray()
    }

    // ------------------------------------------------------------------ display cache

    private val thumbs = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 200
    }

    /** Small bitmap for display (cached). */
    @Synchronized
    fun thumb(path: String, size: Int = 360): ImageBitmap? {
        val key = "$path@$size"
        thumbs[key]?.let { return it }
        val f = File(path)
        if (!f.exists()) return null
        val img = runCatching { ImageIO.read(f) }.getOrNull() ?: return null
        val small = scale(toRgb(img), size)
        val bmp = Image.makeFromEncoded(jpeg(small, 0.85f)).toComposeImageBitmap()
        thumbs[key] = bmp
        return bmp
    }
}
