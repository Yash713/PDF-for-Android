package com.pdfmaster.app.presentation.screens.pageNumbers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.PageNumberFormat
import com.pdfmaster.app.domain.model.PageNumberPosition
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog

private val positionOptions = listOf(
    "Top-Left" to PageNumberPosition.TOP_LEFT,
    "Top-Center" to PageNumberPosition.TOP_CENTER,
    "Top-Right" to PageNumberPosition.TOP_RIGHT,
    "Bottom-Left" to PageNumberPosition.BOTTOM_LEFT,
    "Bottom-Center" to PageNumberPosition.BOTTOM_CENTER,
    "Bottom-Right" to PageNumberPosition.BOTTOM_RIGHT
)

private val formatOptions = listOf(
    "1, 2, 3" to PageNumberFormat.NUMERIC,
    "i, ii, iii" to PageNumberFormat.ROMAN_LOWER,
    "I, II, III" to PageNumberFormat.ROMAN_UPPER
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageNumbersScreen(onBack: () -> Unit, viewModel: PageNumbersViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

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
                snackbarHostState.showSnackbar("Page numbers added")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page Numbers") },
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
                val previewLabel = formatNumber(config.startNumber, config.format)
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
                        val margin = 24f * pxPerPt
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = config.fontSize * pxPerPt
                        }
                        val (x, align) = when (config.position) {
                            PageNumberPosition.TOP_LEFT, PageNumberPosition.BOTTOM_LEFT ->
                                margin to android.graphics.Paint.Align.LEFT
                            PageNumberPosition.TOP_CENTER, PageNumberPosition.BOTTOM_CENTER ->
                                size.width / 2f to android.graphics.Paint.Align.CENTER
                            PageNumberPosition.TOP_RIGHT, PageNumberPosition.BOTTOM_RIGHT ->
                                size.width - margin to android.graphics.Paint.Align.RIGHT
                        }
                        val y = when (config.position) {
                            PageNumberPosition.TOP_LEFT, PageNumberPosition.TOP_CENTER, PageNumberPosition.TOP_RIGHT ->
                                margin + paint.textSize
                            else -> size.height - margin
                        }
                        paint.textAlign = align
                        drawContext.canvas.nativeCanvas.drawText(previewLabel, x, y, paint)
                    }
                }
            }

            Text("Position", style = MaterialTheme.typography.titleMedium)
            positionOptions.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    row.forEach { (label, value) ->
                        FilterChip(
                            selected = config.position == value,
                            onClick = { viewModel.updateConfig { it.copy(position = value) } },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Text("Format", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                formatOptions.forEach { (label, value) ->
                    FilterChip(
                        selected = config.format == value,
                        onClick = { viewModel.updateConfig { it.copy(format = value) } },
                        label = { Text(label) }
                    )
                }
            }

            Text("Font size: ${config.fontSize.toInt()}pt", modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = config.fontSize,
                onValueChange = { value -> viewModel.updateConfig { it.copy(fontSize = value) } },
                valueRange = 8f..24f
            )

            OutlinedTextField(
                value = config.startNumber.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { number ->
                        viewModel.updateConfig { it.copy(startNumber = number) }
                    }
                },
                label = { Text("Start at") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Button(
                onClick = { createDocumentLauncher.launch("numbered_document.pdf") },
                enabled = selectedFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Apply")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Adding page numbers...", progress = loading.progress)
    }
}

private fun formatNumber(number: Int, format: PageNumberFormat): String = when (format) {
    PageNumberFormat.NUMERIC -> number.toString()
    PageNumberFormat.ROMAN_LOWER -> toRoman(number).lowercase()
    PageNumberFormat.ROMAN_UPPER -> toRoman(number)
}

private fun toRoman(number: Int): String {
    if (number <= 0) return number.toString()
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var remaining = number
    val sb = StringBuilder()
    for (i in values.indices) {
        while (remaining >= values[i]) {
            remaining -= values[i]
            sb.append(symbols[i])
        }
    }
    return sb.toString()
}
