package com.pdfmaster.app.presentation.screens.watermark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.model.WatermarkPosition
import com.pdfmaster.app.domain.model.WatermarkType
import com.pdfmaster.app.presentation.components.ProgressDialog

private val colorSwatches = listOf(
    "Gray" to android.graphics.Color.GRAY,
    "Black" to android.graphics.Color.BLACK,
    "Red" to android.graphics.Color.RED,
    "Blue" to android.graphics.Color.BLUE,
    "Green" to android.graphics.Color.rgb(0, 128, 0)
)

private val positions = listOf(
    "Center" to WatermarkPosition.CENTER,
    "Top-Left" to WatermarkPosition.TOP_LEFT,
    "Top-Right" to WatermarkPosition.TOP_RIGHT,
    "Bottom-Left" to WatermarkPosition.BOTTOM_LEFT,
    "Bottom-Right" to WatermarkPosition.BOTTOM_RIGHT
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWatermarkScreen(onBack: () -> Unit, viewModel: AddWatermarkViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var watermarkImagePreview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(config.imageUri) {
        watermarkImagePreview = config.imageUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> viewModel.updateConfig { it.copy(imageUri = uri) } }

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
                snackbarHostState.showSnackbar("Watermark added")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Watermark") },
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

            preview?.let { rendered ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .aspectRatio(rendered.pageWidthPt / rendered.pageHeightPt)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Image(
                        bitmap = rendered.bitmap.asImageBitmap(),
                        contentDescription = "Page preview",
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pxPerPt = size.width / rendered.pageWidthPt
                        val anchors = if (config.tile) {
                            tiledPreviewAnchors(size.width, size.height)
                        } else {
                            listOf(previewAnchorFor(config.position, size.width, size.height))
                        }
                        anchors.forEach { anchor ->
                            withTransform({
                                translate(anchor.x, anchor.y)
                                // Compose rotates clockwise for +degrees in this
                                // top-down space; PDF rotates counter-clockwise
                                // for +degrees in its bottom-up space - negate so
                                // the preview turns the same visual way as the PDF.
                                rotate(degrees = -config.rotation, pivot = Offset.Zero)
                            }) {
                                when (config.type) {
                                    WatermarkType.TEXT -> {
                                        val text = config.text.orEmpty()
                                        if (text.isNotBlank()) {
                                            drawContext.canvas.nativeCanvas.apply {
                                                val paint = Paint().apply {
                                                    color = config.textColor
                                                    alpha = (config.opacity.coerceIn(0f, 1f) * 255).toInt()
                                                    textSize = config.fontSize * pxPerPt
                                                    typeface = Typeface.DEFAULT_BOLD
                                                    textAlign = Paint.Align.CENTER
                                                }
                                                drawText(text, 0f, paint.textSize / 3f, paint)
                                            }
                                        }
                                    }
                                    WatermarkType.IMAGE -> {
                                        watermarkImagePreview?.let { bmp ->
                                            val dispWidth = size.width * 0.35f
                                            val dispHeight = dispWidth * bmp.height / bmp.width.toFloat()
                                            drawImage(
                                                image = bmp.asImageBitmap(),
                                                dstOffset = IntOffset((-dispWidth / 2f).toInt(), (-dispHeight / 2f).toInt()),
                                                dstSize = IntSize(dispWidth.toInt(), dispHeight.toInt()),
                                                alpha = config.opacity.coerceIn(0f, 1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "Preview is approximate (font rendering differs slightly from the final PDF).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                FilterChip(
                    selected = config.type == WatermarkType.TEXT,
                    onClick = { viewModel.updateConfig { it.copy(type = WatermarkType.TEXT) } },
                    label = { Text("Text") }
                )
                FilterChip(
                    selected = config.type == WatermarkType.IMAGE,
                    onClick = { viewModel.updateConfig { it.copy(type = WatermarkType.IMAGE) } },
                    label = { Text("Image") }
                )
            }

            if (config.type == WatermarkType.TEXT) {
                OutlinedTextField(
                    value = config.text.orEmpty(),
                    onValueChange = { text -> viewModel.updateConfig { it.copy(text = text) } },
                    label = { Text("Watermark text") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                Text("Font size: ${config.fontSize.toInt()}pt", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = config.fontSize,
                    onValueChange = { value -> viewModel.updateConfig { it.copy(fontSize = value) } },
                    valueRange = 12f..96f
                )

                Text("Color")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    colorSwatches.forEach { (name, argb) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (config.textColor == argb) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable(
                                    onClickLabel = name
                                ) { viewModel.updateConfig { cfg -> cfg.copy(textColor = argb) } }
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(if (config.imageUri == null) "Choose Image" else "Change Image")
                }
            }

            Text("Opacity: ${(config.opacity * 100).toInt()}%", modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = config.opacity,
                onValueChange = { value -> viewModel.updateConfig { it.copy(opacity = value) } },
                valueRange = 0.05f..1f
            )

            Text("Rotation: ${config.rotation.toInt()}°")
            Slider(
                value = config.rotation,
                onValueChange = { value -> viewModel.updateConfig { it.copy(rotation = value) } },
                valueRange = -180f..180f
            )

            Text("Position", modifier = Modifier.padding(top = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                positions.forEach { (label, value) ->
                    FilterChip(
                        selected = config.position == value,
                        onClick = { viewModel.updateConfig { it.copy(position = value) } },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Tile across page", modifier = Modifier.padding(end = 8.dp))
                Switch(
                    checked = config.tile,
                    onCheckedChange = { checked -> viewModel.updateConfig { it.copy(tile = checked) } }
                )
            }

            Button(
                onClick = { createDocumentLauncher.launch("watermarked_document.pdf") },
                enabled = selectedFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Apply Watermark")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Applying watermark...", progress = loading.progress)
    }
}

private fun previewAnchorFor(position: WatermarkPosition, width: Float, height: Float): Offset {
    val margin = minOf(width, height) * 0.1f
    return when (position) {
        WatermarkPosition.CENTER -> Offset(width / 2f, height / 2f)
        WatermarkPosition.TOP_LEFT -> Offset(margin, margin)
        WatermarkPosition.TOP_RIGHT -> Offset(width - margin, margin)
        WatermarkPosition.BOTTOM_LEFT -> Offset(margin, height - margin)
        WatermarkPosition.BOTTOM_RIGHT -> Offset(width - margin, height - margin)
    }
}

private fun tiledPreviewAnchors(width: Float, height: Float): List<Offset> {
    val stepX = width / 3f
    val stepY = height / 4f
    val points = mutableListOf<Offset>()
    var y = stepY / 2f
    while (y < height) {
        var x = stepX / 2f
        while (x < width) {
            points += Offset(x, y)
            x += stepX
        }
        y += stepY
    }
    return points
}
