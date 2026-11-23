package com.projecttrack.app.ui.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.projecttrack.app.data.file.FileImportManager
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.FileQuery
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.FileSort
import com.projecttrack.app.data.model.NodeFilter
import com.projecttrack.app.data.model.NodeLabel
import com.projecttrack.app.data.model.ProjectOption
import com.projecttrack.app.data.repository.FileDeleteResult
import com.projecttrack.app.data.repository.FileRepository
import com.projecttrack.app.data.repository.ProjectRepository
import com.projecttrack.app.domain.FileFilterEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilesFilterUiState(
    val projectFilter: Long = 0L,
    val nodeFilter: NodeFilter = NodeFilter.All,
    val category: FileCategory? = null,
    val search: String = "",
    val sort: FileSort = FileSort.IMPORTED_DESC,
)

data class FilesUiState(
    val isLoading: Boolean = true,
    val isProjectScope: Boolean = false,
    val projectId: Long? = null,
    val projectName: String? = null,
    val projects: List<ProjectOption> = emptyList(),
    val nodeLabels: List<NodeLabel> = emptyList(),
    val filter: FilesFilterUiState = FilesFilterUiState(),
    val files: List<FileRow> = emptyList(),
    val totalFileCount: Int = 0,
    val hasActiveFilters: Boolean = false,
    val error: String? = null,
)

class FilesViewModel(
    savedStateHandle: SavedStateHandle,
    projectRepository: ProjectRepository,
    private val fileRepository: FileRepository,
    private val importManager: FileImportManager,
) : ViewModel() {

    private val projectId: Long? = (savedStateHandle.get<Long>("projectId") ?: 0L).takeIf { it > 0 }

    private val filterState = MutableStateFlow(FilesFilterUiState())

    private val filesFlow: Flow<List<FileRow>> = if (projectId != null) {
        fileRepository.observeProjectFiles(projectId)
    } else {
        fileRepository.observeAllFiles()
    }

    private val projectNameFlow: Flow<String?> = if (projectId != null) {
        projectRepository.observeProject(projectId).map { it?.name }
    } else {
        flowOf(null)
    }

    private val projectsFlow = projectRepository.observeProjectOptions()

    private val nodeLabelsFlow = projectRepository.observeNodeLabels(projectId)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<FilesUiState> = combine(
        filesFlow,
        projectNameFlow,
        projectsFlow,
        nodeLabelsFlow,
        filterState,
    ) { files, projectName, projects, labels, filter ->
        val effectiveProjectId = projectId ?: filter.projectFilter.takeIf { it > 0 }
        val visibleLabels = labels.filter {
            projectId != null || filter.projectFilter <= 0 || it.projectId == filter.projectFilter
        }
        val effectiveNodeFilter = when (val nodeFilter = filter.nodeFilter) {
            is NodeFilter.ById ->
                if (visibleLabels.any { it.nodeId == nodeFilter.nodeId }) nodeFilter else NodeFilter.All
            else -> nodeFilter
        }
        val query = FileQuery(
            projectId = effectiveProjectId,
            nodeFilter = effectiveNodeFilter,
            category = filter.category,
            search = filter.search,
            sort = filter.sort,
        )
        val filtered = FileFilterEngine.apply(files, query)
        val hasActiveFilters = effectiveProjectId != null ||
            effectiveNodeFilter != NodeFilter.All ||
            filter.category != null ||
            filter.search.isNotBlank()
        FilesUiState(
            isLoading = false,
            isProjectScope = projectId != null,
            projectId = projectId,
            projectName = projectName,
            projects = projects,
            nodeLabels = visibleLabels,
            filter = filter.copy(nodeFilter = effectiveNodeFilter),
            files = filtered,
            totalFileCount = files.size,
            hasActiveFilters = hasActiveFilters,
        )
    }.catch { error ->
        emit(
            FilesUiState(
                isLoading = false,
                isProjectScope = projectId != null,
                projectId = projectId,
                error = error.message ?: "读取文件列表失败",
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilesUiState(
            isLoading = true,
            isProjectScope = projectId != null,
            projectId = projectId,
        ),
    )

    fun onProjectFilterChange(value: Long) {
        filterState.update { it.copy(projectFilter = value, nodeFilter = NodeFilter.All) }
    }

    fun onNodeFilterChange(value: NodeFilter) {
        filterState.update { it.copy(nodeFilter = value) }
    }

    fun onCategoryChange(value: FileCategory?) {
        filterState.update { it.copy(category = value) }
    }

    fun onSearchChange(value: String) {
        filterState.update { it.copy(search = value) }
    }

    fun onSortChange(value: FileSort) {
        filterState.update { it.copy(sort = value) }
    }

    fun clearFilters() {
        filterState.update { FilesFilterUiState() }
    }

    fun deleteFile(fileId: Long) {
        viewModelScope.launch {
            when (fileRepository.deleteFile(fileId)) {
                FileDeleteResult.Deleted -> _messages.send("文件已删除")
                FileDeleteResult.FileDeleteFailed ->
                    _messages.send("删除文件失败，记录与文件均未删除，请稍后重试")
                FileDeleteResult.NotFound -> _messages.send("文件记录不存在")
            }
        }
    }

    /** 项目文件页直接导入到当前项目；全局文件页需要显式选择目标项目。 */
    fun importFiles(uris: List<Uri>) {
        val targetProjectId = projectId ?: return
        importManager.importFiles(projectId = targetProjectId, nodeId = null, uris = uris)
    }
}
