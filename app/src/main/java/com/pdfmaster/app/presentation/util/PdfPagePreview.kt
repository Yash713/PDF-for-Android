package com.pdfmaster.app.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RenderedPage(
    val bitmap: Bitmap,
    val pageIndex: Int,
    val pageCount: Int,
    /** The PDF page's own MediaBox size in points (1/72in) - PdfRenderer.Page
     * reports width/height in points already, which is exactly what PdfEngine's
     * PDFBox-side coordinates use, so this is the scale factor callers need to
     * convert a tap/drag on [bitmap] into PDF point-space. */
    val pageWidthPt: Float,
    val pageHeightPt: Float
)

/**
 * Rasterizes one page of a PDF for on-screen preview (watermark preview, Cover
 * tool). PdfRenderer needs a seekable local file descriptor, which a SAF content
 * Uri usually isn't, so the source is copied into cache first.
 */
object PdfPagePreview {

    suspend fun render(context: Context, uri: Uri, pageIndex: Int, targetWidthPx: Int): RenderedPage? =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "preview_${System.nanoTime()}.pdf")
            try {
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied) return@withContext null

                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                        renderer.openPage(pageIndex).use { page ->
                            val scale = targetWidthPx.toFloat() / page.width
                            val bitmap = Bitmap.createBitmap(
                                targetWidthPx,
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            RenderedPage(
                                bitmap = bitmap,
                                pageIndex = pageIndex,
                                pageCount = renderer.pageCount,
                                pageWidthPt = page.width.toFloat(),
                                pageHeightPt = page.height.toFloat()
                            )
                        }
                    }
                }
            } finally {
                cacheFile.delete()
            }
        }

    /** Renders every page at a small [thumbnailWidthPx] - used by the Organize
     * screen's reorder list. Kept low-res deliberately: a 200px-wide bitmap per
     * page is cheap even for a hundred-page document, a full-res render per page
     * would not be. */
    suspend fun renderAllThumbnails(context: Context, uri: Uri, thumbnailWidthPx: Int = 200): List<Bitmap> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "thumbs_${System.nanoTime()}.pdf")
            try {
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied) return@withContext emptyList()

                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        (0 until renderer.pageCount).map { index ->
                            renderer.openPage(index).use { page ->
                                val scale = thumbnailWidthPx.toFloat() / page.width
                                val bitmap = Bitmap.createBitmap(
                                    thumbnailWidthPx,
                                    (page.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }
                        }
                    }
                }
            } finally {
                cacheFile.delete()
            }
        }
}
