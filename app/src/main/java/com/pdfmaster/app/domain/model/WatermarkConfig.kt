package com.pdfmaster.app.domain.model

import android.graphics.Color
import android.graphics.RectF
import android.net.Uri

enum class WatermarkType { TEXT, IMAGE }
enum class WatermarkPosition { CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Entirely user-defined watermark content - PdfEngineImpl never draws any app
 * branding of its own, only what's set here. For [WatermarkType.TEXT], [text]
 * must be non-blank; for [WatermarkType.IMAGE], [imageUri] must be set -
 * PdfEngineImpl validates this rather than the type system, since a single flat
 * shape (vs. a sealed class per type) is what this screen's form binds to directly.
 */
data class WatermarkConfig(
    val type: WatermarkType,
    val text: String? = null,
    val imageUri: Uri? = null,
    val textColor: Int = Color.GRAY,
    val fontSize: Float = 36f,
    val opacity: Float = 0.5f,
    val rotation: Float = 0f,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val tile: Boolean = false
)

/**
 * Best-effort heuristic hit from [com.pdfmaster.app.domain.repository.PdfEngine.detectWatermarks] -
 * [bounds] is a union of low-opacity text runs found on the page (PDF point-space,
 * bottom-left origin). Detects semi-transparent text overlays; does not currently
 * detect image-based watermarks (that needs content-stream image-placement
 * tracking, which is a larger lift than this optional feature warrants yet).
 */
data class DetectedWatermark(val pageIndex: Int, val bounds: RectF, val type: String)
