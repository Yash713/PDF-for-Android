package com.pdfmaster.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.pdfmaster.app.domain.model.CompressQuality
import com.pdfmaster.app.domain.model.DetectedWatermark
import com.pdfmaster.app.domain.model.EditElement
import com.pdfmaster.app.domain.model.PageDiff
import com.pdfmaster.app.domain.model.PageNumberConfig
import com.pdfmaster.app.domain.model.PageNumberFormat
import com.pdfmaster.app.domain.model.PageNumberPosition
import com.pdfmaster.app.domain.model.PdfAConformanceLevel
import com.pdfmaster.app.domain.model.PdfComparisonResult
import com.pdfmaster.app.domain.model.RepairOutcome
import com.pdfmaster.app.domain.model.ShapeType
import com.pdfmaster.app.domain.model.WatermarkConfig
import com.pdfmaster.app.domain.model.WatermarkPosition
import com.pdfmaster.app.domain.model.WatermarkType
import com.pdfmaster.app.domain.repository.PdfEngine
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.min

class PdfEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfEngine {

    override suspend fun getPageCount(inputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open $inputUri")
            stream.use { PDDocument.load(it).use { doc -> doc.numberOfPages } }
        }
    }

    override suspend fun mergePdfs(
        inputUris: List<Uri>,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputUris.isNotEmpty()) { "No files selected to merge" }

            val sourceDocuments = inputUris.map { uri ->
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open $uri")
                stream.use { PDDocument.load(it) }
            }
            val totalPages = sourceDocuments.sumOf { it.numberOfPages }.coerceAtLeast(1)

            val merged = PDDocument()
            try {
                var processed = 0
                sourceDocuments.forEach { source ->
                    source.use { doc ->
                        for (page in doc.pages) {
                            merged.importPage(page)
                            processed++
                            onProgress(processed.toFloat() / totalPages)
                        }
                    }
                }
                writeTo(outputUri) { merged.save(it) }
            } finally {
                merged.close()
            }
            onProgress(1f)
        }
    }

    override suspend fun splitPdf(
        inputUri: Uri,
        pageRanges: List<IntRange>,
        outputDir: Uri,
        onProgress: (Float) -> Unit
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        runCatching {
            require(pageRanges.isNotEmpty()) { "No page ranges specified" }
            val destinationDir = DocumentFile.fromTreeUri(context, outputDir)
                ?: error("Invalid output folder")

            val outputs = mutableListOf<Uri>()
            val sourceStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            sourceStream.use { input ->
                PDDocument.load(input).use { source ->
                    pageRanges.forEachIndexed { index, range ->
                        val chunk = PDDocument()
                        try {
                            range.forEach { pageIndex ->
                                require(pageIndex in 0 until source.numberOfPages) {
                                    "Page $pageIndex is out of range"
                                }
                                chunk.importPage(source.getPage(pageIndex))
                            }
                            val outFile = destinationDir.createFile(
                                "application/pdf",
                                "split_part_${index + 1}.pdf"
                            ) ?: error("Could not create output file")
                            val outStream = context.contentResolver.openOutputStream(outFile.uri)
                                ?: error("Could not open output stream")
                            outStream.use { chunk.save(it) }
                            outputs += outFile.uri
                        } finally {
                            chunk.close()
                        }
                        onProgress((index + 1).toFloat() / pageRanges.size)
                    }
                }
            }
            outputs
        }
    }

    override suspend fun compressPdf(
        inputUri: Uri,
        outputUri: Uri,
        quality: CompressQuality,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val pages = document.pages.toList()
                    pages.forEachIndexed { pageIndex, page ->
                        val resources = page.resources ?: return@forEachIndexed
                        val xObjectNames = resources.xObjectNames?.toList().orEmpty()
                        for (name in xObjectNames) {
                            val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                            if (xObject is PDImageXObject) {
                                recompressImage(document, resources, name, xObject, quality)
                            }
                        }
                        onProgress((pageIndex + 1).toFloat() / pages.size.coerceAtLeast(1))
                    }
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    private fun recompressImage(
        document: PDDocument,
        resources: com.tom_roush.pdfbox.pdmodel.PDResources,
        name: COSName,
        original: PDImageXObject,
        quality: CompressQuality
    ) {
        runCatching {
            val bitmap = original.image ?: return
            val scaledWidth = (bitmap.width * quality.imageScale).toInt().coerceAtLeast(1)
            val scaledHeight = (bitmap.height * quality.imageScale).toInt().coerceAtLeast(1)
            val scaled = if (quality.imageScale < 1f) {
                Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            } else {
                bitmap
            }
            val recompressed = JPEGFactory.createFromImage(document, scaled, quality.imageQuality / 100f)
            resources.put(name, recompressed)
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    override suspend fun protectPdf(
        inputUri: Uri,
        outputUri: Uri,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.isNotBlank()) { "Password cannot be empty" }
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val permissions = AccessPermission().apply {
                        setCanPrint(true)
                        setCanModify(false)
                        setCanExtractContent(false)
                    }
                    // Owner password unlocks full permissions; user password is what's
                    // asked for when opening the file - here they're the same on purpose
                    // (simple "protect with a password" flow, no separate admin password).
                    val policy = StandardProtectionPolicy(password, password, permissions)
                    policy.encryptionKeyLength = 128
                    document.protect(policy)
                    writeTo(outputUri) { document.save(it) }
                }
            }
        }
    }

    override suspend fun unlockPdf(
        inputUri: Uri,
        outputUri: Uri,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input, password).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    writeTo(outputUri) { document.save(it) }
                }
            }
        }
    }

    override suspend fun addWatermark(
        inputUri: Uri,
        outputUri: Uri,
        config: WatermarkConfig,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (config.type) {
                WatermarkType.TEXT -> require(!config.text.isNullOrBlank()) { "Enter watermark text" }
                WatermarkType.IMAGE -> requireNotNull(config.imageUri) { "Choose a watermark image" }
            }

            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val watermarkImage = config.imageUri?.let { imageUri ->
                        val bitmap = context.contentResolver.openInputStream(imageUri)
                            ?.use(BitmapFactory::decodeStream)
                            ?: error("Could not read watermark image")
                        // Lossless (not JPEG) so a transparent PNG logo keeps its alpha channel.
                        LosslessFactory.createFromImage(document, bitmap)
                    }

                    val pages = document.pages.toList()
                    pages.forEachIndexed { index, page ->
                        drawWatermarkOnPage(document, page, config, watermarkImage)
                        onProgress((index + 1f) / pages.size.coerceAtLeast(1))
                    }

                    // Tags the file as ours (and marks exactly one appended content
                    // stream per page as the watermark) so removeOwnWatermark can
                    // find and strip it again without touching original content.
                    document.documentInformation.setCustomMetadataValue(WATERMARK_META_KEY, WATERMARK_META_VALUE)
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    private fun drawWatermarkOnPage(
        document: PDDocument,
        page: PDPage,
        config: WatermarkConfig,
        image: PDImageXObject?
    ) {
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val transparency = PDExtendedGraphicsState().apply {
                setNonStrokingAlphaConstant(config.opacity.coerceIn(0f, 1f))
            }
            stream.setGraphicsStateParameters(transparency)

            val spots = if (config.tile) {
                tiledAnchors(pageWidth, pageHeight)
            } else {
                listOf(anchorFor(config.position, pageWidth, pageHeight))
            }
            spots.forEach { (x, y) -> drawWatermarkAt(stream, config, image, x, y, pageWidth) }
        }
    }

    private fun anchorFor(position: WatermarkPosition, pageWidth: Float, pageHeight: Float): Pair<Float, Float> {
        val margin = 56f
        return when (position) {
            WatermarkPosition.CENTER -> pageWidth / 2f to pageHeight / 2f
            WatermarkPosition.TOP_LEFT -> margin to pageHeight - margin
            WatermarkPosition.TOP_RIGHT -> pageWidth - margin to pageHeight - margin
            WatermarkPosition.BOTTOM_LEFT -> margin to margin
            WatermarkPosition.BOTTOM_RIGHT -> pageWidth - margin to margin
        }
    }

    private fun tiledAnchors(pageWidth: Float, pageHeight: Float): List<Pair<Float, Float>> {
        val stepX = pageWidth / 3f
        val stepY = pageHeight / 4f
        val points = mutableListOf<Pair<Float, Float>>()
        var y = stepY / 2f
        while (y < pageHeight) {
            var x = stepX / 2f
            while (x < pageWidth) {
                points += x to y
                x += stepX
            }
            y += stepY
        }
        return points
    }

    private fun drawWatermarkAt(
        stream: PDPageContentStream,
        config: WatermarkConfig,
        image: PDImageXObject?,
        x: Float,
        y: Float,
        pageWidth: Float
    ) {
        stream.saveGraphicsState()
        try {
            // Move the origin to the anchor and rotate there, then draw centered
            // on the (now local) origin - simpler and safer than hand-building a
            // single compound Matrix, since PDF content streams already compose
            // sequential `cm` transforms in exactly this order.
            stream.transform(Matrix.getTranslateInstance(x, y))
            val radians = Math.toRadians(config.rotation.toDouble())
            stream.transform(Matrix.getRotateInstance(radians, 0f, 0f))

            when (config.type) {
                WatermarkType.TEXT -> {
                    val text = config.text.orEmpty()
                    val font = PDType1Font.HELVETICA_BOLD
                    val halfWidth = font.getStringWidth(text) / 1000f * config.fontSize / 2f
                    val color = android.graphics.Color.valueOf(config.textColor)
                    stream.setNonStrokingColor(color.red(), color.green(), color.blue())
                    stream.beginText()
                    stream.setFont(font, config.fontSize)
                    stream.newLineAtOffset(-halfWidth, -config.fontSize / 3f)
                    stream.showText(text)
                    stream.endText()
                }

                WatermarkType.IMAGE -> {
                    val img = image ?: return
                    val displayWidth = pageWidth * IMAGE_WATERMARK_WIDTH_FRACTION
                    val displayHeight = displayWidth * img.height / img.width.toFloat()
                    stream.drawImage(img, -displayWidth / 2f, -displayHeight / 2f, displayWidth, displayHeight)
                }
            }
        } finally {
            stream.restoreGraphicsState()
        }
    }

    override suspend fun removeOwnWatermark(
        inputUri: Uri,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val tag = document.documentInformation.getCustomMetadataValue(WATERMARK_META_KEY)
                    check(tag == WATERMARK_META_VALUE) {
                        "This PDF wasn't watermarked by PDF Master - use the Cover tool instead"
                    }

                    val pages = document.pages.toList()
                    pages.forEachIndexed { index, page ->
                        stripLastAppendedStream(page)
                        onProgress((index + 1f) / pages.size.coerceAtLeast(1))
                    }
                    document.documentInformation.setCustomMetadataValue(WATERMARK_META_KEY, null)
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    /**
     * addWatermark always appends exactly one new content stream to a page
     * (PDPageContentStream.AppendMode.APPEND never merges into an existing
     * stream object - it can't, since two independently-compressed streams
     * can't just be concatenated), so undoing it is dropping that last stream.
     * Leaves single-stream pages untouched rather than guess.
     */
    private fun stripLastAppendedStream(page: PDPage) {
        val contents = page.cosObject.getDictionaryObject(COSName.CONTENTS)
        if (contents is COSArray && contents.size() > 1) {
            contents.remove(contents.size() - 1)
        }
    }

    override suspend fun coverWatermark(
        inputUri: Uri,
        outputUri: Uri,
        pages: Map<Int, List<RectF>>,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "No areas selected to cover" }
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val allPages = document.pages.toList()
                    allPages.forEachIndexed { index, page ->
                        val rects = pages[index]
                        if (!rects.isNullOrEmpty()) {
                            PDPageContentStream(
                                document, page, PDPageContentStream.AppendMode.APPEND, true, true
                            ).use { stream ->
                                stream.setNonStrokingColor(1f, 1f, 1f)
                                rects.forEach { rect ->
                                    // Normalize in case the caller's rect used a
                                    // top<bottom (screen) or bottom<top (PDF) convention.
                                    val left = min(rect.left, rect.right)
                                    val bottom = min(rect.top, rect.bottom)
                                    stream.addRect(left, bottom, abs(rect.width()), abs(rect.height()))
                                    stream.fill()
                                }
                            }
                        }
                        onProgress((index + 1f) / allPages.size.coerceAtLeast(1))
                    }
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    override suspend fun detectWatermarks(
        inputUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<List<DetectedWatermark>> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val detections = mutableListOf<DetectedWatermark>()
                    val pageCount = document.numberOfPages.coerceAtLeast(1)
                    val stripper = TransparentTextBoundsStripper()

                    for (pageIndex in 0 until document.numberOfPages) {
                        stripper.detectedBounds = null
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        stripper.getText(document)
                        stripper.detectedBounds?.let { bounds ->
                            detections += DetectedWatermark(pageIndex, bounds, "transparent_text")
                        }
                        onProgress((pageIndex + 1f) / pageCount)
                    }
                    detections
                }
            }
        }
    }

    /**
     * Heuristic only: flags text drawn under a non-stroking alpha < 0.95 as a
     * likely watermark. Coordinates come straight from PDFBox's TextPosition
     * (its own top-down, page-relative space) - treat [detectedBounds] as an
     * advisory hint of where/whether a watermark exists, not as ready-to-use
     * input for coverWatermark without the user confirming it on the preview.
     * Doesn't detect image-based watermarks (would need content-stream image-
     * placement tracking, a bigger lift than this optional feature warrants yet).
     */
    private class TransparentTextBoundsStripper : PDFTextStripper() {
        var detectedBounds: RectF? = null

        override fun processTextPosition(text: TextPosition) {
            super.processTextPosition(text)
            if (graphicsState.alphaConstant < 0.95f) {
                val box = RectF(
                    text.xDirAdj,
                    text.yDirAdj - text.heightDir,
                    text.xDirAdj + text.widthDirAdj,
                    text.yDirAdj
                )
                detectedBounds = detectedBounds?.let { existing ->
                    RectF(
                        min(existing.left, box.left),
                        min(existing.top, box.top),
                        maxOf(existing.right, box.right),
                        maxOf(existing.bottom, box.bottom)
                    )
                } ?: box
            }
        }
    }

    override suspend fun rotatePdf(
        inputUri: Uri,
        outputUri: Uri,
        angle: Int,
        pageIndices: List<Int>?,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(angle % 90 == 0) { "Rotation angle must be a multiple of 90" }
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val pages = document.pages.toList()
                    val targets = pageIndices?.toSet()
                    pages.forEachIndexed { index, page ->
                        if (targets == null || index in targets) {
                            page.rotation = (page.rotation + angle) % 360
                        }
                        onProgress((index + 1f) / pages.size.coerceAtLeast(1))
                    }
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    override suspend fun organizePdf(
        inputUri: Uri,
        outputUri: Uri,
        pageOrder: List<Int>,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pageOrder.isNotEmpty()) { "No pages selected" }
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { source ->
                    val newDocument = PDDocument()
                    try {
                        pageOrder.forEachIndexed { i, pageIndex ->
                            require(pageIndex in 0 until source.numberOfPages) {
                                "Page $pageIndex is out of range"
                            }
                            newDocument.importPage(source.getPage(pageIndex))
                            onProgress((i + 1f) / pageOrder.size)
                        }
                        writeTo(outputUri) { newDocument.save(it) }
                    } finally {
                        newDocument.close()
                    }
                }
            }
            onProgress(1f)
        }
    }

    override suspend fun addPageNumbers(
        inputUri: Uri,
        outputUri: Uri,
        config: PageNumberConfig,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val pages = document.pages.toList()
                    pages.forEachIndexed { index, page ->
                        val label = formatPageNumber(config.startNumber + index, config.format)
                        drawPageNumber(document, page, label, config)
                        onProgress((index + 1f) / pages.size.coerceAtLeast(1))
                    }
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    private fun formatPageNumber(number: Int, format: PageNumberFormat): String = when (format) {
        PageNumberFormat.NUMERIC -> number.toString()
        PageNumberFormat.ROMAN_LOWER -> toRomanNumeral(number).lowercase()
        PageNumberFormat.ROMAN_UPPER -> toRomanNumeral(number)
    }

    private fun toRomanNumeral(number: Int): String {
        if (number <= 0) return number.toString()
        var remaining = number
        val result = StringBuilder()
        for ((value, symbol) in ROMAN_VALUES) {
            while (remaining >= value) {
                remaining -= value
                result.append(symbol)
            }
        }
        return result.toString()
    }

    private fun drawPageNumber(document: PDDocument, page: PDPage, label: String, config: PageNumberConfig) {
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val margin = 24f
        val font = PDType1Font.HELVETICA
        val textWidth = font.getStringWidth(label) / 1000f * config.fontSize

        val (x, y) = when (config.position) {
            PageNumberPosition.TOP_LEFT -> margin to pageHeight - margin
            PageNumberPosition.TOP_CENTER -> (pageWidth - textWidth) / 2f to pageHeight - margin
            PageNumberPosition.TOP_RIGHT -> pageWidth - margin - textWidth to pageHeight - margin
            PageNumberPosition.BOTTOM_LEFT -> margin to margin
            PageNumberPosition.BOTTOM_CENTER -> (pageWidth - textWidth) / 2f to margin
            PageNumberPosition.BOTTOM_RIGHT -> pageWidth - margin - textWidth to margin
        }

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.setNonStrokingColor(0f, 0f, 0f)
            stream.beginText()
            stream.setFont(font, config.fontSize)
            stream.newLineAtOffset(x, y)
            stream.showText(label)
            stream.endText()
        }
    }

    override suspend fun pdfToJpg(
        inputUri: Uri,
        outputDir: Uri,
        dpi: Int,
        quality: Int,
        onProgress: (Float) -> Unit
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        runCatching {
            val destinationDir = DocumentFile.fromTreeUri(context, outputDir)
                ?: error("Invalid output folder")

            val cacheFile = File.createTempFile("pdf_to_jpg_", ".pdf", context.cacheDir)
            try {
                val copied = context.contentResolver.openInputStream(inputUri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                check(copied) { "Could not open input PDF" }

                val outputs = mutableListOf<Uri>()
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pageCount = renderer.pageCount.coerceAtLeast(1)
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val scale = dpi / 72f
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val outFile = destinationDir.createFile("image/jpeg", "page_${index + 1}.jpg")
                                    ?: error("Could not create output file")
                                context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
                                }
                                bitmap.recycle()
                                outputs += outFile.uri
                            }
                            onProgress((index + 1f) / pageCount)
                        }
                    }
                }
                outputs
            } finally {
                cacheFile.delete()
            }
        }
    }

    override suspend fun jpgToPdf(
        imageUris: List<Uri>,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageUris.isNotEmpty()) { "No images selected" }
            val document = PDDocument()
            try {
                imageUris.forEachIndexed { index, uri ->
                    val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                        ?: error("Could not read image $uri")
                    val pointsPerPixel = 72f / DEFAULT_IMAGE_DPI
                    val pageWidth = bitmap.width * pointsPerPixel
                    val pageHeight = bitmap.height * pointsPerPixel
                    val page = PDPage(PDRectangle(pageWidth, pageHeight))
                    document.addPage(page)

                    val image = JPEGFactory.createFromImage(document, bitmap, 0.9f)
                    PDPageContentStream(document, page).use { stream ->
                        stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
                    }
                    bitmap.recycle()
                    onProgress((index + 1f) / imageUris.size)
                }
                writeTo(outputUri) { document.save(it) }
            } finally {
                document.close()
            }
            onProgress(1f)
        }
    }

    override suspend fun pdfToPdfA(
        inputUri: Uri,
        outputUri: Uri,
        level: PdfAConformanceLevel,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    onProgress(0.2f)
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    onProgress(0.4f)
                    removeActiveContent(document)
                    onProgress(0.6f)
                    applyPdfAIdentification(document, level)
                    onProgress(0.85f)
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    /** PDF/A disallows document-level JavaScript and auto-run actions. */
    private fun removeActiveContent(document: PDDocument) {
        runCatching {
            val catalogDict = document.documentCatalog.cosObject
            catalogDict.removeItem(COSName.getPDFName("OpenAction"))
            val namesDict = catalogDict.getDictionaryObject(COSName.getPDFName("Names")) as? COSDictionary
            namesDict?.removeItem(COSName.getPDFName("JavaScript"))
        }
    }

    private fun applyPdfAIdentification(document: PDDocument, level: PdfAConformanceLevel) {
        val xmp = """
            <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                  <pdfaid:part>${level.part}</pdfaid:part>
                  <pdfaid:conformance>${level.conformance}</pdfaid:conformance>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent()

        val metadata = PDMetadata(document)
        metadata.importXMPMetadata(xmp.toByteArray(Charsets.UTF_8))
        document.documentCatalog.metadata = metadata
    }

    override suspend fun repairPdf(
        inputUri: Uri,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<RepairOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.1f)
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            // PDFBox's parser already recovers from common xref/trailer corruption
            // on its own during load() - if this throws, the file is genuinely
            // unparseable and there's nothing more we can safely attempt.
            val source = try {
                inputStream.use { PDDocument.load(it) }
            } catch (e: Exception) {
                throw IllegalStateException("This file is too damaged to repair automatically.", e)
            }

            source.use { document ->
                val totalPages = document.numberOfPages
                val rebuilt = PDDocument()
                var recovered = 0
                try {
                    for (index in 0 until totalPages) {
                        runCatching { rebuilt.importPage(document.getPage(index)) }
                            .onSuccess { recovered++ }
                        onProgress(0.1f + 0.8f * (index + 1f) / totalPages.coerceAtLeast(1))
                    }
                    require(recovered > 0) { "No pages could be recovered from this file." }
                    writeTo(outputUri) { rebuilt.save(it) }
                } finally {
                    rebuilt.close()
                }
                onProgress(1f)
                RepairOutcome(totalPages = totalPages, recoveredPages = recovered)
            }
        }
    }

    override suspend fun editPdf(
        inputUri: Uri,
        outputUri: Uri,
        elements: List<EditElement>,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(elements.isNotEmpty()) { "No elements to add" }
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    val elementsByPage = elements.groupBy { it.pageIndex }
                    val pages = document.pages.toList()
                    pages.forEachIndexed { index, page ->
                        elementsByPage[index]?.let { pageElements ->
                            drawEditElements(document, page, pageElements)
                        }
                        onProgress((index + 1f) / pages.size.coerceAtLeast(1))
                    }
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    private fun drawEditElements(document: PDDocument, page: PDPage, elements: List<EditElement>) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            elements.forEach { element ->
                runCatching {
                    when (element) {
                        is EditElement.TextElement -> drawEditText(stream, element)
                        is EditElement.ImageElement -> drawEditImage(document, stream, element)
                        is EditElement.ShapeElement -> drawEditShape(stream, element)
                    }
                }
            }
        }
    }

    private fun drawEditText(stream: PDPageContentStream, element: EditElement.TextElement) {
        val color = android.graphics.Color.valueOf(element.colorArgb)
        stream.setNonStrokingColor(color.red(), color.green(), color.blue())
        stream.beginText()
        stream.setFont(PDType1Font.HELVETICA, element.fontSize)
        stream.newLineAtOffset(element.x, element.y)
        stream.showText(element.text)
        stream.endText()
    }

    private fun drawEditImage(document: PDDocument, stream: PDPageContentStream, element: EditElement.ImageElement) {
        val bitmap = context.contentResolver.openInputStream(element.imageUri)?.use(BitmapFactory::decodeStream)
            ?: return
        val image = LosslessFactory.createFromImage(document, bitmap)
        stream.drawImage(image, element.x, element.y, element.width, element.height)
    }

    private fun drawEditShape(stream: PDPageContentStream, element: EditElement.ShapeElement) {
        val color = android.graphics.Color.valueOf(element.colorArgb)
        stream.setStrokingColor(color.red(), color.green(), color.blue())
        stream.setNonStrokingColor(color.red(), color.green(), color.blue())
        stream.setLineWidth(element.strokeWidthPt)

        when (element.shapeType) {
            ShapeType.RECTANGLE -> {
                stream.addRect(element.x, element.y, element.width, element.height)
                if (element.filled) stream.fill() else stream.stroke()
            }
            ShapeType.CIRCLE -> {
                drawEllipse(stream, element.x, element.y, element.width, element.height)
                if (element.filled) stream.fill() else stream.stroke()
            }
            ShapeType.LINE -> {
                stream.moveTo(element.x, element.y)
                stream.lineTo(element.x + element.width, element.y + element.height)
                stream.stroke()
            }
        }
    }

    /**
     * Approximates an ellipse inscribed in the box [x,y,x+width,y+height] using
     * four cubic Bezier curves (the standard k=0.5523 "circle via bezier"
     * constant) - PDFBox has no native ellipse/circle path primitive.
     */
    private fun drawEllipse(stream: PDPageContentStream, x: Float, y: Float, width: Float, height: Float) {
        val kappa = 0.5523f
        val rx = width / 2f
        val ry = height / 2f
        val cx = x + rx
        val cy = y + ry
        val ox = rx * kappa
        val oy = ry * kappa

        stream.moveTo(cx, cy + ry)
        stream.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy)
        stream.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry)
        stream.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy)
        stream.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry)
        stream.closePath()
    }

    override suspend fun signPdf(
        inputUri: Uri,
        outputUri: Uri,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: error("Could not open input PDF")

            inputStream.use { input ->
                PDDocument.load(input).use { document ->
                    require(pageIndex in 0 until document.numberOfPages) {
                        "Page $pageIndex is out of range"
                    }
                    onProgress(0.3f)
                    val page = document.getPage(pageIndex)
                    // Lossless (not JPEG) so a drawn signature's transparent
                    // background is preserved instead of turning into a white box.
                    val image = LosslessFactory.createFromImage(document, signatureBitmap)
                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.drawImage(image, x, y, width, height)
                    }
                    onProgress(0.7f)
                    document.documentInformation.setCustomMetadataValue(
                        SIGNATURE_META_KEY,
                        SIGNATURE_DATE_FORMAT.format(java.util.Date())
                    )
                    writeTo(outputUri) { document.save(it) }
                }
            }
            onProgress(1f)
        }
    }

    override suspend fun comparePdf(
        firstUri: Uri,
        secondUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<PdfComparisonResult> = withContext(Dispatchers.IO) {
        runCatching {
            val firstCache = File.createTempFile("compare_a_", ".pdf", context.cacheDir)
            val secondCache = File.createTempFile("compare_b_", ".pdf", context.cacheDir)
            try {
                copyUriToFile(firstUri, firstCache)
                copyUriToFile(secondUri, secondCache)

                ParcelFileDescriptor.open(firstCache, ParcelFileDescriptor.MODE_READ_ONLY).use { pfdA ->
                    ParcelFileDescriptor.open(secondCache, ParcelFileDescriptor.MODE_READ_ONLY).use { pfdB ->
                        PdfRenderer(pfdA).use { rendererA ->
                            PdfRenderer(pfdB).use { rendererB ->
                                val pageCountA = rendererA.pageCount
                                val pageCountB = rendererB.pageCount
                                val comparedPages = minOf(pageCountA, pageCountB)
                                val diffs = mutableListOf<PageDiff>()

                                for (index in 0 until comparedPages) {
                                    val bitmapA = renderPageToBitmap(rendererA, index, COMPARE_WIDTH_PX)
                                    val bitmapB = renderPageToBitmap(rendererB, index, COMPARE_WIDTH_PX)
                                    diffs += diffPages(index, bitmapA, bitmapB)
                                    bitmapA.recycle()
                                    bitmapB.recycle()
                                    onProgress((index + 1f) / comparedPages.coerceAtLeast(1))
                                }

                                PdfComparisonResult(pageCountA, pageCountB, diffs)
                            }
                        }
                    }
                }
            } finally {
                firstCache.delete()
                secondCache.delete()
            }
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open $uri")
        input.use { inStream -> destination.outputStream().use { out -> inStream.copyTo(out) } }
    }

    private fun renderPageToBitmap(renderer: PdfRenderer, index: Int, targetWidthPx: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val scale = targetWidthPx.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(
                targetWidthPx,
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    /** Coarse grid diff (not per-pixel/connected-components) - cheap enough to
     * run on Dispatchers.IO for every page pair, and precise enough to point
     * the user at roughly where two pages differ. */
    private fun diffPages(pageIndex: Int, bitmapA: Bitmap, bitmapB: Bitmap): PageDiff {
        val width = bitmapA.width
        val height = bitmapA.height
        val normalizedB = if (bitmapB.width != width || bitmapB.height != height) {
            Bitmap.createScaledBitmap(bitmapB, width, height, true)
        } else {
            bitmapB
        }

        val pixelsA = IntArray(width * height)
        val pixelsB = IntArray(width * height)
        bitmapA.getPixels(pixelsA, 0, width, 0, 0, width, height)
        normalizedB.getPixels(pixelsB, 0, width, 0, 0, width, height)

        val cellWidth = (width / COMPARE_GRID_COLS).coerceAtLeast(1)
        val cellHeight = (height / COMPARE_GRID_ROWS).coerceAtLeast(1)
        var totalDiffPixels = 0
        val regions = mutableListOf<RectF>()

        for (row in 0 until COMPARE_GRID_ROWS) {
            val startY = row * cellHeight
            if (startY >= height) break
            val endY = if (row == COMPARE_GRID_ROWS - 1) height else (startY + cellHeight).coerceAtMost(height)

            for (col in 0 until COMPARE_GRID_COLS) {
                val startX = col * cellWidth
                if (startX >= width) break
                val endX = if (col == COMPARE_GRID_COLS - 1) width else (startX + cellWidth).coerceAtMost(width)

                var diffCount = 0
                var cellPixelCount = 0
                for (py in startY until endY) {
                    for (px in startX until endX) {
                        val idx = py * width + px
                        val a = pixelsA[idx]
                        val b = pixelsB[idx]
                        cellPixelCount++
                        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
                        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
                        val db = abs((a and 0xFF) - (b and 0xFF))
                        if (dr + dg + db > PIXEL_DIFF_THRESHOLD) diffCount++
                    }
                }
                totalDiffPixels += diffCount
                if (cellPixelCount > 0 && diffCount.toFloat() / cellPixelCount > CELL_DIFF_THRESHOLD) {
                    regions += RectF(
                        startX.toFloat() / width,
                        startY.toFloat() / height,
                        endX.toFloat() / width,
                        endY.toFloat() / height
                    )
                }
            }
        }

        if (normalizedB !== bitmapB) normalizedB.recycle()

        val diffPercentage = totalDiffPixels.toFloat() / (width * height).coerceAtLeast(1)
        return PageDiff(
            pageIndex = pageIndex,
            hasDifference = regions.isNotEmpty(),
            diffRegions = regions,
            diffPercentage = diffPercentage
        )
    }

    private inline fun writeTo(uri: Uri, write: (OutputStream) -> Unit) {
        val out = context.contentResolver.openOutputStream(uri) ?: error("Could not open output stream")
        out.use(write)
    }

    private companion object {
        const val WATERMARK_META_KEY = "PdfMasterWatermark"
        const val WATERMARK_META_VALUE = "1"
        const val IMAGE_WATERMARK_WIDTH_FRACTION = 0.35f
        const val DEFAULT_IMAGE_DPI = 200f
        const val SIGNATURE_META_KEY = "PdfMasterSignedDate"
        const val COMPARE_WIDTH_PX = 300
        const val COMPARE_GRID_COLS = 12
        const val COMPARE_GRID_ROWS = 16
        const val PIXEL_DIFF_THRESHOLD = 60
        const val CELL_DIFF_THRESHOLD = 0.08f
        val SIGNATURE_DATE_FORMAT = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        val ROMAN_VALUES = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
    }
}
