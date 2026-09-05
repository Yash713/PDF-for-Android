package com.pdfmaster.app.domain.model

/**
 * imageQuality feeds Bitmap.compress()'s JPEG quality (0-100 scale internally);
 * imageScale downsamples embedded images before re-encoding - most of a scanned
 * PDF's size is oversized images, not text, so scaling first is what actually
 * shrinks the file.
 */
enum class CompressQuality(val label: String, val imageQuality: Int, val imageScale: Float) {
    LOW(label = "Low (smallest file)", imageQuality = 35, imageScale = 0.5f),
    MEDIUM(label = "Medium (balanced)", imageQuality = 60, imageScale = 0.75f),
    HIGH(label = "High (best quality)", imageQuality = 85, imageScale = 1.0f)
}
