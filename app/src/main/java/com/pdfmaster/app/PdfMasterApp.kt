package com.pdfmaster.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pdfmaster.app.data.worker.PdfProcessingWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PdfMasterApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // PDFBox-Android needs its font/resource assets primed once before any
        // PDDocument use, or page rendering that touches fonts throws.
        PDFBoxResourceLoader.init(applicationContext)
        createNotificationChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PdfProcessingWorker.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_pdf_processing),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_pdf_processing_desc)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
