package com.pdfmaster.app.presentation.util

import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Drives an already-attached [WebView] through Android's print framework to
 * produce a PDF. All of this must run on the main thread (WebView and
 * PrintDocumentAdapter both require it) - callers are expected to already be
 * on Dispatchers.Main.
 *
 * The WebView is expected to be live in the view hierarchy (e.g. hosted by an
 * AndroidView in the caller's Composable) - a WebView that's only constructed
 * and manually sized without ever being attached to a Window risks producing a
 * blank PDF, since its renderer generally needs a real attach/layout pass to
 * have actually drawn anything to capture.
 */
object HtmlToPdfPrinter {

    sealed class Source {
        data class Url(val url: String) : Source()
        data class Html(val html: String) : Source()
    }

    suspend fun loadAndPrint(
        webView: WebView,
        source: Source,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ) {
        onProgress(0.05f)
        waitForPageLoad(webView, source)
        onProgress(0.4f)

        val adapter = webView.createPrintDocumentAdapter("PDF Master export")
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        layoutPrintDocument(adapter, attributes)
        onProgress(0.7f)

        val context = webView.context
        val pfd = context.contentResolver.openFileDescriptor(outputUri, "w")
            ?: error("Could not open output stream")
        pfd.use { writePrintDocument(adapter, it) }
        onProgress(1f)
    }

    private suspend fun waitForPageLoad(webView: WebView, source: Source) {
        withTimeout(30_000) {
            suspendCancellableCoroutine { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                when (source) {
                    is Source.Url -> webView.loadUrl(source.url)
                    is Source.Html -> webView.loadDataWithBaseURL(null, source.html, "text/html", "UTF-8", null)
                }
            }
        }
    }

    private suspend fun layoutPrintDocument(adapter: PrintDocumentAdapter, attributes: PrintAttributes) {
        suspendCancellableCoroutine<Unit> { continuation ->
            adapter.onLayout(
                null,
                attributes,
                null,
                object : PrintDocumentAdapter.LayoutResultCallback() {
                    override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onLayoutFailed(error: CharSequence?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Layout failed: $error"))
                        }
                    }
                },
                Bundle()
            )
        }
    }

    private suspend fun writePrintDocument(adapter: PrintDocumentAdapter, destination: ParcelFileDescriptor) {
        suspendCancellableCoroutine<Unit> { continuation ->
            adapter.onWrite(
                arrayOf(PageRange.ALL_PAGES),
                destination,
                null,
                object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<PageRange>?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onWriteFailed(error: CharSequence?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Write failed: $error"))
                        }
                    }
                }
            )
        }
    }
}
