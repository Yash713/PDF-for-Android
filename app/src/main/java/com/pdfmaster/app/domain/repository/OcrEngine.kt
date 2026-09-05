package com.pdfmaster.app.domain.repository

import android.graphics.Bitmap
import android.net.Uri

interface OcrEngine {

    /** Runs OCR over [bitmap] and returns the recognized plain text. */
    suspend fun extractText(bitmap: Bitmap, onProgress: (Float) -> Unit = {}): Result<String>

    /**
     * Generates a single-page, searchable PDF at [outputUri]: [bitmap] rendered
     * as the visible page image with the OCR'd words drawn invisibly on top,
     * aligned to their detected positions.
     */
    suspend fun createSearchablePdf(
        bitmap: Bitmap,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>
}
