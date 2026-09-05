package com.pdfmaster.app.presentation.screens.htmlToPdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.pdfmaster.app.domain.model.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Deliberately thin: the actual conversion drives a WebView that must live in
 * the Composable's view hierarchy (see HtmlToPdfPrinter's doc comment), so
 * HtmlToPdfScreen owns that orchestration and just reports outcomes back here -
 * a ViewModel shouldn't hold a WebView reference past its Composable's lifetime.
 */
@HiltViewModel
class HtmlToPdfViewModel @Inject constructor() : ViewModel() {

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

    fun setLoading(progress: Float) {
        _state.value = Resource.Loading(progress)
    }

    fun setResult(outputUri: Uri, result: Result<Unit>) {
        _state.value = result.fold(
            onSuccess = { Resource.Success(outputUri) },
            onFailure = { Resource.Error(it.message ?: "Conversion failed") }
        )
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
