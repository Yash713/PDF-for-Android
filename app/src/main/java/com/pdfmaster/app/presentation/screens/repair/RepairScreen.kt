package com.pdfmaster.app.presentation.screens.repair

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun RepairScreen(onBack: () -> Unit, viewModel: RepairViewModel = hiltViewModel()) {
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.selectFile(uri) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::repair) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        val current = state
        if (current is Resource.Error) {
            snackbarHostState.showSnackbar(current.message)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair PDF") },
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
                Text("Select File")
            }

            selectedFile?.let { uri ->
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
                    Text(text = viewModel.displayName(uri), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text(
                text = "Attempts to reopen a corrupted or unreadable PDF and rebuild it page by " +
                    "page. Pages that are too damaged to recover are dropped rather than left broken.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { createDocumentLauncher.launch("repaired_document.pdf") },
                enabled = selectedFile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Attempt Repair")
            }

            when (val current = state) {
                is Resource.Success -> {
                    val outcome = current.data
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (outcome.isFullyRecovered) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (outcome.isFullyRecovered) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null
                            )
                            Text(
                                text = if (outcome.isFullyRecovered) {
                                    "Fully recovered - all ${outcome.totalPages} page(s) restored"
                                } else {
                                    "Partial recovery - ${outcome.recoveredPages} of ${outcome.totalPages} page(s) restored"
                                },
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Repairing...", progress = loading.progress)
    }
}
