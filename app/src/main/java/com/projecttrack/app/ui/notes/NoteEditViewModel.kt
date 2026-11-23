package com.projecttrack.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditUiState(
    val isNew: Boolean = true,
    val isNodeNote: Boolean = false,
    val isLoading: Boolean = false,
    val content: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
)

class NoteEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: 0L
    private val nodeId: Long? = (savedStateHandle.get<Long>("nodeId") ?: 0L).takeIf { it > 0 }
    private val noteId: Long = savedStateHandle.get<Long>("noteId") ?: 0L

    private val _uiState = MutableStateFlow(
        NoteEditUiState(
            isNew = noteId <= 0,
            isNodeNote = nodeId != null,
            isLoading = noteId > 0,
        ),
    )
    val uiState: StateFlow<NoteEditUiState> = _uiState.asStateFlow()

    init {
        if (noteId > 0) {
            viewModelScope.launch {
                runCatching { repository.getNote(noteId) }
                    .onSuccess { note ->
                        _uiState.update {
                            if (note == null) {
                                it.copy(isLoading = false, loadError = "记录不存在或已被删除")
                            } else {
                                it.copy(isLoading = false, content = note.content)
                            }
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(isLoading = false, loadError = error.message ?: "读取记录失败")
                        }
                    }
            }
        }
    }

    fun onContentChange(value: String) {
        _uiState.update { it.copy(content = value, saveError = null) }
    }

    fun save() {
        val snapshot = _uiState.value
        if (snapshot.isSaving || snapshot.saved) return
        if (snapshot.content.trim().isEmpty()) {
            _uiState.update { it.copy(saveError = "请输入记录内容") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                if (noteId > 0) {
                    repository.updateNote(noteId, snapshot.content)
                } else {
                    require(projectId > 0) { "缺少所属项目" }
                    repository.createNote(
                        projectId = projectId,
                        nodeId = nodeId,
                        content = snapshot.content,
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, saveError = error.message ?: "保存失败，请重试")
                }
            }
        }
    }
}
