package com.pdfmaster.app.presentation.screens.split

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
class SplitViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _pageCount = MutableStateFlow<Int?>(null)
    val pageCount: StateFlow<Int?> = _pageCount.asStateFlow()

    private val _rangeInput = MutableStateFlow("")
    val rangeInput: StateFlow<String> = _rangeInput.asStateFlow()

    private val _state = MutableStateFlow<Resource<List<Uri>>>(Resource.Idle)
    val state: StateFlow<Resource<List<Uri>>> = _state.asStateFlow()

    fun displayName(uri: Uri): String = context.displayNameOf(uri)

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _pageCount.value = null
        _rangeInput.value = ""
        if (uri == null) return
        viewModelScope.launch {
            pdfEngine.getPageCount(uri).fold(
                onSuccess = { _pageCount.value = it },
                onFailure = { _state.value = Resource.Error(it.message ?: "Could not read PDF") }
            )
        }
    }

    fun setRangeInput(value: String) {
        _rangeInput.value = value
    }

    fun split(outputDir: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF to split")
            return
        }
        val ranges = parsePageRanges(_rangeInput.value, _pageCount.value)
        if (ranges == null) {
            _state.value = Resource.Error("Enter valid page ranges, e.g. 1-3, 5, 7-10")
            return
        }

        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.splitPdf(input, ranges, outputDir) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it.message ?: "Split failed") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }

    /**
     * Parses a comma-separated, 1-indexed, inclusive range string ("1-3, 5, 7-10")
     * into 0-indexed IntRanges - one range per output file, matching what
     * PdfEngine.splitPdf expects. Returns null on any malformed or out-of-bounds token.
     */
    private fun parsePageRanges(input: String, totalPages: Int?): List<IntRange>? {
        val tokens = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val ranges = mutableListOf<IntRange>()
        for (token in tokens) {
            val range = if ("-" in token) {
                val parts = token.split("-").map { it.trim() }
                if (parts.size != 2) return null
                val start = parts[0].toIntOrNull() ?: return null
                val end = parts[1].toIntOrNull() ?: return null
                if (start < 1 || start > end) return null
                (start - 1)..(end - 1)
            } else {
                val page = token.toIntOrNull() ?: return null
                if (page < 1) return null
                (page - 1)..(page - 1)
            }
            if (totalPages != null && range.last >= totalPages) return null
            ranges += range
        }
        return ranges
    }
}
