package com.pdfmaster.app.presentation.screens.ocr

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.OcrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrEngine: OcrEngine
) : ViewModel() {

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _extractedText = MutableStateFlow<String?>(null)
    val extractedText: StateFlow<String?> = _extractedText.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun setBitmap(bitmap: Bitmap?) {
        _bitmap.value = bitmap
        _extractedText.value = null
    }

    fun extractText() {
        val bmp = _bitmap.value ?: return
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = ocrEngine.extractText(bmp) { progress -> _state.value = Resource.Loading(progress) }
            result.fold(
                onSuccess = {
                    _extractedText.value = it
                    _state.value = Resource.Idle
                },
                onFailure = { _state.value = Resource.Error(it.message ?: "OCR failed") }
            )
        }
    }

    fun saveAsSearchablePdf(outputUri: Uri) {
        val bmp = _bitmap.value
        if (bmp == null) {
            _state.value = Resource.Error("Scan or pick an image first")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = ocrEngine.createSearchablePdf(bmp, outputUri) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Could not create PDF") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
