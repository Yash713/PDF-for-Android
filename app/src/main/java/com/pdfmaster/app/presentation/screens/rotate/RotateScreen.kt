package com.pdfmaster.app.presentation.screens.rotate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog

private val angleOptions = listOf(90, 180, 270)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotateScreen(onBack: () -> Unit, viewModel: RotateViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val angle by viewModel.angle.collectAsStateWithLifecycle()
    val applyToAll by viewModel.applyToAll.collectAsStateWithLifecycle()
    val pageListInput by viewModel.pageListInput.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::rotate) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("Rotated successfully")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    val animatedAngle by animateFloatAsState(
        targetValue = angle.toFloat(),
        animationSpec = tween(durationMillis = 350),
        label = "pageRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rotate PDF") },
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
                Text("Select File")
            }

            selectedFile?.let { uri ->
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
                    Text(
                        text = pageCount?.let { "${viewModel.displayName(uri)} - $it pages" }
                            ?: viewModel.displayName(uri),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            preview?.let { rendered ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = rendered.bitmap.asImageBitmap(),
                        contentDescription = "Page preview, rotation applied",
                        modifier = Modifier
                            .size(180.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .rotate(animatedAngle)
                    )
                }
            }

            Text("Rotation angle", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                angleOptions.forEach { option ->
                    FilterChip(
                        selected = angle == option,
                        onClick = { viewModel.setAngle(option) },
                        label = { Text("$option°") }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Apply to all pages", modifier = Modifier.weight(1f))
                        Switch(checked = applyToAll, onCheckedChange = viewModel::setApplyToAll)
                    }
                    if (!applyToAll) {
                        OutlinedTextField(
                            value = pageListInput,
                            onValueChange = viewModel::setPageListInput,
                            label = { Text("Pages to rotate") },
                            placeholder = { Text("e.g. 1, 3, 5-6") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }
                }
            }

            Button(
                onClick = { createDocumentLauncher.launch("rotated_document.pdf") },
                enabled = selectedFile != null && (applyToAll || pageListInput.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Rotate")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Rotating...", progress = loading.progress)
    }
}
