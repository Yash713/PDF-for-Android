package com.pdfmaster.app.presentation.screens.htmlToPdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The WebView capture step (see HtmlToPdfPrinter) must run from the
 * Composable, which owns the actual WebView instance - this ViewModel drives
 * everything else (input state, and finishing the PDF once a bitmap is handed
 * back to it) so it still owns the terminal Resource state like every other
 * screen in the app.
 */
@HiltViewModel
class HtmlToPdfViewModel @Inject constructor(
    private val pdfEngine: PdfEngine
) : ViewModel() {

    private val _useUrl = MutableStateFlow(true)
    val useUrl: StateFlow<Boolean> = _useUrl.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun setUseUrl(value: Boolean) {
        _useUrl.value = value
    }

    fun setInput(value: String) {
        _input.value = value
    }

    fun onCaptureStarted() {
        _state.value = Resource.Loading(0f)
    }

    fun onCaptureFailed(message: String) {
        _state.value = Resource.Error(message)
    }

    fun convertCapturedBitmap(bitmap: Bitmap, outputUri: Uri) {
        viewModelScope.launch {
            _state.value = Resource.Loading(0.5f)
            val result = pdfEngine.htmlBitmapToPdf(bitmap, outputUri) { progress ->
                _state.value = Resource.Loading(0.5f + progress * 0.5f)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Conversion failed") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
