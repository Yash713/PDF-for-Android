package com.pdfmaster.app.domain.model

import android.graphics.RectF

/**
 * [diffRegions] are normalized to [0,1] against whatever resolution the pages
 * were rendered at for comparison, so the UI can scale them onto a preview
 * bitmap of any size rather than being tied to the diffing resolution.
 */
data class PageDiff(
    val pageIndex: Int,
    val hasDifference: Boolean,
    val diffRegions: List<RectF>,
    val diffPercentage: Float
)

data class PdfComparisonResult(
    val pageCountFirst: Int,
    val pageCountSecond: Int,
    val pageDiffs: List<PageDiff>
) {
    val pageCountsMatch: Boolean get() = pageCountFirst == pageCountSecond
    val comparedPageCount: Int get() = pageDiffs.size
}
