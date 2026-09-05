package com.pdfmaster.app.presentation.screens.convert

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.data.remote.CloudConvertRepository
import com.pdfmaster.app.data.remote.OfficeFormat
import com.pdfmaster.app.domain.model.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConvertViewModel @Inject constructor(
    private val repository: CloudConvertRepository
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
    }

    fun convert(format: OfficeFormat) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF to convert")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = repository.convertPdfToOffice(input, format) { progress ->
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
