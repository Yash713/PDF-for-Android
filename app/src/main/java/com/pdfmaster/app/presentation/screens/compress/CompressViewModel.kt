package com.pdfmaster.app.presentation.screens.compress

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pdfmaster.app.data.worker.PdfProcessingWorker
import com.pdfmaster.app.domain.model.CompressQuality
import com.pdfmaster.app.domain.model.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompressViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _quality = MutableStateFlow(CompressQuality.MEDIUM)
    val quality: StateFlow<CompressQuality> = _quality.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun selectFile(uri: Uri?) {
        _selectedFile.value = uri
    }

    fun setQuality(quality: CompressQuality) {
        _quality.value = quality
    }

    fun compress(outputUri: Uri) {
        val input = _selectedFile.value
        if (input == null) {
            _state.value = Resource.Error("Select a PDF to compress")
            return
        }
        _state.value = Resource.Loading(0f)
        val request = PdfProcessingWorker.compressRequest(input, outputUri, _quality.value)
        workManager.enqueue(request)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) return@collect
                _state.value = when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED ->
                        Resource.Loading(info.progress.getFloat(PdfProcessingWorker.KEY_PROGRESS, 0f))
                    WorkInfo.State.SUCCEEDED -> Resource.Success(outputUri)
                    WorkInfo.State.FAILED ->
                        Resource.Error(info.outputData.getString(PdfProcessingWorker.KEY_ERROR) ?: "Compression failed")
                    WorkInfo.State.CANCELLED -> Resource.Error("Compression cancelled")
                    WorkInfo.State.BLOCKED -> _state.value
                }
            }
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
