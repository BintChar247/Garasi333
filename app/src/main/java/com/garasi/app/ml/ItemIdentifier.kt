package com.garasi.app.ml

import android.content.Context
import android.net.Uri
import com.garasi.app.data.Category
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

data class IdentifyResult(
    val barcode: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val category: Category? = null,
    val labels: List<String> = emptyList(),
    val textSnippet: String = "",
) {
    val isEmpty get() = barcode == null && brand == null && model == null && category == null
}

/** Free, offline item identification with ML Kit: barcode + OCR + image labeling. */
class ItemIdentifier(private val context: Context) {

    suspend fun identify(paths: List<String>): IdentifyResult {
        val barcoder = BarcodeScanning.getClient()
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
        try {
            var barcode: String? = null
            val text = StringBuilder()
            val labelScores = mutableMapOf<String, Float>()
            for (path in paths.take(10)) {
                val image = InputImage.fromFilePath(context, Uri.fromFile(File(path)))
                if (barcode == null) {
                    val codes = runCatching { barcoder.process(image).await() }.getOrNull().orEmpty()
                    barcode = (codes.firstOrNull { it.rawValue != null && it.format in PRODUCT_FORMATS }
                        ?: codes.firstOrNull { !it.rawValue.isNullOrBlank() && it.valueType != Barcode.TYPE_URL })
                        ?.rawValue
                }
                runCatching { ocr.process(image).await() }.getOrNull()?.let { text.append(it.text).append('\n') }
                runCatching { labeler.process(image).await() }.getOrNull()?.forEach {
                    labelScores[it.text] = (labelScores[it.text] ?: 0f) + it.confidence
                }
            }
            val ocrText = text.toString()
            val labels = labelScores.entries.sortedByDescending { it.value }.map { it.key }
            return IdentifyResult(
                barcode = barcode,
                brand = guessBrand(ocrText),
                model = guessModel(ocrText),
                category = guessCategory(labelScores),
                labels = labels.take(6),
                textSnippet = ocrText.lines().map { it.trim() }.filter { it.length >= 3 }.take(6).joinToString(" · "),
            )
        } finally {
            barcoder.close(); ocr.close(); labeler.close()
        }
    }

    companion object {
        private val PRODUCT_FORMATS = setOf(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
        )

        fun guessBrand(text: String) = IdentifyHeuristics.guessBrand(text)
        fun guessModel(text: String) = IdentifyHeuristics.guessModel(text)
        fun guessCategory(labelScores: Map<String, Float>) = IdentifyHeuristics.guessCategory(labelScores)
    }
}
