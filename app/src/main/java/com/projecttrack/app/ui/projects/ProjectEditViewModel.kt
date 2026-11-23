package com.projecttrack.app.ui.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
)

class ProjectEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: 0L

    private val _uiState = MutableStateFlow(ProjectEditUiState(isNew = projectId <= 0))
    val uiState: StateFlow<ProjectEditUiState> = _uiState.asStateFlow()

    init {
        if (projectId > 0) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                runCatching { repository.getProject(projectId) }
                    .onSuccess { project ->
                        _uiState.update {
                            if (project == null) {
                                it.copy(isLoading = false, loadError = "项目不存在或已被删除")
                            } else {
                                it.copy(
                                    isLoading = false,
                                    name = project.name,
                                    description = project.description,
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(isLoading = false, loadError = error.message ?: "读取项目失败")
                        }
                    }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = null, saveError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, saveError = null) }
    }

    fun save() {
        val snapshot = _uiState.value
        if (snapshot.isSaving || snapshot.saved) return
        val trimmedName = snapshot.name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(nameError = "请输入项目名称") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                if (projectId > 0) {
                    repository.updateProject(projectId, trimmedName, snapshot.description)
                } else {
                    repository.createProject(trimmedName, snapshot.description)
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
