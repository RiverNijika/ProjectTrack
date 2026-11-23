package com.projecttrack.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projecttrack.app.data.model.ProjectSummary
import com.projecttrack.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProjectListUiState(
    val isLoading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    val error: String? = null,
)

class ProjectListViewModel(repository: ProjectRepository) : ViewModel() {
    val uiState: StateFlow<ProjectListUiState> = repository.observeProjectSummaries()
        .map { summaries -> ProjectListUiState(isLoading = false, projects = summaries) }
        .catch { error ->
            emit(ProjectListUiState(isLoading = false, error = error.message ?: "读取项目失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectListUiState(),
        )
}
