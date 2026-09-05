package com.pdfmaster.app.presentation.screens.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pdfmaster.app.domain.model.EditElement
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.model.ShapeType
import com.pdfmaster.app.presentation.components.ProgressDialog
import com.pdfmaster.app.presentation.util.RenderedPage
import kotlin.math.roundToInt

private enum class AddMode { TEXT, IMAGE, SHAPE }

private val textColorSwatches = listOf(
    android.graphics.Color.BLACK,
    android.graphics.Color.RED,
    android.graphics.Color.BLUE,
    android.graphics.Color.rgb(0, 128, 0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(onBack: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val elements by viewModel.elements.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var addMode by remember { mutableStateOf(AddMode.TEXT) }
    var shapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }
    var showTextDialog by remember { mutableStateOf(false) }
    var pendingPosition by remember { mutableStateOf<Offset?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val position = pendingPosition
        val page = currentPage
        if (uri != null && position != null && page != null) {
            viewModel.addElement(
                EditElement.ImageElement(
                    pageIndex = page.pageIndex,
                    x = position.x,
                    y = position.y,
                    imageUri = uri,
                    width = 100f,
                    height = 100f
                )
            )
        }
        pendingPosition = null
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
                snackbarHostState.showSnackbar("Saved")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit PDF") },
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

            currentPage?.let { rendered ->
                val pageElements = elements.withIndex().filter { it.value.pageIndex == rendered.pageIndex }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    val density = LocalDensity.current
                    val canvasWidthPx = with(density) { maxWidth.toPx() }
                    val canvasHeightPx = canvasWidthPx * (rendered.pageHeightPt / rendered.pageWidthPt)
                    val canvasHeightDp = with(density) { canvasHeightPx.toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(width = maxWidth, height = canvasHeightDp)
                            .pointerInputTap(rendered, addMode, shapeType, canvasWidthPx, canvasHeightPx) { pdfPoint ->
                                when (addMode) {
                                    AddMode.TEXT -> {
                                        pendingPosition = pdfPoint
                                        showTextDialog = true
                                    }
                                    AddMode.IMAGE -> {
                                        pendingPosition = pdfPoint
                                        pickImageLauncher.launch("image/*")
                                    }
                                    AddMode.SHAPE -> {
                                        val (w, h) = if (shapeType == ShapeType.LINE) 60f to 0f else 60f to 40f
                                        viewModel.addElement(
                                            EditElement.ShapeElement(
                                                pageIndex = rendered.pageIndex,
                                                x = pdfPoint.x,
                                                y = pdfPoint.y,
                                                shapeType = shapeType,
                                                width = w,
                                                height = h
                                            )
                                        )
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = rendered.bitmap.asImageBitmap(),
                            contentDescription = "Page ${rendered.pageIndex + 1}",
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            pageElements.forEach { (_, element) ->
                                if (element is EditElement.ShapeElement) {
                                    drawShapeElement(element, rendered, canvasWidthPx, canvasHeightPx)
                                }
                            }
                        }

                        pageElements.forEach { (index, element) ->
                            EditElementOverlay(
                                key = index,
                                element = element,
                                rendered = rendered,
                                canvasWidthPx = canvasWidthPx,
                                canvasHeightPx = canvasHeightPx,
                                onMove = { nx, ny -> viewModel.moveElement(index, nx, ny) },
                                onDelete = { viewModel.removeElement(index) }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
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

            Text("Add", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = addMode == AddMode.TEXT, onClick = { addMode = AddMode.TEXT }, label = { Text("Text") })
                FilterChip(selected = addMode == AddMode.IMAGE, onClick = { addMode = AddMode.IMAGE }, label = { Text("Image") })
                FilterChip(selected = addMode == AddMode.SHAPE, onClick = { addMode = AddMode.SHAPE }, label = { Text("Shape") })
            }
            if (addMode == AddMode.SHAPE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    FilterChip(
                        selected = shapeType == ShapeType.RECTANGLE,
                        onClick = { shapeType = ShapeType.RECTANGLE },
                        label = { Text("Rectangle") }
                    )
                    FilterChip(
                        selected = shapeType == ShapeType.CIRCLE,
                        onClick = { shapeType = ShapeType.CIRCLE },
                        label = { Text("Circle") }
                    )
                    FilterChip(
                        selected = shapeType == ShapeType.LINE,
                        onClick = { shapeType = ShapeType.LINE },
                        label = { Text("Line") }
                    )
                }
            }
            Text(
                text = "Tap the page to add. Drag an element to move it, tap the X to remove it.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { createDocumentLauncher.launch("edited_document.pdf") },
                enabled = elements.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Apply")
            }
        }
    }

    if (showTextDialog) {
        TextInputDialog(
            onConfirm = { text, fontSize, colorArgb ->
                val position = pendingPosition
                val page = currentPage
                if (position != null && page != null && text.isNotBlank()) {
                    viewModel.addElement(
                        EditElement.TextElement(
                            pageIndex = page.pageIndex,
                            x = position.x,
                            y = position.y,
                            text = text,
                            fontSize = fontSize,
                            colorArgb = colorArgb
                        )
                    )
                }
                showTextDialog = false
                pendingPosition = null
            },
            onDismiss = {
                showTextDialog = false
                pendingPosition = null
            }
        )
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Saving...", progress = loading.progress)
    }
}

@Composable
private fun TextInputDialog(
    onConfirm: (text: String, fontSize: Float, colorArgb: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(18f) }
    var color by remember { mutableStateOf(textColorSwatches.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") })
                Text("Font size: ${fontSize.toInt()}pt", modifier = Modifier.padding(top = 12.dp))
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 10f..48f)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    textColorSwatches.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (color == argb) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { color = argb }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, fontSize, color) }, enabled = text.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditElementOverlay(
    key: Int,
    element: EditElement,
    rendered: RenderedPage,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onMove: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    val bounds = elementBounds(element, rendered, canvasWidthPx, canvasHeightPx)
    val density = LocalDensity.current
    // detectDragGestures runs on a coroutine that outlives any single recomposition
    // (it keeps handling gestures as long as `key` is stable), so a plain captured
    // `element` would go stale after the first drag tick - rememberUpdatedState
    // makes every read inside the gesture callback resolve to the latest value.
    val latestElement by rememberUpdatedState(element)

    Box(
        modifier = Modifier
            .offset { IntOffset(bounds.topLeft.x.roundToInt(), bounds.topLeft.y.roundToInt()) }
            .size(
                width = with(density) { bounds.width.toDp() },
                height = with(density) { bounds.height.toDp() }
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            .pointerInput(key) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val ptPerPxX = rendered.pageWidthPt / canvasWidthPx
                    val ptPerPxY = rendered.pageHeightPt / canvasHeightPx
                    val dxPdf = dragAmount.x * ptPerPxX
                    val dyPdf = -dragAmount.y * ptPerPxY // pixel y is down, PDF y is up
                    onMove(latestElement.x + dxPdf, latestElement.y + dyPdf)
                }
            }
    ) {
        when (element) {
            is EditElement.ImageElement -> AsyncImage(
                model = element.imageUri,
                contentDescription = "Added image",
                modifier = Modifier.fillMaxSize()
            )
            is EditElement.TextElement -> {
                val fontSizePx = pdfLenToPxX(element.fontSize, rendered, canvasWidthPx)
                Text(
                    text = element.text,
                    color = Color(element.colorArgb),
                    fontSize = with(density) { fontSizePx.toSp() }
                )
            }
            is EditElement.ShapeElement -> Unit // visuals drawn by the Canvas layer; this Box is just the drag/delete hit target
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private data class ElementBounds(val topLeft: Offset, val width: Float, val height: Float)

private fun elementBounds(
    element: EditElement,
    rendered: RenderedPage,
    canvasWidthPx: Float,
    canvasHeightPx: Float
): ElementBounds = when (element) {
    is EditElement.TextElement -> {
        val pos = pdfToPixel(element.x, element.y, rendered, canvasWidthPx, canvasHeightPx)
        ElementBounds(Offset(pos.x, pos.y - 32f), 100f, 32f)
    }
    is EditElement.ImageElement -> {
        val w = pdfLenToPxX(element.width, rendered, canvasWidthPx)
        val h = pdfLenToPxY(element.height, rendered, canvasHeightPx)
        val pos = pdfToPixel(element.x, element.y + element.height, rendered, canvasWidthPx, canvasHeightPx)
        ElementBounds(pos, w, h)
    }
    is EditElement.ShapeElement -> when (element.shapeType) {
        ShapeType.LINE -> {
            val pos = pdfToPixel(element.x, element.y, rendered, canvasWidthPx, canvasHeightPx)
            ElementBounds(Offset(pos.x - 16f, pos.y - 16f), 32f, 32f)
        }
        else -> {
            val w = pdfLenToPxX(element.width, rendered, canvasWidthPx)
            val h = pdfLenToPxY(element.height, rendered, canvasHeightPx)
            val pos = pdfToPixel(element.x, element.y + element.height, rendered, canvasWidthPx, canvasHeightPx)
            ElementBounds(pos, w, h)
        }
    }
}

private fun Modifier.pointerInputTap(
    rendered: RenderedPage,
    addMode: AddMode,
    shapeType: ShapeType,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onTap: (Offset) -> Unit
): Modifier = this.pointerInput(rendered.pageIndex, addMode, shapeType) {
    detectTapGestures { offset ->
        onTap(pixelToPdf(offset, rendered, canvasWidthPx, canvasHeightPx))
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShapeElement(
    element: EditElement.ShapeElement,
    rendered: RenderedPage,
    canvasWidthPx: Float,
    canvasHeightPx: Float
) {
    val color = Color(element.colorArgb)
    val strokeWidthPx = pdfLenToPxX(element.strokeWidthPt, rendered, canvasWidthPx)
    when (element.shapeType) {
        ShapeType.RECTANGLE -> {
            val topLeft = pdfToPixel(element.x, element.y + element.height, rendered, canvasWidthPx, canvasHeightPx)
            val size = androidx.compose.ui.geometry.Size(
                pdfLenToPxX(element.width, rendered, canvasWidthPx),
                pdfLenToPxY(element.height, rendered, canvasHeightPx)
            )
            if (element.filled) {
                drawRect(color = color, topLeft = topLeft, size = size)
            } else {
                drawRect(color = color, topLeft = topLeft, size = size, style = Stroke(width = strokeWidthPx))
            }
        }
        ShapeType.CIRCLE -> {
            val center = pdfToPixel(
                element.x + element.width / 2f,
                element.y + element.height / 2f,
                rendered, canvasWidthPx, canvasHeightPx
            )
            val radius = pdfLenToPxX(element.width / 2f, rendered, canvasWidthPx)
            if (element.filled) {
                drawCircle(color = color, radius = radius, center = center)
            } else {
                drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidthPx))
            }
        }
        ShapeType.LINE -> {
            val start = pdfToPixel(element.x, element.y, rendered, canvasWidthPx, canvasHeightPx)
            val end = pdfToPixel(element.x + element.width, element.y + element.height, rendered, canvasWidthPx, canvasHeightPx)
            drawLine(color = color, start = start, end = end, strokeWidth = strokeWidthPx)
        }
    }
}
