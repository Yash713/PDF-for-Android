package com.pdfmaster.app.domain.model

import android.graphics.Color
import android.net.Uri

enum class ShapeType { RECTANGLE, CIRCLE, LINE }

/**
 * One user-added overlay element. Coordinates/sizes are all in PDF point-space
 * (origin bottom-left, matching the target page's MediaBox) - the screen that
 * builds these is responsible for converting from on-screen tap/drag
 * coordinates, the same convention used by PdfEngine.coverWatermark.
 *
 * V1 scope: these are new layers drawn on top of the page, not edits to
 * existing page content - there's no selection/modification of what's already
 * on the page.
 */
sealed class EditElement {
    abstract val pageIndex: Int
    abstract val x: Float
    abstract val y: Float

    data class TextElement(
        override val pageIndex: Int,
        override val x: Float,
        override val y: Float,
        val text: String,
        val fontSize: Float = 18f,
        val colorArgb: Int = Color.BLACK
    ) : EditElement()

    data class ImageElement(
        override val pageIndex: Int,
        override val x: Float,
        override val y: Float,
        val imageUri: Uri,
        val width: Float,
        val height: Float
    ) : EditElement()

    /** For [ShapeType.LINE], [width]/[height] are the delta to the line's end
     * point (x+width, y+height) rather than a bounding box. */
    data class ShapeElement(
        override val pageIndex: Int,
        override val x: Float,
        override val y: Float,
        val shapeType: ShapeType,
        val width: Float,
        val height: Float,
        val colorArgb: Int = Color.RED,
        val strokeWidthPt: Float = 2f,
        val filled: Boolean = false
    ) : EditElement()
}
