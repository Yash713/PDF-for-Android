package com.pdfmaster.app.domain.model

/**
 * [recoveredPages] can be less than [totalPages] when PDFBox's parser could load
 * the document overall but a specific page's content couldn't be imported into
 * the rebuilt file - that page is dropped rather than left corrupt.
 */
data class RepairOutcome(val totalPages: Int, val recoveredPages: Int) {
    val isFullyRecovered: Boolean get() = recoveredPages == totalPages
}
