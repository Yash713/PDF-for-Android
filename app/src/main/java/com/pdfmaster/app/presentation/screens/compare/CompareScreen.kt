package com.pdfmaster.app.presentation.screens.compare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog
import com.pdfmaster.app.presentation.util.RenderedPage

private enum class CompareMode { SIDE_BY_SIDE, OVERLAY_DIFF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(onBack: () -> Unit, viewModel: CompareViewModel = hiltViewModel()) {
    val firstFile by viewModel.firstFile.collectAsStateWithLifecycle()
    val secondFile by viewModel.secondFile.collectAsStateWithLifecycle()
    val comparisonState by viewModel.comparisonState.collectAsStateWithLifecycle()
    val pageIndex by viewModel.pageIndex.collectAsStateWithLifecycle()
    val firstPreview by viewModel.firstPagePreview.collectAsStateWithLifecycle()
    val secondPreview by viewModel.secondPagePreview.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(CompareMode.SIDE_BY_SIDE) }

    val pickFirstLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFirst(uri) }

    val pickSecondLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectSecond(uri) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(comparisonState) {
        val current = comparisonState
        if (current is Resource.Error) {
            snackbarHostState.showSnackbar(current.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare PDFs") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickFirstLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (firstFile == null) "Select First PDF" else "First: ${firstFile?.lastPathSegment}", maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { pickSecondLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (secondFile == null) "Select Second PDF" else "Second: ${secondFile?.lastPathSegment}", maxLines = 1)
                }
            }

            Button(
                onClick = viewModel::compare,
                enabled = firstFile != null && secondFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Compare")
            }

            val result = (comparisonState as? Resource.Success)?.data
            if (result != null) {
                if (!result.pageCountsMatch) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "Page counts differ: first has ${result.pageCountFirst}, second has " +
                                "${result.pageCountSecond}. Comparing the first ${result.comparedPageCount} page(s).",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    FilterChip(
                        selected = mode == CompareMode.SIDE_BY_SIDE,
                        onClick = { mode = CompareMode.SIDE_BY_SIDE },
                        label = { Text("Side by Side") }
                    )
                    FilterChip(
                        selected = mode == CompareMode.OVERLAY_DIFF,
                        onClick = { mode = CompareMode.OVERLAY_DIFF },
                        label = { Text("Overlay Diff") }
                    )
                }

                val currentDiff = result.pageDiffs.getOrNull(pageIndex)

                if (mode == CompareMode.SIDE_BY_SIDE) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        PagePane(rendered = firstPreview, modifier = Modifier.weight(1f))
                        PagePane(rendered = secondPreview, modifier = Modifier.weight(1f))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        firstPreview?.let { rendered ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Image(
                                    bitmap = rendered.bitmap.asImageBitmap(),
                                    contentDescription = "Page ${pageIndex + 1}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    currentDiff?.diffRegions?.forEach { region ->
                                        drawRect(
                                            color = Color.Red.copy(alpha = 0.35f),
                                            topLeft = Offset(region.left * size.width, region.top * size.height),
                                            size = Size(
                                                (region.right - region.left) * size.width,
                                                (region.bottom - region.top) * size.height
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                currentDiff?.let { diff ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (diff.hasDifference) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                            contentDescription = null
                        )
                        Text(
                            text = if (diff.hasDifference) {
                                "This page differs (~${(diff.diffPercentage * 100).toInt()}% of the page)"
                            } else {
                                "No visual differences on this page"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedButton(onClick = { viewModel.goToPage(-1) }, enabled = pageIndex > 0) {
                        Text("Previous")
                    }
                    Text(
                        text = "Page ${pageIndex + 1} of ${result.comparedPageCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { viewModel.goToPage(1) },
                        enabled = pageIndex < result.comparedPageCount - 1
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }

    (comparisonState as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Comparing pages...", progress = loading.progress)
    }
}

@Composable
private fun PagePane(rendered: RenderedPage?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        rendered?.let {
            Image(
                bitmap = it.bitmap.asImageBitmap(),
                contentDescription = "Page ${it.pageIndex + 1}",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
