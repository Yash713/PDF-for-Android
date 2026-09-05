package com.pdfmaster.app.presentation.screens.jpgToPdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JpgToPdfScreen(onBack: () -> Unit, viewModel: JpgToPdfViewModel = hiltViewModel()) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> viewModel.addImages(uris) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::convert) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("PDF created")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JPG to PDF") },
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
        ) {
            Button(onClick = { pickImagesLauncher.launch("image/*") }) {
                Text("Select Images")
            }

            Text(
                text = "${images.size} image(s) selected - long-press the handle to reorder",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            ReorderableImageList(
                images = images,
                onMove = viewModel::moveImage,
                onRemove = viewModel::removeImage,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { createDocumentLauncher.launch("images_to_pdf.pdf") },
                enabled = images.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Convert")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Building PDF...", progress = loading.progress)
    }
}

@Composable
private fun ReorderableImageList(
    images: List<Uri>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemHeightPx by remember { mutableIntStateOf(0) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(images, key = { _, uri -> uri.toString() }) { index, uri ->
            val isDragged = index == draggedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates -> itemHeightPx = coordinates.size.height }
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .zIndex(if (isDragged) 1f else 0f)
                    .background(
                        if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .pointerInput(uri) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggedIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedIndex = -1
                                    dragOffset = 0f
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta.y
                                    val height = itemHeightPx
                                    if (height <= 0) return@detectDragGesturesAfterLongPress
                                    val steps = (dragOffset / height).roundToInt()
                                    if (steps != 0) {
                                        val target = (draggedIndex + steps).coerceIn(images.indices)
                                        if (target != draggedIndex) {
                                            onMove(draggedIndex, target)
                                            dragOffset -= steps * height
                                            draggedIndex = target
                                        }
                                    }
                                }
                            )
                        }
                )
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Text(
                    text = uri.lastPathSegment ?: "image",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    maxLines = 1
                )
                IconButton(onClick = { onRemove(uri) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        }
    }
}
