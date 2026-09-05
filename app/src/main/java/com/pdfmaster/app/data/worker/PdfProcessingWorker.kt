package com.pdfmaster.app.data.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pdfmaster.app.domain.model.CompressQuality
import com.pdfmaster.app.domain.repository.PdfEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs Merge/Compress on WorkManager so a large job survives the user leaving
 * the screen, and drives a foreground-service notification with a live
 * progress bar (the "Background Processing" architecture constraint).
 */
@HiltWorker
class PdfProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pdfEngine: PdfEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val operation = inputData.getString(KEY_OPERATION) ?: return Result.failure()
        setForeground(createForegroundInfo(0f))

        val onProgress: (Float) -> Unit = { progress ->
            updateNotification(progress)
            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        }

        val outcome = when (operation) {
            OPERATION_MERGE -> {
                val inputs = inputData.getStringArray(KEY_INPUT_URIS)?.map(Uri::parse).orEmpty()
                val output = inputData.getString(KEY_OUTPUT_URI)?.let(Uri::parse)
                    ?: return Result.failure()
                pdfEngine.mergePdfs(inputs, output, onProgress)
            }

            OPERATION_COMPRESS -> {
                val input = inputData.getStringArray(KEY_INPUT_URIS)?.firstOrNull()?.let(Uri::parse)
                    ?: return Result.failure()
                val output = inputData.getString(KEY_OUTPUT_URI)?.let(Uri::parse)
                    ?: return Result.failure()
                val quality = CompressQuality.valueOf(
                    inputData.getString(KEY_QUALITY) ?: CompressQuality.MEDIUM.name
                )
                pdfEngine.compressPdf(input, output, quality, onProgress)
            }

            else -> return Result.failure()
        }

        return outcome.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.failure(workDataOf(KEY_ERROR to it.message)) }
        )
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val notification = buildNotification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(progress: Float): Notification =
        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PDF Master")
            .setContentText("Processing your PDF...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
            .build()

    private fun updateNotification(progress: Float) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pdf_processing"
        const val NOTIFICATION_ID = 4201

        private const val KEY_OPERATION = "operation"
        private const val OPERATION_MERGE = "merge"
        private const val OPERATION_COMPRESS = "compress"
        private const val KEY_INPUT_URIS = "input_uris"
        private const val KEY_OUTPUT_URI = "output_uri"
        private const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        fun mergeRequest(inputUris: List<Uri>, outputUri: Uri): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<PdfProcessingWorker>()
                .setInputData(
                    workDataOf(
                        KEY_OPERATION to OPERATION_MERGE,
                        KEY_INPUT_URIS to inputUris.map(Uri::toString).toTypedArray(),
                        KEY_OUTPUT_URI to outputUri.toString()
                    )
                )
                .build()

        fun compressRequest(inputUri: Uri, outputUri: Uri, quality: CompressQuality): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<PdfProcessingWorker>()
                .setInputData(
                    workDataOf(
                        KEY_OPERATION to OPERATION_COMPRESS,
                        KEY_INPUT_URIS to arrayOf(inputUri.toString()),
                        KEY_OUTPUT_URI to outputUri.toString(),
                        KEY_QUALITY to quality.name
                    )
                )
                .build()
    }
}
