package com.projecttrack.app.ui.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.file.TextPreviewResult
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilePreviewUiState(
    val isLoading: Boolean = true,
    val file: FileRow? = null,
    val missing: Boolean = false,
    val textPreview: TextPreviewResult? = null,
    val isTextLoading: Boolean = false,
    val error: String? = null,
)

class FilePreviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: 0L

    private val _uiState = MutableStateFlow(FilePreviewUiState())
    val uiState: StateFlow<FilePreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            fileRepository.observeFile(fileId)
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "读取文件信息失败")
                    }
                }
                .collect { row ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            file = row,
                            missing = row == null || !row.exists,
                            error = if (row == null) "文件记录不存在" else it.error,
                        )
                    }
                }
        }
    }

    fun loadTextPreview() {
        if (_uiState.value.isTextLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTextLoading = true) }
            val result = runCatching { fileRepository.readTextPreview(fileId) }.getOrNull()
            _uiState.update {
                it.copy(
                    isTextLoading = false,
                    textPreview = result ?: TextPreviewResult.Failure("无法读取文件内容"),
                )
            }
        }
    }

    fun retryTextPreview() {
        _uiState.update { it.copy(textPreview = null) }
        loadTextPreview()
    }
}
