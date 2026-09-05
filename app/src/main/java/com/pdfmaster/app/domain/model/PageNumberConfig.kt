package com.pdfmaster.app.domain.model

enum class PageNumberPosition { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
enum class PageNumberFormat { NUMERIC, ROMAN_LOWER, ROMAN_UPPER }

data class PageNumberConfig(
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val fontSize: Float = 12f,
    val startNumber: Int = 1,
    val format: PageNumberFormat = PageNumberFormat.NUMERIC
)
