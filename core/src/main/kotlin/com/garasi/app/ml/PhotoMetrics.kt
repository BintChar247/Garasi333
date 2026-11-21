package com.garasi.app.ml

import kotlin.math.abs
import kotlin.math.sqrt

/** Pixel statistics used by the Appeal Score. Platform code supplies ARGB pixels (≈512 px long side). */
object PhotoMetrics {
    data class Result(
        val sharpness: Double, val brightness: Double, val contrast: Double,
        val edgeDensity: Double, val colorVariance: Double,
    )

    /**
     * @param inner the item's bounding box in these pixel coordinates (left, top, right, bottom), or null to
     * use the centre 60 % — everything outside it counts as background.
     */
    fun compute(px: IntArray, w: Int, h: Int, inner: IntArray? = null): Result {
        val gray = DoubleArray(px.size) { i ->
            val c = px[i]
            0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
        }
        val mean = gray.average()
        val contrast = sqrt(gray.sumOf { (it - mean) * (it - mean) } / gray.size)

        var lapSum = 0.0; var lapSq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] - 4 * gray[i]
            lapSum += lap; lapSq += lap * lap; n++
        }
        val lapMean = if (n > 0) lapSum / n else 0.0
        val sharpness = if (n > 0) lapSq / n - lapMean * lapMean else 0.0

        val box = inner ?: intArrayOf((w * 0.2).toInt(), (h * 0.2).toInt(), (w * 0.8).toInt(), (h * 0.8).toInt())
        var edges = 0; var bg = 0
        var rS = 0.0; var gS = 0.0; var bS = 0.0; var rQ = 0.0; var gQ = 0.0; var bQ = 0.0; var cN = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            if (x >= box[0] && x < box[2] && y >= box[1] && y < box[3]) continue
            val i = y * w + x
            if (abs(gray[i + 1] - gray[i - 1]) + abs(gray[i + w] - gray[i - w]) > 60) edges++
            bg++
            if ((x + y) % 3 == 0) {
                val c = px[i]
                val r = ((c shr 16) and 0xFF).toDouble(); val g = ((c shr 8) and 0xFF).toDouble(); val b = (c and 0xFF).toDouble()
                rS += r; gS += g; bS += b; rQ += r * r; gQ += g * g; bQ += b * b; cN++
            }
        }
        fun std(sum: Double, sq: Double) = if (cN == 0) 0.0 else sqrt((sq / cN - (sum / cN) * (sum / cN)).coerceAtLeast(0.0))
        return Result(
            sharpness = sharpness, brightness = mean, contrast = contrast,
            edgeDensity = if (bg > 0) edges.toDouble() / bg else 0.0,
            colorVariance = (std(rS, rQ) + std(gS, gQ) + std(bS, bQ)) / 3,
        )
    }
}
