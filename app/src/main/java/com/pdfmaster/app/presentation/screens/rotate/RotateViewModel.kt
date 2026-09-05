package com.pdfmaster.app.presentation.screens.rotate

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class RotateViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _pageCount = MutableStateFlow<Int?>(null)
    val pageCount: StateFlow<Int?> = _pageCount.asStateFlow()

    private val _preview = MutableStateFlow<RenderedPage?>(null)
    val preview: StateFlow<RenderedPage?> = _preview.asStateFlow()

    private val _angle = MutableStateFlow(90)
    val angle: StateFlow<Int> = _angle.asStateFlow()

    private val _applyToAll = MutableStateFlow(true)
    val applyToAll: StateFlow<Boolean> = _applyToAll.asStateFlow()

    private val _pageListInput = MutableStateFlow("")
    val pageListInput: StateFlow<String> = _pageListInput.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun displayName(uri: Uri): String = context.displayNameOf(uri)

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _pageCount.value = null
        _preview.value = null
        if (uri == null) return
        viewModelScope.launch {
            pdfEngine.getPageCount(uri).onSuccess { _pageCount.value = it }
            _preview.value = PdfPagePreview.render(context, uri, pageIndex = 0, targetWidthPx = 700)
        }
    }

    fun setAngle(value: Int) {
        _angle.value = value
    }

    fun setApplyToAll(value: Boolean) {
        _applyToAll.value = value
    }

    fun setPageListInput(value: String) {
        _pageListInput.value = value
    }

    fun rotate(outputUri: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF first")
            return
        }

        val pageIndices: List<Int>? = if (_applyToAll.value) {
            null
        } else {
            val parsed = parsePageList(_pageListInput.value, _pageCount.value)
            if (parsed == null) {
                _state.value = Resource.Error("Enter valid page numbers, e.g. 1, 3, 5-6")
                return
            }
            parsed
        }

        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.rotatePdf(input, outputUri, _angle.value, pageIndices) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Could not rotate PDF") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }

    private fun parsePageList(input: String, totalPages: Int?): List<Int>? {
        val tokens = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val pages = mutableListOf<Int>()
        for (token in tokens) {
            if ("-" in token) {
                val parts = token.split("-").map { it.trim() }
                if (parts.size != 2) return null
                val start = parts[0].toIntOrNull() ?: return null
                val end = parts[1].toIntOrNull() ?: return null
                if (start < 1 || start > end) return null
                for (p in start..end) pages += p - 1
            } else {
                val page = token.toIntOrNull() ?: return null
                if (page < 1) return null
                pages += page - 1
            }
        }
        if (totalPages != null && pages.any { it >= totalPages }) return null
        return pages
    }
}
