package com.pdfmaster.app.presentation.screens.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.EditElement
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
class EditViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _currentPage = MutableStateFlow<RenderedPage?>(null)
    val currentPage: StateFlow<RenderedPage?> = _currentPage.asStateFlow()

    private val _elements = MutableStateFlow<List<EditElement>>(emptyList())
    val elements: StateFlow<List<EditElement>> = _elements.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    private var pageIndex = 0

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _currentPage.value = null
        _elements.value = emptyList()
        pageIndex = 0
        if (uri == null) return
        loadPage(uri, 0)
    }

    private fun loadPage(uri: Uri, index: Int) {
        viewModelScope.launch {
            _currentPage.value = PdfPagePreview.render(context, uri, index, targetWidthPx = 1000)
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

    fun addElement(element: EditElement) {
        _elements.value = _elements.value + element
    }

    fun moveElement(index: Int, newX: Float, newY: Float) {
        val current = _elements.value.toMutableList()
        if (index !in current.indices) return
        current[index] = when (val element = current[index]) {
            is EditElement.TextElement -> element.copy(x = newX, y = newY)
            is EditElement.ImageElement -> element.copy(x = newX, y = newY)
            is EditElement.ShapeElement -> element.copy(x = newX, y = newY)
        }
        _elements.value = current
    }

    fun removeElement(index: Int) {
        _elements.value = _elements.value.filterIndexed { i, _ -> i != index }
    }

    fun apply(outputUri: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF first")
            return
        }
        if (_elements.value.isEmpty()) {
            _state.value = Resource.Error("Add at least one element before saving")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.editPdf(input, outputUri, _elements.value) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Could not save edits") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
