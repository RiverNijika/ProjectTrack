package com.projecttrack.app.ui.nodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NodeEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val title: String = "",
    val description: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val titleError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
)

class NodeEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: 0L
    private val nodeId: Long = savedStateHandle.get<Long>("nodeId") ?: 0L

    private val _uiState = MutableStateFlow(
        NodeEditUiState(
            isNew = nodeId <= 0,
            isLoading = nodeId > 0,
        ),
    )
    val uiState: StateFlow<NodeEditUiState> = _uiState.asStateFlow()

    init {
        if (nodeId > 0) {
            viewModelScope.launch {
                runCatching { repository.getNode(nodeId) }
                    .onSuccess { node ->
                        _uiState.update {
                            if (node == null) {
                                it.copy(isLoading = false, loadError = "节点不存在或已被删除")
                            } else {
                                it.copy(
                                    isLoading = false,
                                    title = node.title,
                                    description = node.description,
                                    occurredAt = node.occurredAt,
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(isLoading = false, loadError = error.message ?: "读取节点失败")
                        }
                    }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, titleError = null, saveError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, saveError = null) }
    }

    fun onOccurredAtChange(value: Long) {
        _uiState.update { it.copy(occurredAt = value, saveError = null) }
    }

    fun save() {
        val snapshot = _uiState.value
        if (snapshot.isSaving || snapshot.saved) return
        if (snapshot.title.trim().isEmpty()) {
            _uiState.update { it.copy(titleError = "请输入节点标题") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                if (nodeId > 0) {
                    repository.updateNode(
                        nodeId = nodeId,
                        title = snapshot.title,
                        description = snapshot.description,
                        occurredAt = snapshot.occurredAt,
                    )
                } else {
                    require(projectId > 0) { "缺少所属项目" }
                    repository.createNode(
                        projectId = projectId,
                        title = snapshot.title,
                        description = snapshot.description,
                        occurredAt = snapshot.occurredAt,
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
