package com.pdfmaster.app.presentation.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Renders HTML/a URL in an already-attached [WebView] and captures the full
 * (not just on-screen) content as one bitmap.
 *
 * This intentionally does NOT use android.print.PrintDocumentAdapter (the
 * "real" API for WebView-to-PDF): PrintDocumentAdapter.LayoutResultCallback
 * and WriteResultCallback both have package-private constructors in the
 * compiled Android SDK, so only the print framework itself can construct
 * them - an app can't call adapter.onLayout()/onWrite() directly without
 * going through the system print dialog (PrintManager.print()), which would
 * hand control to the OS UI instead of converting in place. Measuring the
 * WebView to its full content height and calling View.draw() into a bitmap
 * is the standard workaround, and it feeds directly into the same image-to-
 * PDF path jpgToPdf already uses (PdfEngine.htmlBitmapToPdf).
 */
object HtmlToPdfPrinter {

    sealed class Source {
        data class Url(val url: String) : Source()
        data class Html(val html: String) : Source()
    }

    /** Caps memory use on very long pages - about 55 "pages" worth of content at 200dpi. */
    private const val MAX_CAPTURE_HEIGHT_PX = 16000

    suspend fun loadAndCapture(
        webView: WebView,
        source: Source,
        onProgress: (Float) -> Unit = {}
    ): Bitmap {
        onProgress(0.05f)
        waitForPageLoad(webView, source)
        onProgress(0.5f)
        return captureFullPage(webView)
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

    private fun captureFullPage(webView: WebView): Bitmap {
        val width = webView.width.takeIf { it > 0 } ?: error("WebView has no width - is it attached to the screen?")

        // An UNSPECIFIED height measure spec makes WebView report its full
        // scrollable content height instead of just the visible viewport.
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val fullHeight = webView.measuredHeight.coerceIn(1, MAX_CAPTURE_HEIGHT_PX)
        webView.layout(0, 0, width, fullHeight)

        val bitmap = Bitmap.createBitmap(width, fullHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        webView.draw(canvas)
        return bitmap
    }
}
