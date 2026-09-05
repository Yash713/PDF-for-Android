package com.pdfmaster.app.presentation.screens.split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(onBack: () -> Unit, viewModel: SplitViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val rangeInput by viewModel.rangeInput.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::split) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("Created ${current.data.size} file(s)")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split PDF") },
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

            OutlinedTextField(
                value = rangeInput,
                onValueChange = viewModel::setRangeInput,
                label = { Text("Page ranges") },
                placeholder = { Text("e.g. 1-3, 5, 7-10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Each comma-separated range becomes its own PDF file.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Button(
                onClick = { pickFolderLauncher.launch(null) },
                enabled = selectedFile != null && rangeInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Choose Destination Folder & Split")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Splitting...", progress = loading.progress)
    }
}
