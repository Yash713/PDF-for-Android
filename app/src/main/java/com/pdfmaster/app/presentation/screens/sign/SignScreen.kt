package com.pdfmaster.app.presentation.screens.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog
import com.pdfmaster.app.presentation.util.RenderedPage
import kotlin.math.roundToInt

private enum class SignatureSource { DRAW, UPLOAD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignScreen(onBack: () -> Unit, viewModel: SignViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val signatureBitmap by viewModel.signatureBitmap.collectAsStateWithLifecycle()
    val placement by viewModel.placement.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var source by remember { mutableStateOf(SignatureSource.DRAW) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream)
            viewModel.setSignatureBitmap(bitmap)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::apply) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("PDF signed")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign PDF") },
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
                text = "Your signature",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = source == SignatureSource.DRAW,
                    onClick = { source = SignatureSource.DRAW },
                    label = { Text("Draw Signature") }
                )
                FilterChip(
                    selected = source == SignatureSource.UPLOAD,
                    onClick = { source = SignatureSource.UPLOAD },
                    label = { Text("Upload Image") }
                )
            }

            if (source == SignatureSource.DRAW) {
                SignaturePad(
                    modifier = Modifier.padding(top = 12.dp),
                    onDone = { bitmap -> viewModel.setSignatureBitmap(bitmap) }
                )
            } else {
                Button(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Choose Image")
                }
            }

            signatureBitmap?.let { bitmap ->
                Text("Signature ready:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Signature preview",
                    modifier = Modifier
                        .height(60.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(4.dp)
                )
            }

            currentPage?.let { rendered ->
                Text(
                    text = "Tap the page to place your signature, then drag to reposition:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    val density = LocalDensity.current
                    val canvasWidthPx = with(density) { maxWidth.toPx() }
                    val canvasHeightPx = canvasWidthPx * (rendered.pageHeightPt / rendered.pageWidthPt)
                    val canvasHeightDp = with(density) { canvasHeightPx.toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(canvasHeightDp)
                            .pointerInput(rendered.pageIndex, signatureBitmap) {
                                detectTapGestures { offset ->
                                    val pdfPoint = pixelToPdf(offset, rendered, canvasWidthPx, canvasHeightPx)
                                    viewModel.placeAt(pdfPoint.x, pdfPoint.y)
                                }
                            }
                    ) {
                        Image(
                            bitmap = rendered.bitmap.asImageBitmap(),
                            contentDescription = "Page ${rendered.pageIndex + 1}",
                            modifier = Modifier.fillMaxSize()
                        )

                        placement?.let { place ->
                            SignatureOverlay(
                                key = rendered.pageIndex,
                                bitmap = signatureBitmap,
                                placement = place,
                                rendered = rendered,
                                canvasWidthPx = canvasWidthPx,
                                canvasHeightPx = canvasHeightPx,
                                onMove = viewModel::movePlacement
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
            }

            Button(
                onClick = { createDocumentLauncher.launch("signed_document.pdf") },
                enabled = selectedFile != null && signatureBitmap != null && placement != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Apply")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Signing...", progress = loading.progress)
    }
}

@Composable
private fun SignaturePad(modifier: Modifier = Modifier, onDone: (Bitmap) -> Unit) {
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .onGloballyPositioned { coordinates -> padSize = coordinates.size }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke.clear()
                            currentStroke.add(offset)
                        },
                        onDragEnd = {
                            if (currentStroke.size > 1) completedStrokes.add(currentStroke.toList())
                            currentStroke.clear()
                        },
                        onDragCancel = { currentStroke.clear() },
                        onDrag = { change, _ ->
                            change.consume()
                            currentStroke.add(change.position)
                        }
                    )
                }
        ) {
            (completedStrokes + listOf(currentStroke.toList())).forEach { stroke ->
                if (stroke.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(stroke.first().x, stroke.first().y)
                    stroke.drop(1).forEach { point -> lineTo(point.x, point.y) }
                }
                drawPath(path, color = Color.Black, style = Stroke(width = 6f))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            OutlinedButton(onClick = {
                completedStrokes.clear()
                currentStroke.clear()
            }) {
                Text("Clear")
            }
            Button(
                onClick = {
                    onDone(renderStrokesToBitmap(completedStrokes.toList(), padSize.width, padSize.height))
                },
                enabled = completedStrokes.isNotEmpty()
            ) {
                Text("Done")
            }
        }
    }
}

private fun renderStrokesToBitmap(strokes: List<List<Offset>>, widthPx: Int, heightPx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = android.graphics.Path()
        path.moveTo(stroke.first().x, stroke.first().y)
        stroke.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

@Composable
private fun SignatureOverlay(
    key: Int,
    bitmap: Bitmap?,
    placement: SignaturePlacement,
    rendered: RenderedPage,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onMove: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val latestPlacement by rememberUpdatedState(placement)

    val topLeftPx = pdfToPixel(placement.x, placement.y + placement.height, rendered, canvasWidthPx, canvasHeightPx)
    val widthPx = pdfLenToPxX(placement.width, rendered, canvasWidthPx)
    val heightPx = pdfLenToPxY(placement.height, rendered, canvasHeightPx)

    Box(
        modifier = Modifier
            .offset { IntOffset(topLeftPx.x.roundToInt(), topLeftPx.y.roundToInt()) }
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { heightPx.toDp() }
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            .pointerInput(key) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val ptPerPxX = rendered.pageWidthPt / canvasWidthPx
                    val ptPerPxY = rendered.pageHeightPt / canvasHeightPx
                    val dxPdf = dragAmount.x * ptPerPxX
                    val dyPdf = -dragAmount.y * ptPerPxY
                    onMove(latestPlacement.x + dxPdf, latestPlacement.y + dyPdf)
                }
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Signature",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun pixelToPdf(offset: Offset, rendered: RenderedPage, canvasWidthPx: Float, canvasHeightPx: Float): Offset {
    val ptPerPxX = rendered.pageWidthPt / canvasWidthPx
    val ptPerPxY = rendered.pageHeightPt / canvasHeightPx
    return Offset(offset.x * ptPerPxX, rendered.pageHeightPt - offset.y * ptPerPxY)
}

private fun pdfToPixel(x: Float, y: Float, rendered: RenderedPage, canvasWidthPx: Float, canvasHeightPx: Float): Offset {
    val pxPerPtX = canvasWidthPx / rendered.pageWidthPt
    val pxPerPtY = canvasHeightPx / rendered.pageHeightPt
    return Offset(x * pxPerPtX, (rendered.pageHeightPt - y) * pxPerPtY)
}

private fun pdfLenToPxX(length: Float, rendered: RenderedPage, canvasWidthPx: Float) =
    length * (canvasWidthPx / rendered.pageWidthPt)

private fun pdfLenToPxY(length: Float, rendered: RenderedPage, canvasHeightPx: Float) =
    length * (canvasHeightPx / rendered.pageHeightPt)
