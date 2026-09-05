package com.pdfmaster.app.presentation.screens.watermark

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.model.WatermarkConfig
import com.pdfmaster.app.domain.model.WatermarkType
import com.pdfmaster.app.domain.repository.PdfEngine
import com.pdfmaster.app.presentation.util.RenderedPage
import com.pdfmaster.app.presentation.util.PdfPagePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddWatermarkViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _config = MutableStateFlow(WatermarkConfig(type = WatermarkType.TEXT, text = "CONFIDENTIAL"))
    val config: StateFlow<WatermarkConfig> = _config.asStateFlow()

    private val _preview = MutableStateFlow<RenderedPage?>(null)
    val preview: StateFlow<RenderedPage?> = _preview.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _preview.value = null
        if (uri == null) return
        viewModelScope.launch {
            _preview.value = PdfPagePreview.render(context, uri, pageIndex = 0, targetWidthPx = 1000)
        }
    }

    fun updateConfig(transform: (WatermarkConfig) -> WatermarkConfig) {
        _config.value = transform(_config.value)
    }

    fun apply(outputUri: Uri) {
        val input = _selectedFile.value
        val cfg = _config.value
        when {
            input == null -> _state.value = Resource.Error("Select a PDF first")
            cfg.type == WatermarkType.TEXT && cfg.text.isNullOrBlank() ->
                _state.value = Resource.Error("Enter watermark text")
            cfg.type == WatermarkType.IMAGE && cfg.imageUri == null ->
                _state.value = Resource.Error("Choose a watermark image")
            else -> viewModelScope.launch {
                _state.value = Resource.Loading(0f)
                val result = pdfEngine.addWatermark(input, outputUri, cfg) { progress ->
                    _state.value = Resource.Loading(progress)
                }
                _state.value = result.fold(
                    onSuccess = { Resource.Success(outputUri) },
                    onFailure = { Resource.Error(it.message ?: "Could not add watermark") }
                )
            }
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
