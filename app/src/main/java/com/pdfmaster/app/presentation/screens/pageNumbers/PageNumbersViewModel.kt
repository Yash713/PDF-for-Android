package com.pdfmaster.app.presentation.screens.pageNumbers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.PageNumberConfig
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import com.pdfmaster.app.presentation.util.PdfPagePreview
import com.pdfmaster.app.presentation.util.RenderedPage
import com.pdfmaster.app.presentation.util.displayNameOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PageNumbersViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _preview = MutableStateFlow<RenderedPage?>(null)
    val preview: StateFlow<RenderedPage?> = _preview.asStateFlow()

    private val _config = MutableStateFlow(PageNumberConfig())
    val config: StateFlow<PageNumberConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun displayName(uri: Uri): String = context.displayNameOf(uri)

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _preview.value = null
        if (uri == null) return
        viewModelScope.launch {
            _preview.value = PdfPagePreview.render(context, uri, pageIndex = 0, targetWidthPx = 700)
        }
    }

    fun updateConfig(transform: (PageNumberConfig) -> PageNumberConfig) {
        _config.value = transform(_config.value)
    }

    fun apply(outputUri: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF first")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.addPageNumbers(input, outputUri, _config.value) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Could not add page numbers") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
