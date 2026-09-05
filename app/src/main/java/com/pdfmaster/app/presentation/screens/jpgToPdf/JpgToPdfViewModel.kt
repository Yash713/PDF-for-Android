package com.pdfmaster.app.presentation.screens.jpgToPdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JpgToPdfViewModel @Inject constructor(
    private val pdfEngine: PdfEngine
) : ViewModel() {

    private val _images = MutableStateFlow<List<Uri>>(emptyList())
    val images: StateFlow<List<Uri>> = _images.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun addImages(uris: List<Uri>) {
        val existing = _images.value
        _images.value = existing + uris.filterNot { it in existing }
    }

    fun removeImage(uri: Uri) {
        _images.value = _images.value.filterNot { it == uri }
    }

    fun moveImage(from: Int, to: Int) {
        val current = _images.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _images.value = mutable
    }

    fun convert(outputUri: Uri) {
        if (_images.value.isEmpty()) {
            _state.value = Resource.Error("Select at least one image")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.jpgToPdf(_images.value, outputUri) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(it.message ?: "Conversion failed") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
