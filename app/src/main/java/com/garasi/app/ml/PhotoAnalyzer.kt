package com.garasi.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.garasi.app.data.Photo
import com.garasi.app.photo.PhotoStore
import com.garasi.app.quality.QualityChecker
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

/**
 * On-device photo quality metrics: pixel statistics from [PhotoMetrics] (shared with the desktop app)
 * plus ML Kit object detection for framing (item fill ratio and distance from centre).
 */
class PhotoAnalyzer(private val store: PhotoStore) {
    private val detector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        )
    }

    suspend fun analyze(photo: Photo): Photo = withContext(Dispatchers.Default) {
        val full = store.decodeUpright(File(photo.path), 1024)
        val small = scaleTo(full, 512)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)

        val objectBox: Rect? = runCatching {
            detector.process(InputImage.fromBitmap(full, 0)).await()
                .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.boundingBox
        }.getOrNull()
        var fill: Double? = null
        var offset: Double? = null
        var inner: IntArray? = null
        if (objectBox != null) {
            fill = (objectBox.width().toDouble() * objectBox.height()) / (full.width.toDouble() * full.height)
            val dx = objectBox.exactCenterX() - full.width / 2.0
            val dy = objectBox.exactCenterY() - full.height / 2.0
            offset = hypot(dx, dy) / (hypot(full.width.toDouble(), full.height.toDouble()) / 2)
            val sx = w.toDouble() / full.width
            val sy = h.toDouble() / full.height
            inner = intArrayOf((objectBox.left * sx).toInt(), (objectBox.top * sy).toInt(), (objectBox.right * sx).toInt(), (objectBox.bottom * sy).toInt())
        }
        val m = PhotoMetrics.compute(px, w, h, inner)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        val updated = photo.copy(
            width = bounds.outWidth, height = bounds.outHeight,
            sharpness = m.sharpness, brightness = m.brightness, contrast = m.contrast,
            edgeDensity = m.edgeDensity, colorVariance = m.colorVariance,
            objectFill = fill, objectOffset = offset, updatedAt = System.currentTimeMillis(),
        )
        updated.copy(score = QualityChecker.photoScore(updated))
    }

    private fun scaleTo(b: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= maxSide) return b
        val s = maxSide.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }
}
