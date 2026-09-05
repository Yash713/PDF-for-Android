package com.pdfmaster.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Non-dismissible progress dialog shown while a PdfEngine/OcrEngine op is running.
 * Pass [indeterminate] = true for operations that don't report granular progress
 * (Protect/Unlock just encrypt-and-save in one shot with no per-page callback).
 */
@Composable
fun ProgressDialog(
    title: String,
    progress: Float,
    indeterminate: Boolean = false,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { /* not dismissible while work is in flight */ },
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(modifier = modifier.fillMaxWidth()) {
                if (indeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}
