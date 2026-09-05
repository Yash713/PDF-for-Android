package com.pdfmaster.app.presentation.screens.compare

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.PdfComparisonResult
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
class CompareViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _firstFile = MutableStateFlow<Uri?>(null)
    val firstFile: StateFlow<Uri?> = _firstFile.asStateFlow()

    private val _secondFile = MutableStateFlow<Uri?>(null)
    val secondFile: StateFlow<Uri?> = _secondFile.asStateFlow()

    private val _comparisonState = MutableStateFlow<Resource<PdfComparisonResult>>(Resource.Idle)
    val comparisonState: StateFlow<Resource<PdfComparisonResult>> = _comparisonState.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private val _firstPagePreview = MutableStateFlow<RenderedPage?>(null)
    val firstPagePreview: StateFlow<RenderedPage?> = _firstPagePreview.asStateFlow()

    private val _secondPagePreview = MutableStateFlow<RenderedPage?>(null)
    val secondPagePreview: StateFlow<RenderedPage?> = _secondPagePreview.asStateFlow()

    fun selectFirst(uri: Uri?) {
        _firstFile.value = uri
        resetComparison()
    }

    fun selectSecond(uri: Uri?) {
        _secondFile.value = uri
        resetComparison()
    }

    private fun resetComparison() {
        _comparisonState.value = Resource.Idle
        _pageIndex.value = 0
        _firstPagePreview.value = null
        _secondPagePreview.value = null
    }

    fun compare() {
        val first = _firstFile.value
        val second = _secondFile.value
        if (first == null || second == null) {
            _comparisonState.value = Resource.Error("Select both PDFs first")
            return
        }
        viewModelScope.launch {
            _comparisonState.value = Resource.Loading(0f)
            val result = pdfEngine.comparePdf(first, second) { progress ->
                _comparisonState.value = Resource.Loading(progress)
            }
            _comparisonState.value = result.fold(
                onSuccess = { comparisonResult ->
                    loadPagePreviews(0)
                    Resource.Success(comparisonResult)
                },
                onFailure = { Resource.Error(it.message ?: "Comparison failed") }
            )
        }
    }

    private fun loadPagePreviews(index: Int) {
        val first = _firstFile.value ?: return
        val second = _secondFile.value ?: return
        viewModelScope.launch {
            _firstPagePreview.value = PdfPagePreview.render(context, first, index, targetWidthPx = 500)
            _secondPagePreview.value = PdfPagePreview.render(context, second, index, targetWidthPx = 500)
        }
    }

    fun goToPage(delta: Int) {
        val result = (_comparisonState.value as? Resource.Success)?.data ?: return
        val target = (_pageIndex.value + delta).coerceIn(0, result.comparedPageCount - 1)
        if (target == _pageIndex.value) return
        _pageIndex.value = target
        loadPagePreviews(target)
    }
}
