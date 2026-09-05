package com.pdfmaster.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

/** One entry per concrete tool. The six spec categories (Organize/Optimize/Convert/
 * Edit/Security/Intelligence) aren't routes of their own - they're section headers
 * on [HomeScreen][com.pdfmaster.app.presentation.screens.home.HomeScreen] that group
 * these tools, since a "category" isn't something a user taps into on its own.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "PDF Master", Icons.Filled.PictureAsPdf)

    // Organize PDF
    data object Merge : Screen("merge", "Merge", Icons.Filled.CallMerge)
    data object Split : Screen("split", "Split", Icons.Filled.CallSplit)
    data object OrganizePages : Screen("organize_pages", "Organize Pages", Icons.Filled.Reorder)

    // Optimize PDF
    data object Compress : Screen("compress", "Compress", Icons.Filled.Compress)
    data object Repair : Screen("repair", "Repair", Icons.Filled.Build)
    data object PdfA : Screen("pdf_a", "PDF/A", Icons.Filled.VerifiedUser)

    // Convert PDF
    data object PdfToWord : Screen("pdf_to_word", "PDF to Word", Icons.Filled.Description)
    data object PdfToPpt : Screen("pdf_to_ppt", "PDF to PowerPoint", Icons.Filled.Slideshow)
    data object PdfToExcel : Screen("pdf_to_excel", "PDF to Excel", Icons.Filled.TableChart)
    data object WordToPdf : Screen("word_to_pdf", "Word to PDF", Icons.Filled.PictureAsPdf)
    data object PptToPdf : Screen("ppt_to_pdf", "PowerPoint to PDF", Icons.Filled.PictureAsPdf)
    data object ExcelToPdf : Screen("excel_to_pdf", "Excel to PDF", Icons.Filled.PictureAsPdf)
    data object HtmlToPdf : Screen("html_to_pdf", "HTML to PDF", Icons.Filled.Html)
    data object PdfToJpg : Screen("pdf_to_jpg", "PDF to JPG", Icons.Filled.Image)
    data object JpgToPdf : Screen("jpg_to_pdf", "JPG to PDF", Icons.Filled.Image)

    // Edit PDF
    data object Edit : Screen("edit", "Edit PDF", Icons.Filled.Edit)
    data object Watermark : Screen("watermark", "Watermark", Icons.Filled.Water)
    data object Rotate : Screen("rotate", "Rotate", Icons.Filled.RotateRight)
    data object PageNumbers : Screen("page_numbers", "Page Numbers", Icons.Filled.FormatListNumbered)
    data object Sign : Screen("sign", "Sign", Icons.Filled.Draw)

    // PDF Security
    data object Protect : Screen("protect", "Protect", Icons.Filled.Lock)
    data object Unlock : Screen("unlock", "Unlock", Icons.Filled.LockOpen)

    // PDF Intelligence
    data object ScanOcr : Screen("scan_ocr", "Scan to PDF / OCR", Icons.Filled.DocumentScanner)
    data object Compare : Screen("compare", "Compare", Icons.Filled.Compare)
}

data class FeatureSection(val title: String, val features: List<Screen>)

val homeSections = listOf(
    FeatureSection("Organize PDF", listOf(Screen.Merge, Screen.Split, Screen.OrganizePages)),
    FeatureSection("Optimize PDF", listOf(Screen.Compress, Screen.Repair, Screen.PdfA)),
    FeatureSection(
        "Convert PDF",
        listOf(
            Screen.PdfToWord, Screen.PdfToPpt, Screen.PdfToExcel,
            Screen.WordToPdf, Screen.PptToPdf, Screen.ExcelToPdf,
            Screen.HtmlToPdf, Screen.PdfToJpg, Screen.JpgToPdf
        )
    ),
    FeatureSection("Edit PDF", listOf(Screen.Edit, Screen.Watermark, Screen.Rotate, Screen.PageNumbers, Screen.Sign)),
    FeatureSection("PDF Security", listOf(Screen.Protect, Screen.Unlock)),
    FeatureSection("PDF Intelligence", listOf(Screen.ScanOcr, Screen.Compare))
)
