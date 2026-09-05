package com.pdfmaster.app.presentation.screens.unlock

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.model.Resource
import com.pdfmaster.app.domain.repository.PdfEngine
import com.pdfmaster.app.presentation.util.displayNameOf
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun displayName(uri: Uri): String = context.displayNameOf(uri)

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
    }

    fun unlock(outputUri: Uri, password: String) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF to unlock")
            return
        }
        viewModelScope.launch {
            _state.value = Resource.Loading(0f)
            val result = pdfEngine.unlockPdf(input, outputUri, password)
            _state.value = result.fold(
                onSuccess = { Resource.Success(outputUri) },
                onFailure = { Resource.Error(mapError(it)) }
            )
        }
    }

    private fun mapError(t: Throwable): String = when (t) {
        is InvalidPasswordException -> "Incorrect password"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Could not unlock PDF"
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
