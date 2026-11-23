package com.projecttrack.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.projecttrack.app.ProjectTrackApplication
import com.projecttrack.app.ui.files.FilesViewModel
import com.projecttrack.app.ui.nodes.NodeDetailViewModel
import com.projecttrack.app.ui.nodes.NodeEditViewModel
import com.projecttrack.app.ui.notes.NoteEditViewModel
import com.projecttrack.app.ui.preview.FilePreviewViewModel
import com.projecttrack.app.ui.projects.ProjectDetailViewModel
import com.projecttrack.app.ui.projects.ProjectEditViewModel
import com.projecttrack.app.ui.projects.ProjectListViewModel

/**
 * 手工依赖注入的 ViewModel 工厂：从应用容器取出仓库与导入管理器。
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            ProjectListViewModel(projectTrackApplication().container.projectRepository)
        }
        initializer {
            ProjectEditViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = projectTrackApplication().container.projectRepository,
            )
        }
        initializer {
            val container = projectTrackApplication().container
            ProjectDetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = container.projectRepository,
                fileRepository = container.fileRepository,
                importManager = container.importManager,
            )
        }
        initializer {
            val container = projectTrackApplication().container
            NodeDetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = container.projectRepository,
                fileRepository = container.fileRepository,
                importManager = container.importManager,
            )
        }
        initializer {
            NodeEditViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = projectTrackApplication().container.projectRepository,
            )
        }
        initializer {
            NoteEditViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = projectTrackApplication().container.projectRepository,
            )
        }
        initializer {
            val container = projectTrackApplication().container
            FilesViewModel(
                savedStateHandle = createSavedStateHandle(),
                projectRepository = container.projectRepository,
                fileRepository = container.fileRepository,
                importManager = container.importManager,
            )
        }
        initializer {
            FilePreviewViewModel(
                savedStateHandle = createSavedStateHandle(),
                fileRepository = projectTrackApplication().container.fileRepository,
            )
        }
    }

    private fun CreationExtras.projectTrackApplication(): ProjectTrackApplication =
        this[APPLICATION_KEY] as ProjectTrackApplication
}
