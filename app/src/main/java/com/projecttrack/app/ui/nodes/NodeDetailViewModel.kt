package com.projecttrack.app.ui.nodes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.file.FileImportManager
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.Note
import com.projecttrack.app.data.model.ProgressNode
import com.projecttrack.app.data.repository.FileRepository
import com.projecttrack.app.data.repository.ProjectRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NodeDetailUiState(
    val isLoading: Boolean = true,
    val node: ProgressNode? = null,
    val projectName: String? = null,
    val notes: List<Note> = emptyList(),
    val files: List<FileRow> = emptyList(),
    val deleted: Boolean = false,
    val error: String? = null,
)

class NodeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    fileRepository: FileRepository,
    private val importManager: FileImportManager,
) : ViewModel() {

    private val nodeId: Long = savedStateHandle.get<Long>("nodeId") ?: 0L

    private val _uiState = MutableStateFlow(NodeDetailUiState())
    val uiState: StateFlow<NodeDetailUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val projectNameFlow: Flow<String?> = repository.observeNode(nodeId).flatMapLatest { node ->
        if (node == null) {
            flowOf(null)
        } else {
            repository.observeProject(node.projectId).map { it?.name }
        }
    }

    init {
        viewModelScope.launch {
            combine(
                repository.observeNode(nodeId),
                projectNameFlow,
                repository.observeNodeNotes(nodeId),
                fileRepository.observeNodeFiles(nodeId),
            ) { node, projectName, notes, files ->
                NodeDetailUiState(
                    isLoading = false,
                    node = node,
                    projectName = projectName,
                    notes = notes,
                    files = files,
                )
            }.catch { error ->
                emit(
                    NodeDetailUiState(
                        isLoading = false,
                        error = error.message ?: "读取节点详情失败",
                    ),
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteNode() {
        viewModelScope.launch {
            val deleted = repository.deleteNode(nodeId)
            if (deleted) {
                _messages.send("节点已删除，相关记录和文件已保留为未关联内容")
                _uiState.update { it.copy(deleted = true) }
            } else {
                _messages.send("节点不存在或已被删除")
            }
        }
    }

    fun importFiles(uris: List<Uri>) {
        val projectId = _uiState.value.node?.projectId ?: return
        importManager.importFiles(projectId = projectId, nodeId = nodeId, uris = uris)
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(noteId) }
                .onSuccess { _messages.send("记录已删除") }
                .onFailure { error -> _messages.send(error.message ?: "删除记录失败") }
        }
    }
}
