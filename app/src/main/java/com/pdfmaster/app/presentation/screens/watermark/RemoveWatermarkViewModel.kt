package com.pdfmaster.app.presentation.screens.watermark

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.DetectedWatermark
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import com.pdfmaster.app.presentation.util.PdfPagePreview
import com.pdfmaster.app.presentation.util.RenderedPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoveWatermarkViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _currentPage = MutableStateFlow<RenderedPage?>(null)
    val currentPage: StateFlow<RenderedPage?> = _currentPage.asStateFlow()

    private val _coverRects = MutableStateFlow<Map<Int, List<RectF>>>(emptyMap())
    val coverRects: StateFlow<Map<Int, List<RectF>>> = _coverRects.asStateFlow()

    private val _detected = MutableStateFlow<List<DetectedWatermark>>(emptyList())
    val detected: StateFlow<List<DetectedWatermark>> = _detected.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    private var pageIndex = 0

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _currentPage.value = null
        _coverRects.value = emptyMap()
        _detected.value = emptyList()
        pageIndex = 0
        if (uri == null) return
        loadPage(uri, 0)
        runDetection(uri)
    }

    private fun loadPage(uri: Uri, index: Int) {
        viewModelScope.launch {
            _currentPage.value = PdfPagePreview.render(context, uri, index, targetWidthPx = 1000)
        }
    }

    private fun runDetection(uri: Uri) {
        viewModelScope.launch {
            pdfEngine.detectWatermarks(uri).onSuccess { _detected.value = it }
        }
    }

    fun goToPage(delta: Int) {
        val uri = _selectedFile.value ?: return
        val pageCount = _currentPage.value?.pageCount ?: return
        val target = (pageIndex + delta).coerceIn(0, pageCount - 1)
        if (target == pageIndex) return
        pageIndex = target
        loadPage(uri, target)
    }

    fun addRect(rect: RectF) {
        val current = _coverRects.value
        val forPage = current[pageIndex].orEmpty() + rect
        _coverRects.value = current + (pageIndex to forPage)
    }

    fun undoLastRectOnCurrentPage() {
        val current = _coverRects.value
        val forPage = current[pageIndex].orEmpty()
        if (forPage.isEmpty()) return
        _coverRects.value = current + (pageIndex to forPage.dropLast(1))
    }

    fun clearRectsOnCurrentPage() {
        _coverRects.value = _coverRects.value + (pageIndex to emptyList())
    }

    fun removeOwnWatermark(outputUri: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF first")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.removeOwnWatermark(input, outputUri) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Could not remove watermark") }
            )
        }
    }

    fun applyCover(outputUri: Uri) {
        val input = _selectedFile.value
        val rects = _coverRects.value.filterValues { it.isNotEmpty() }
        when {
            input == null -> _state.value = Resource.Error("Select a PDF first")
            rects.isEmpty() -> _state.value = Resource.Error("Draw at least one box to cover")
            else -> viewModelScope.launch {
                _state.value = Resource.Loading(0f)
                val result = pdfEngine.coverWatermark(input, outputUri, rects) { progress ->
                    _state.value = Resource.Loading(progress)
                }
                _state.value = result.fold(
                    onSuccess = { Resource.Success(outputUri) },
                    onFailure = { Resource.Error(it.message ?: "Could not cover the selected area") }
                )
            }
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
