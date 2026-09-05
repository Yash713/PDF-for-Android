package com.pdfmaster.app.domain.repository

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.pdfmaster.app.domain.model.CompressQuality
import com.pdfmaster.app.domain.model.DetectedWatermark
import com.pdfmaster.app.domain.model.EditElement
import com.pdfmaster.app.domain.model.PageNumberConfig
import com.pdfmaster.app.domain.model.PdfAConformanceLevel
import com.pdfmaster.app.domain.model.PdfComparisonResult
import com.pdfmaster.app.domain.model.RepairOutcome
import com.pdfmaster.app.domain.model.WatermarkConfig

/**
 * Core PDF manipulation engine. All methods are suspend + Result-returning so
 * callers get a single terminal outcome; live progress (0f..1f) is reported
 * through the onProgress callback instead of a second return channel, since a
 * suspend fun can't hand back both a Flow<Float> and a terminal Result<T>
 * without wrapping one of them - a callback is the simplest option that still
 * lets a ViewModel forward ticks into its own StateFlow<Float> for the UI.
 */
interface PdfEngine {

    /** Cheap metadata read (no page content is touched) - used to validate page-range input. */
    suspend fun getPageCount(inputUri: Uri): Result<Int>

    suspend fun mergePdfs(
        inputUris: List<Uri>,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    suspend fun splitPdf(
        inputUri: Uri,
        pageRanges: List<IntRange>,
        outputDir: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<List<Uri>>

    suspend fun compressPdf(
        inputUri: Uri,
        outputUri: Uri,
        quality: CompressQuality,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    suspend fun protectPdf(
        inputUri: Uri,
        outputUri: Uri,
        password: String
    ): Result<Unit>

    suspend fun unlockPdf(
        inputUri: Uri,
        outputUri: Uri,
        password: String
    ): Result<Unit>

    /**
     * Draws [config] onto every page, entirely as specified by the user - no
     * app branding is ever added. Tags the output (document Info metadata +
     * exactly one appended content stream per page) so [removeOwnWatermark]
     * can find and strip it again later without touching pre-existing content.
     */
    suspend fun addWatermark(
        inputUri: Uri,
        outputUri: Uri,
        config: WatermarkConfig,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Strips a watermark previously added by [addWatermark] - fails with a
     * clear message if [inputUri] isn't tagged as one of ours (use
     * [coverWatermark] instead for watermarks from another source).
     */
    suspend fun removeOwnWatermark(
        inputUri: Uri,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Manual redaction tool for watermarks this app didn't add: paints opaque
     * white rectangles over the given regions. [pages] maps a 0-indexed page
     * number to the rectangles to cover on that page, in PDF point-space
     * (origin bottom-left, matching that page's MediaBox) - converting from
     * on-screen preview coordinates into that space is the caller's job.
     */
    suspend fun coverWatermark(
        inputUri: Uri,
        outputUri: Uri,
        pages: Map<Int, List<RectF>>,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Best-effort heuristic scan for existing watermark-like overlays (see
     * [DetectedWatermark] for what this can and can't find). Optional/advisory -
     * used to point the user at the Cover tool, not a guarantee of detection.
     */
    suspend fun detectWatermarks(
        inputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<List<DetectedWatermark>>

    /**
     * Rotates pages clockwise by [angle] (must be 90, 180, or 270). [pageIndices]
     * of null means every page; otherwise only those 0-indexed pages are rotated
     * and the rest are left as-is.
     */
    suspend fun rotatePdf(
        inputUri: Uri,
        outputUri: Uri,
        angle: Int,
        pageIndices: List<Int>? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Rebuilds the document from [pageOrder] - a list of 0-indexed source page
     * numbers in the desired final order. Any source page not present in the
     * list is dropped, so this covers both reordering and deletion in one call.
     */
    suspend fun organizePdf(
        inputUri: Uri,
        outputUri: Uri,
        pageOrder: List<Int>,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /** Draws a page-number label (per [config]) onto every page. */
    suspend fun addPageNumbers(
        inputUri: Uri,
        outputUri: Uri,
        config: PageNumberConfig,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /** Rasterizes every page to a JPEG at [dpi] into [outputDir], returning the created files. */
    suspend fun pdfToJpg(
        inputUri: Uri,
        outputDir: Uri,
        dpi: Int = 150,
        quality: Int = 90,
        onProgress: (Float) -> Unit = {}
    ): Result<List<Uri>>

    /** Builds a new PDF with one full-bleed page per image, in [imageUris] order. */
    suspend fun jpgToPdf(
        imageUris: List<Uri>,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Builds a single-page PDF sized to [bitmap]'s aspect ratio - used by
     * HTML to PDF, which captures a rendered WebView into one bitmap
     * (see presentation/util/HtmlToPdfPrinter.kt) rather than driving
     * android.print.PrintDocumentAdapter directly: its LayoutResultCallback/
     * WriteResultCallback have package-private constructors in the compiled
     * SDK, so only the print framework itself can construct them - an app
     * can't call adapter.onLayout()/onWrite() on its own.
     */
    suspend fun htmlBitmapToPdf(
        bitmap: Bitmap,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Best-effort PDF/A compatibility pass - NOT a validator-certified
     * conversion. Removes encryption and any /OpenAction + document-level
     * JavaScript (both disallowed in PDF/A), then writes PDF/A identification
     * XMP metadata for [level]. Genuine PDF/A conformance additionally requires
     * full font embedding and a device-independent ICC output intent (and, for
     * the "a" levels, a tagged accessibility structure) - none of which this
     * checks or adds, which is exactly why only the "b" levels are offered.
     */
    suspend fun pdfToPdfA(
        inputUri: Uri,
        outputUri: Uri,
        level: PdfAConformanceLevel,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Opens [inputUri] (PDFBox's parser already recovers from common xref/
     * trailer corruption on its own) and rebuilds it page by page, dropping any
     * individual page that can't be imported rather than failing the whole
     * file. Fails outright only if the file can't be parsed at all.
     */
    suspend fun repairPdf(
        inputUri: Uri,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<RepairOutcome>

    /**
     * Draws [elements] onto [inputUri] as new layers (each appended to its
     * page's content stream) - V1 only adds content, it doesn't let the user
     * select or modify what's already on the page.
     */
    suspend fun editPdf(
        inputUri: Uri,
        outputUri: Uri,
        elements: List<EditElement>,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Places [signatureBitmap] (a drawn or uploaded signature image) onto
     * [pageIndex] at the given PDF point-space rectangle. This is a visible
     * image overlay, not a cryptographically verifiable digital signature.
     */
    suspend fun signPdf(
        inputUri: Uri,
        outputUri: Uri,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Renders matching pages of both files at a small fixed resolution and
     * diffs them on a coarse grid, reporting which cells differ per page - a
     * lightweight visual-difference check, not a semantic/text diff.
     */
    suspend fun comparePdf(
        firstUri: Uri,
        secondUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<PdfComparisonResult>
}
