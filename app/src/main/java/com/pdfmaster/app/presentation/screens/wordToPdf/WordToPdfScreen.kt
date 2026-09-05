package com.pdfmaster.app.presentation.screens.wordToPdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private val wordMimeTypes = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordToPdfScreen(onBack: () -> Unit, viewModel: WordToPdfViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("Saved to Downloads")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word to PDF") },
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
            Button(onClick = { pickFileLauncher.launch(wordMimeTypes) }) {
                Text("Select Word File")
            }

            if (selectedFile != null) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
                    Text(
                        text = selectedFile?.lastPathSegment ?: "Selected file",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Text("Converts via the CloudConvert API and saves the result to your Downloads folder.")

            Button(
                onClick = viewModel::convert,
                enabled = selectedFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Convert")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Converting...", progress = loading.progress)
    }
}
