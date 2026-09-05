package com.pdfmaster.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import com.pdfmaster.app.domain.repository.OcrEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * OCR via Tesseract4Android (com.googlecode.tesseract.android is its package name,
 * inherited from tess-two on purpose - see the OCR dependency note in app/build.gradle.kts).
 *
 * English trained data is bundled at assets/tessdata/eng.traineddata (from
 * tesseract-ocr/tessdata_fast). To add another language, download the matching
 * <lang>.traineddata from that repo, drop it in the same folder, and pass its
 * code to TessBaseAPI.init() alongside or instead of "eng".
 */
class TesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrEngine {

    // Assumed scan resolution used to convert Tesseract's pixel-space word boxes
    // into PDF points (1 pt = 1/72 in). Good enough for phone-camera captures,
    // which have no embedded DPI metadata to read instead.
    private val assumedDpi = 200f
    private val pointsPerPixel = 72f / assumedDpi

    override suspend fun extractText(
        bitmap: Bitmap,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.1f)
            val dataPath = ensureTrainedDataAvailable()
            val tess = TessBaseAPI()
            try {
                check(tess.init(dataPath, "eng")) { "Failed to initialize Tesseract" }
                tess.setImage(bitmap)
                onProgress(0.6f)
                val text = tess.utF8Text.orEmpty()
                onProgress(1f)
                text
            } finally {
                tess.recycle()
            }
        }
    }

    override suspend fun createSearchablePdf(
        bitmap: Bitmap,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.05f)
            val dataPath = ensureTrainedDataAvailable()
            val tess = TessBaseAPI()
            val words = mutableListOf<OcrWord>()
            try {
                check(tess.init(dataPath, "eng")) { "Failed to initialize Tesseract" }
                tess.setImage(bitmap)
                tess.utF8Text // forces recognition to run before we walk the iterator
                onProgress(0.5f)

                tess.resultIterator?.use2 { iterator ->
                    do {
                        val word = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        if (!word.isNullOrBlank() && box != null) {
                            words += OcrWord(word, box.left, box.top, box.right, box.bottom)
                        }
                    } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                }
            } finally {
                tess.recycle()
            }
            onProgress(0.65f)

            PDDocument().use { document ->
                val pageWidthPt = bitmap.width * pointsPerPixel
                val pageHeightPt = bitmap.height * pointsPerPixel
                val page = PDPage(PDRectangle(pageWidthPt, pageHeightPt))
                document.addPage(page)

                val image = JPEGFactory.createFromImage(document, bitmap, 0.85f)

                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(image, 0f, 0f, pageWidthPt, pageHeightPt)

                    words.forEach { word ->
                        val fontSize = ((word.bottom - word.top) * pointsPerPixel * 0.85f)
                            .coerceAtLeast(1f)
                        val x = word.left * pointsPerPixel
                        val y = pageHeightPt - (word.bottom * pointsPerPixel)
                        runCatching {
                            stream.beginText()
                            stream.setRenderingMode(PDPageContentStream.RenderingMode.NEITHER)
                            stream.setFont(PDType1Font.HELVETICA, fontSize)
                            stream.newLineAtOffset(x, y)
                            stream.showText(word.text)
                            stream.endText()
                        }
                    }
                }
                onProgress(0.9f)

                val out = context.contentResolver.openOutputStream(outputUri)
                    ?: error("Could not open output stream")
                out.use { document.save(it) }
            }
            onProgress(1f)
        }
    }

    private fun ensureTrainedDataAvailable(): String {
        val tesseractDir = File(context.filesDir, "tesseract")
        val tessDataDir = File(tesseractDir, "tessdata")
        if (!tessDataDir.exists()) tessDataDir.mkdirs()
        val trainedData = File(tessDataDir, "eng.traineddata")
        if (!trainedData.exists()) {
            try {
                context.assets.open("tessdata/eng.traineddata").use { input ->
                    trainedData.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: java.io.FileNotFoundException) {
                throw IllegalStateException(
                    "Missing OCR language file. Download eng.traineddata from " +
                        "https://github.com/tesseract-ocr/tessdata_fast and place it at " +
                        "app/src/main/assets/tessdata/eng.traineddata",
                    e
                )
            }
        }
        return tesseractDir.absolutePath
    }

    private data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** ResultIterator has no AutoCloseable in the tess-two-derived API; this just calls delete() after use. */
    private inline fun com.googlecode.tesseract.android.ResultIterator.use2(
        block: (com.googlecode.tesseract.android.ResultIterator) -> Unit
    ) {
        try {
            block(this)
        } finally {
            delete()
        }
    }
}
