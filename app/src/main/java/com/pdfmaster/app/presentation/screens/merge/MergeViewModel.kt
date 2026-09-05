package com.pdfmaster.app.presentation.screens.merge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pdfmaster.app.data.worker.PdfProcessingWorker
import com.pdfmaster.app.domain.model.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MergeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _files = MutableStateFlow<List<Uri>>(emptyList())
    val files: StateFlow<List<Uri>> = _files.asStateFlow()

    private val _state = MutableStateFlow<Resource<Uri>>(Resource.Idle)
    val state: StateFlow<Resource<Uri>> = _state.asStateFlow()

    fun addFiles(uris: List<Uri>) {
        val existing = _files.value
        _files.value = existing + uris.filterNot { it in existing }
    }

    fun removeFile(uri: Uri) {
        _files.value = _files.value.filterNot { it == uri }
    }

    fun moveFile(from: Int, to: Int) {
        val current = _files.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _files.value = mutable
    }

    fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: (uri.lastPathSegment ?: "document.pdf")

    fun merge(outputUri: Uri) {
        if (_files.value.size < 2) {
            _state.value = Resource.Error("Select at least 2 PDFs to merge")
            return
        }
        _state.value = Resource.Loading(0f)
        val request = PdfProcessingWorker.mergeRequest(_files.value, outputUri)
        workManager.enqueue(request)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) return@collect
                _state.value = when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED ->
                        Resource.Loading(info.progress.getFloat(PdfProcessingWorker.KEY_PROGRESS, 0f))
                    WorkInfo.State.SUCCEEDED -> Resource.Success(outputUri)
                    WorkInfo.State.FAILED ->
                        Resource.Error(info.outputData.getString(PdfProcessingWorker.KEY_ERROR) ?: "Merge failed")
                    WorkInfo.State.CANCELLED -> Resource.Error("Merge cancelled")
                    WorkInfo.State.BLOCKED -> _state.value
                }
            }
        }
    }

    fun resetState() {
        _state.value = Resource.Idle
    }
}
