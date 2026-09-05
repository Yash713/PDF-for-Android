package com.pdfmaster.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfmaster.app.data.remote.OfficeFormat
import com.pdfmaster.app.presentation.screens.compare.CompareScreen
import com.pdfmaster.app.presentation.screens.compress.CompressScreen
import com.pdfmaster.app.presentation.screens.convert.ConvertScreen
import com.pdfmaster.app.presentation.screens.edit.EditScreen
import com.pdfmaster.app.presentation.screens.excelToPdf.ExcelToPdfScreen
import com.pdfmaster.app.presentation.screens.home.HomeScreen
import com.pdfmaster.app.presentation.screens.htmlToPdf.HtmlToPdfScreen
import com.pdfmaster.app.presentation.screens.jpgToPdf.JpgToPdfScreen
import com.pdfmaster.app.presentation.screens.merge.MergeScreen
import com.pdfmaster.app.presentation.screens.ocr.OcrScreen
import com.pdfmaster.app.presentation.screens.organize.OrganizeScreen
import com.pdfmaster.app.presentation.screens.pageNumbers.PageNumbersScreen
import com.pdfmaster.app.presentation.screens.pdfToJpg.PdfToJpgScreen
import com.pdfmaster.app.presentation.screens.pdfToPdfA.PdfToPdfAScreen
import com.pdfmaster.app.presentation.screens.powerPointToPdf.PowerPointToPdfScreen
import com.pdfmaster.app.presentation.screens.protect.ProtectScreen
import com.pdfmaster.app.presentation.screens.repair.RepairScreen
import com.pdfmaster.app.presentation.screens.rotate.RotateScreen
import com.pdfmaster.app.presentation.screens.sign.SignScreen
import com.pdfmaster.app.presentation.screens.split.SplitScreen
import com.pdfmaster.app.presentation.screens.unlock.UnlockScreen
import com.pdfmaster.app.presentation.screens.watermark.WatermarkScreen
import com.pdfmaster.app.presentation.screens.wordToPdf.WordToPdfScreen

@Composable
fun PdfMasterNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(onFeatureClick = { screen -> navController.navigate(screen.route) })
        }

        composable(Screen.Merge.route) {
            MergeScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Compress.route) {
            CompressScreen(onBack = navController::popBackStack)
        }

        composable(Screen.ScanOcr.route) {
            OcrScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Split.route) {
            SplitScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Protect.route) {
            ProtectScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Unlock.route) {
            UnlockScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Watermark.route) {
            WatermarkScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Rotate.route) {
            RotateScreen(onBack = navController::popBackStack)
        }

        composable(Screen.OrganizePages.route) {
            OrganizeScreen(onBack = navController::popBackStack)
        }

        composable(Screen.PageNumbers.route) {
            PageNumbersScreen(onBack = navController::popBackStack)
        }

        composable(Screen.PdfToJpg.route) {
            PdfToJpgScreen(onBack = navController::popBackStack)
        }

        composable(Screen.JpgToPdf.route) {
            JpgToPdfScreen(onBack = navController::popBackStack)
        }

        composable(Screen.PdfToWord.route) {
            ConvertScreen(format = OfficeFormat.WORD, onBack = navController::popBackStack)
        }
        composable(Screen.PdfToPpt.route) {
            ConvertScreen(format = OfficeFormat.POWERPOINT, onBack = navController::popBackStack)
        }
        composable(Screen.PdfToExcel.route) {
            ConvertScreen(format = OfficeFormat.EXCEL, onBack = navController::popBackStack)
        }

        composable(Screen.WordToPdf.route) {
            WordToPdfScreen(onBack = navController::popBackStack)
        }
        composable(Screen.PptToPdf.route) {
            PowerPointToPdfScreen(onBack = navController::popBackStack)
        }
        composable(Screen.ExcelToPdf.route) {
            ExcelToPdfScreen(onBack = navController::popBackStack)
        }

        composable(Screen.PdfA.route) {
            PdfToPdfAScreen(onBack = navController::popBackStack)
        }

        composable(Screen.HtmlToPdf.route) {
            HtmlToPdfScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Repair.route) {
            RepairScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Edit.route) {
            EditScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Sign.route) {
            SignScreen(onBack = navController::popBackStack)
        }

        composable(Screen.Compare.route) {
            CompareScreen(onBack = navController::popBackStack)
        }
    }
}
