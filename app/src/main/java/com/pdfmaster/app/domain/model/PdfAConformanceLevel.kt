package com.pdfmaster.app.domain.model

/**
 * Only the "b" (visual/basic) levels are offered - true PDF/A-1a/2a/3a
 * conformance additionally requires a full tagged-PDF accessibility structure
 * tree, which [com.pdfmaster.app.domain.repository.PdfEngine.pdfToPdfA] doesn't
 * build. See that method's doc comment for exactly what this does and doesn't verify.
 */
enum class PdfAConformanceLevel(val part: Int, val conformance: String, val label: String) {
    PDF_A_1B(1, "B", "PDF/A-1b"),
    PDF_A_2B(2, "B", "PDF/A-2b"),
    PDF_A_3B(3, "B", "PDF/A-3b")
}
