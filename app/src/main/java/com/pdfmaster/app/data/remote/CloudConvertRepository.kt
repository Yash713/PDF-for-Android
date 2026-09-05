package com.pdfmaster.app.data.remote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.pdfmaster.app.BuildConfig
import com.pdfmaster.app.data.remote.dto.CreateJobRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

enum class OfficeFormat(val extension: String, val cloudConvertFormat: String, val mimeType: String) {
    WORD(
        "docx", "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ),
    POWERPOINT(
        "pptx", "pptx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    ),
    EXCEL(
        "xlsx", "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
}

private const val PDF_FORMAT = "pdf"
private const val PDF_MIME_TYPE = "application/pdf"

class CloudConvertRepository @Inject constructor(
    private val api: CloudConvertApi,
    @ApplicationContext private val context: Context
) {

    /** Converts the PDF at [inputUri] to [format] and saves the result into Downloads. */
    suspend fun convertPdfToOffice(
        inputUri: Uri,
        format: OfficeFormat,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = runConversion(
        inputUri = inputUri,
        inputFormat = PDF_FORMAT,
        inputMimeType = PDF_MIME_TYPE,
        outputFormat = format.cloudConvertFormat,
        outputExtension = format.extension,
        outputMimeType = format.mimeType,
        onProgress = onProgress
    )

    /** Converts a Word/PowerPoint/Excel file at [inputUri] to PDF and saves it into Downloads. */
    suspend fun convertOfficeToPdf(
        inputUri: Uri,
        format: OfficeFormat,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = runConversion(
        inputUri = inputUri,
        inputFormat = format.cloudConvertFormat,
        inputMimeType = format.mimeType,
        outputFormat = PDF_FORMAT,
        outputExtension = PDF_FORMAT,
        outputMimeType = PDF_MIME_TYPE,
        onProgress = onProgress
    )

    private suspend fun runConversion(
        inputUri: Uri,
        inputFormat: String,
        inputMimeType: String,
        outputFormat: String,
        outputExtension: String,
        outputMimeType: String,
        onProgress: (Float) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            check(BuildConfig.CLOUDCONVERT_API_KEY.isNotBlank()) {
                "No CloudConvert API key configured. Add CLOUDCONVERT_API_KEY to gradle.properties " +
                    "(get a free key at https://cloudconvert.com/dashboard/api/v2/keys)."
            }

            onProgress(0.05f)
            val tasks = mapOf(
                "import-file" to mapOf("operation" to "import/upload"),
                "convert-file" to mapOf(
                    "operation" to "convert",
                    "input" to "import-file",
                    "input_format" to inputFormat,
                    "output_format" to outputFormat
                ),
                "export-file" to mapOf("operation" to "export/url", "input" to "convert-file")
            )
            var job = api.createJob(CreateJobRequest(tasks)).data
            val importTask = job.tasks.first { it.name == "import-file" }
            val form = requireNotNull(importTask.result?.form) {
                "CloudConvert did not return an upload form"
            }

            val displayName = queryDisplayName(inputUri) ?: "document.$inputFormat"
            val fileBytes = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
                ?: error("Could not read the selected file")
            onProgress(0.15f)

            val formParts: Map<String, RequestBody> = form.parameters.mapValues { (_, value) ->
                value.toRequestBody("text/plain".toMediaType())
            }
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = displayName,
                body = fileBytes.toRequestBody(inputMimeType.toMediaType())
            )
            api.uploadFile(form.url, formParts, filePart)
            onProgress(0.35f)

            var attempts = 0
            val maxAttempts = 90 // ~3 minutes at 2s polling
            while (job.status !in FINISHED_STATES && attempts < maxAttempts) {
                delay(2000)
                job = api.getJob(job.id).data
                attempts++
                onProgress(0.35f + 0.55f * (attempts.toFloat() / maxAttempts).coerceAtMost(1f))
            }
            check(job.status == "finished") {
                "CloudConvert job did not finish in time (status: ${job.status})"
            }

            val exportTask = job.tasks.first { it.name == "export-file" }
            val resultFile = requireNotNull(exportTask.result?.files?.firstOrNull()) {
                "CloudConvert did not return a downloadable file"
            }

            val savedUri = downloadToDownloads(resultFile.url, resultFile.filename, outputExtension, outputMimeType)
            onProgress(1f)
            savedUri
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }

    private suspend fun downloadToDownloads(
        url: String,
        suggestedName: String,
        extension: String,
        mimeType: String
    ): Uri {
        val response = api.downloadFile(url)
        val body = requireNotNull(response.body()) { "Empty response body while downloading converted file" }
        val fileName = if (suggestedName.endsWith(".$extension")) {
            suggestedName
        } else {
            "${suggestedName.substringBeforeLast('.', suggestedName)}.$extension"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val itemUri = requireNotNull(
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ) { "Could not create a Downloads entry" }
            resolver.openOutputStream(itemUri)?.use { out -> body.byteStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            itemUri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            destFile.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            Uri.fromFile(destFile)
        }
    }

    private companion object {
        val FINISHED_STATES = setOf("finished", "error")
    }
}
