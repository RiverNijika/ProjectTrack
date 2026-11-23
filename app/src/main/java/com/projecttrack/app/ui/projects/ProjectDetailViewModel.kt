package com.projecttrack.app.ui.projects

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.file.FileImportManager
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.Note
import com.projecttrack.app.data.model.ProgressNode
import com.projecttrack.app.data.model.Project
import com.projecttrack.app.data.repository.FileRepository
import com.projecttrack.app.data.repository.ProjectRepository
import com.projecttrack.app.domain.LatestNodeSelector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val isLoading: Boolean = true,
    val project: Project? = null,
    val nodes: List<ProgressNode> = emptyList(),
    val latestNode: ProgressNode? = null,
    val notes: List<Note> = emptyList(),
    val files: List<FileRow> = emptyList(),
    val deleted: Boolean = false,
    val error: String? = null,
)

class ProjectDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    fileRepository: FileRepository,
    private val importManager: FileImportManager,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: 0L

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeProject(projectId),
                repository.observeNodes(projectId),
                repository.observeProjectNotes(projectId),
                fileRepository.observeProjectFiles(projectId),
            ) { project, nodes, notes, files ->
                ProjectDetailUiState(
                    isLoading = false,
                    project = project,
                    nodes = nodes,
                    latestNode = LatestNodeSelector.select(nodes),
                    notes = notes,
                    files = files,
                )
            }.catch { error ->
                emit(
                    ProjectDetailUiState(
                        isLoading = false,
                        error = error.message ?: "读取项目详情失败",
                    ),
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteProject() {
        viewModelScope.launch {
            runCatching { repository.deleteProject(projectId) }
                .onSuccess { failedFiles ->
                    if (failedFiles > 0) {
                        _messages.send("项目已删除，但有 $failedFiles 个文件未能删除，会在后续清理中处理")
                    }
                    _uiState.update { it.copy(deleted = true) }
                }
                .onFailure { error ->
                    _messages.send(error.message ?: "删除项目失败，请重试")
                }
        }
    }

    fun deleteNode(nodeId: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteNode(nodeId)
            _messages.send(
                if (deleted) {
                    "节点已删除，相关记录和文件已保留为未关联内容"
                } else {
                    "节点不存在或已被删除"
                },
            )
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(noteId) }
                .onSuccess { _messages.send("记录已删除") }
                .onFailure { error -> _messages.send(error.message ?: "删除记录失败") }
        }
    }

    fun importFiles(uris: List<Uri>) {
        importManager.importFiles(projectId = projectId, nodeId = null, uris = uris)
    }
}
