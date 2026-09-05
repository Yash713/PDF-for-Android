package com.pdfmaster.app.presentation.screens.pdfToJpg

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import com.pdfmaster.app.presentation.util.displayNameOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfToJpgViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _pageCount = MutableStateFlow<Int?>(null)
    val pageCount: StateFlow<Int?> = _pageCount.asStateFlow()

    private val _dpi = MutableStateFlow(150)
    val dpi: StateFlow<Int> = _dpi.asStateFlow()

    private val _state = MutableStateFlow<Resource<List<Uri>>>(Resource.Idle)
    val state: StateFlow<Resource<List<Uri>>> = _state.asStateFlow()

    fun displayName(uri: Uri): String = context.displayNameOf(uri)

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _pageCount.value = null
        if (uri == null) return
        viewModelScope.launch {
            pdfEngine.getPageCount(uri).onSuccess { _pageCount.value = it }
        }
    }

    fun setDpi(value: Int) {
        _dpi.value = value
    }

    fun convert(outputDir: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF first")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.pdfToJpg(input, outputDir, dpi = _dpi.value, quality = 90) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it.message ?: "Conversion failed") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
