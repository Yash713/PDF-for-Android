package com.pdfmaster.app.presentation.screens.pdfToJpg

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog

private val dpiOptions = listOf("Low (100 DPI)" to 100, "Medium (150 DPI)" to 150, "High (300 DPI)" to 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToJpgScreen(onBack: () -> Unit, viewModel: PdfToJpgViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val dpi by viewModel.dpi.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::convert) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("Saved ${current.data.size} image(s)")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF to JPG") },
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
            Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                Text("Select PDF")
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

            Text("Image quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                dpiOptions.forEach { (label, value) ->
                    FilterChip(
                        selected = dpi == value,
                        onClick = { viewModel.setDpi(value) },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                text = "One JPG per page will be saved to the folder you choose.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { pickFolderLauncher.launch(null) },
                enabled = selectedFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Choose Destination Folder & Convert")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Converting...", progress = loading.progress)
    }
}
