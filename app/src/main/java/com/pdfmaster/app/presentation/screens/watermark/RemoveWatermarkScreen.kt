package com.pdfmaster.app.presentation.screens.watermark

import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog
import com.pdfmaster.app.presentation.util.RenderedPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveWatermarkScreen(onBack: () -> Unit, viewModel: RemoveWatermarkViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val coverRects by viewModel.coverRects.collectAsStateWithLifecycle()
    val detected by viewModel.detected.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val autoRemoveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::removeOwnWatermark) }

    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::applyCover) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("Saved")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remove Watermark") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                Text("Select PDF")
            }

            Text(
                text = "If PDF Master added the watermark, this removes it instantly:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Button(
                onClick = { autoRemoveLauncher.launch("unwatermarked_document.pdf") },
                enabled = selectedFile != null
            ) {
                Text("Remove Watermark (Auto)")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text("Cover / Redact Manually", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "For watermarks from another source: drag a box over each one to paint it white.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (detected.isNotEmpty()) {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "Possible watermark detected on page(s): " +
                            detected.map { it.pageIndex + 1 }.distinct().joinToString(", "),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            currentPage?.let { rendered ->
                val rectsOnPage = coverRects[rendered.pageIndex].orEmpty()
                var dragStart by remember(rendered.pageIndex) { mutableStateOf<Offset?>(null) }
                var dragCurrent by remember(rendered.pageIndex) { mutableStateOf<Offset?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(rendered.pageWidthPt / rendered.pageHeightPt)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .pointerInput(rendered.pageIndex) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    dragCurrent = offset
                                },
                                onDrag = { change, _ ->
                                    dragCurrent = change.position
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val end = dragCurrent
                                    if (start != null && end != null) {
                                        val pixelRect = RectF(
                                            minOf(start.x, end.x), minOf(start.y, end.y),
                                            maxOf(start.x, end.x), maxOf(start.y, end.y)
                                        )
                                        if (pixelRect.width() > 4f && pixelRect.height() > 4f) {
                                            viewModel.addRect(
                                                pixelRectToPdfRect(pixelRect, rendered, size.width.toFloat(), size.height.toFloat())
                                            )
                                        }
                                    }
                                    dragStart = null
                                    dragCurrent = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragCurrent = null
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = rendered.bitmap.asImageBitmap(),
                        contentDescription = "Page ${rendered.pageIndex + 1}",
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rectsOnPage.forEach { pdfRect ->
                            val pixelRect = pdfRectToPixelRect(pdfRect, rendered, size.width, size.height)
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(pixelRect.left, pixelRect.top),
                                size = androidx.compose.ui.geometry.Size(pixelRect.width(), pixelRect.height())
                            )
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(pixelRect.left, pixelRect.top),
                                size = androidx.compose.ui.geometry.Size(pixelRect.width(), pixelRect.height()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                        }
                        val start = dragStart
                        val end = dragCurrent
                        if (start != null && end != null) {
                            drawRect(
                                color = Color.Red.copy(alpha = 0.3f),
                                topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                                size = androidx.compose.ui.geometry.Size(
                                    kotlin.math.abs(end.x - start.x),
                                    kotlin.math.abs(end.y - start.y)
                                )
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedButton(onClick = { viewModel.goToPage(-1) }, enabled = rendered.pageIndex > 0) {
                        Text("Previous")
                    }
                    Text(
                        text = "Page ${rendered.pageIndex + 1} of ${rendered.pageCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { viewModel.goToPage(1) },
                        enabled = rendered.pageIndex < rendered.pageCount - 1
                    ) {
                        Text("Next")
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::undoLastRectOnCurrentPage,
                        enabled = rectsOnPage.isNotEmpty()
                    ) {
                        Text("Undo")
                    }
                    OutlinedButton(
                        onClick = viewModel::clearRectsOnCurrentPage,
                        enabled = rectsOnPage.isNotEmpty()
                    ) {
                        Text("Clear Page")
                    }
                }

                Button(
                    onClick = { coverLauncher.launch("redacted_document.pdf") },
                    enabled = coverRects.values.any { it.isNotEmpty() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Apply Cover")
                }
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Working...", progress = loading.progress)
    }
}

/** rendered's bitmap fills a box sized targetWidthPx x (targetWidthPx*aspect), but the
 * Compose layout box (and therefore drag/canvas coordinates) may render at a different
 * pixel size than the source bitmap - always convert using the Canvas's own [canvasWidth]/
 * [canvasHeight], not rendered.bitmap.width/height. */
private fun pixelRectToPdfRect(pixelRect: RectF, rendered: RenderedPage, canvasWidth: Float, canvasHeight: Float): RectF {
    val ptPerPxX = rendered.pageWidthPt / canvasWidth
    val ptPerPxY = rendered.pageHeightPt / canvasHeight
    val left = pixelRect.left * ptPerPxX
    val right = pixelRect.right * ptPerPxX
    // Canvas y is top-down; PDF y is bottom-up.
    val top = rendered.pageHeightPt - (pixelRect.top * ptPerPxY)
    val bottom = rendered.pageHeightPt - (pixelRect.bottom * ptPerPxY)
    return RectF(left, top, right, bottom)
}

private fun pdfRectToPixelRect(pdfRect: RectF, rendered: RenderedPage, canvasWidth: Float, canvasHeight: Float): RectF {
    val pxPerPtX = canvasWidth / rendered.pageWidthPt
    val pxPerPtY = canvasHeight / rendered.pageHeightPt
    val left = pdfRect.left * pxPerPtX
    val right = pdfRect.right * pxPerPtX
    val top = (rendered.pageHeightPt - pdfRect.top) * pxPerPtY
    val bottom = (rendered.pageHeightPt - pdfRect.bottom) * pxPerPtY
    return RectF(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
}
