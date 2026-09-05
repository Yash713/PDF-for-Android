package com.pdfmaster.app.presentation.screens.sign

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class SignaturePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

@HiltViewModel
class SignViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _currentPage = MutableStateFlow<RenderedPage?>(null)
    val currentPage: StateFlow<RenderedPage?> = _currentPage.asStateFlow()

    private val _signatureBitmap = MutableStateFlow<Bitmap?>(null)
    val signatureBitmap: StateFlow<Bitmap?> = _signatureBitmap.asStateFlow()

    private val _placement = MutableStateFlow<SignaturePlacement?>(null)
    val placement: StateFlow<SignaturePlacement?> = _placement.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    private var pageIndex = 0

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _currentPage.value = null
        _placement.value = null
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
        _placement.value = null
        loadPage(uri, target)
    }

    fun setSignatureBitmap(bitmap: Bitmap?) {
        _signatureBitmap.value = bitmap
        _placement.value = null
    }

    fun placeAt(x: Float, y: Float) {
        val bitmap = _signatureBitmap.value ?: return
        val defaultWidth = 150f
        val defaultHeight = defaultWidth * bitmap.height / bitmap.width.toFloat()
        _placement.value = SignaturePlacement(x - defaultWidth / 2f, y - defaultHeight / 2f, defaultWidth, defaultHeight)
    }

    fun movePlacement(x: Float, y: Float) {
        _placement.value = _placement.value?.copy(x = x, y = y)
    }

    fun apply(outputUri: Uri) {
        val input = _selectedFile.value
        val bitmap = _signatureBitmap.value
        val place = _placement.value
        val page = _currentPage.value
        when {
            input == null -> _state.value = Resource.Error("Select a PDF first")
            bitmap == null -> _state.value = Resource.Error("Draw or upload a signature first")
            place == null || page == null -> _state.value = Resource.Error("Tap the page to place your signature")
            else -> viewModelScope.launch {
                _state.value = Resource.Loading(0f)
                val result = pdfEngine.signPdf(
                    input, outputUri, page.pageIndex, bitmap,
                    place.x, place.y, place.width, place.height
                ) { progress -> _state.value = Resource.Loading(progress) }
                _state.value = result.fold(
                    onSuccess = { Resource.Success(outputUri) },
                    onFailure = { Resource.Error(it.message ?: "Could not sign PDF") }
                )
            }
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
