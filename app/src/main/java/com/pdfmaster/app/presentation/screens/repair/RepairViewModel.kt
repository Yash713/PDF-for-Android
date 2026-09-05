package com.pdfmaster.app.presentation.screens.repair

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.RepairOutcome
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
class RepairViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _state = MutableStateFlow<Resource<RepairOutcome>>(Resource.Idle)
    val state: StateFlow<Resource<RepairOutcome>> = _state.asStateFlow()

    fun displayName(uri: Uri): String = context.displayNameOf(uri)

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
        _state.value = Resource.Idle
    }

    fun repair(outputUri: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF first")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.repairPdf(input, outputUri) { progress ->
                _state.value = Resource.Loading(progress)
            }
            _state.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it.message ?: "Could not repair this PDF") }
            )
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
