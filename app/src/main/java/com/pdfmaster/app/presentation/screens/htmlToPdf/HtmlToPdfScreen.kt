package com.pdfmaster.app.presentation.screens.htmlToPdf

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.presentation.components.ProgressDialog
import com.pdfmaster.app.presentation.util.HtmlToPdfPrinter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlToPdfScreen(onBack: () -> Unit, viewModel: HtmlToPdfViewModel = hiltViewModel()) {
    val useUrl by viewModel.useUrl.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val view = webView
        if (uri != null && view != null) {
            val source = if (useUrl) {
                HtmlToPdfPrinter.Source.Url(normalizeUrl(input))
            } else {
                HtmlToPdfPrinter.Source.Html(input)
            }
            viewModel.setLoading(0f)
            scope.launch {
                val result = runCatching {
                    HtmlToPdfPrinter.loadAndPrint(view, source, uri) { progress ->
                        viewModel.setLoading(progress)
                    }
                }
                viewModel.setResult(uri, result)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        when (val current = state) {
            is Resource.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetState()
            }
            is Resource.Success -> {
                snackbarHostState.showSnackbar("PDF saved")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HTML to PDF") },
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = useUrl,
                    onClick = { viewModel.setUseUrl(true) },
                    label = { Text("From URL") }
                )
                FilterChip(
                    selected = !useUrl,
                    onClick = { viewModel.setUseUrl(false) },
                    label = { Text("Paste HTML") }
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = viewModel::setInput,
                label = { Text(if (useUrl) "URL" else "HTML") },
                placeholder = { Text(if (useUrl) "https://example.com" else "<html>...</html>") },
                singleLine = useUrl,
                minLines = if (useUrl) 1 else 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Text(
                text = "Preview - this is what gets captured to PDF:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webView = this
                    }
                }
            )

            Button(
                onClick = { createDocumentLauncher.launch("webpage.pdf") },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Convert")
            }
        }
    }

    (state as? Resource.Loading)?.let { loading ->
        ProgressDialog(title = "Rendering and converting...", progress = loading.progress)
    }
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
