package com.pdfmaster.app.presentation.screens.organize

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import com.pdfmaster.app.presentation.util.PdfPagePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [originalIndex] is the page's 0-indexed position in the SOURCE document -
 * kept stable across reordering so `apply()` can tell PdfEngine exactly which
 * source pages to keep, and in what order, in one list. */
data class OrganizePageItem(val originalIndex: Int, val thumbnail: Bitmap)

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _pages = MutableStateFlow<List<OrganizePageItem>>(emptyList())
    val pages: StateFlow<List<OrganizePageItem>> = _pages.asStateFlow()

    private val _loadingThumbnails = MutableStateFlow(false)
    val loadingThumbnails: StateFlow<Boolean> = _loadingThumbnails.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _pages.value = emptyList()
        if (uri == null) return
        viewModelScope.launch {
            _loadingThumbnails.value = true
            val thumbnails = PdfPagePreview.renderAllThumbnails(context, uri)
            _pages.value = thumbnails.mapIndexed { index, bitmap -> OrganizePageItem(index, bitmap) }
            _loadingThumbnails.value = false
        }
    }

    fun movePage(from: Int, to: Int) {
        val current = _pages.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _pages.value = mutable
    }

    fun deletePage(originalIndex: Int) {
        _pages.value = _pages.value.filterNot { it.originalIndex == originalIndex }
    }

    fun apply(outputUri: Uri) {
        val input = _selectedFile.value
        val order = _pages.value.map { it.originalIndex }
        when {
            input == null -> _state.value = Resource.Error("Select a PDF first")
            order.isEmpty() -> _state.value = Resource.Error("At least one page must remain")
            else -> viewModelScope.launch {
                _state.value = Resource.Loading(0f)
                val result = pdfEngine.organizePdf(input, outputUri, order) { progress ->
                    _state.value = Resource.Loading(progress)
                }
                _state.value = result.fold(
                    onSuccess = { Resource.Success(outputUri) },
                    onFailure = { Resource.Error(it.message ?: "Could not organize PDF") }
                )
            }
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
