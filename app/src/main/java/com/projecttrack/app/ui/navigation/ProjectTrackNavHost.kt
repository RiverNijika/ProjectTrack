package com.projecttrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.projecttrack.app.ui.backup.BackupScreen
import com.projecttrack.app.ui.files.FilesScreen
import com.projecttrack.app.ui.nodes.NodeDetailScreen
import com.projecttrack.app.ui.nodes.NodeEditScreen
import com.projecttrack.app.ui.notes.NoteEditScreen
import com.projecttrack.app.ui.preview.FilePreviewScreen
import com.projecttrack.app.ui.projects.ProjectDetailScreen
import com.projecttrack.app.ui.projects.ProjectEditScreen
import com.projecttrack.app.ui.projects.ProjectListScreen

/** 应用内路由。 */
object Routes {
    const val PROJECT_LIST = "projects"

    const val PROJECT_DETAIL = "projects/{projectId}"
    fun projectDetail(projectId: Long): String = "projects/$projectId"

    const val PROJECT_EDIT = "project_edit?projectId={projectId}"
    fun projectEdit(projectId: Long?): String = "project_edit?projectId=${projectId ?: 0L}"

    const val PROJECT_FILES = "projects/{projectId}/files"
    fun projectFiles(projectId: Long): String = "projects/$projectId/files"

    const val GLOBAL_FILES = "files"

    const val BACKUP = "backup"

    const val NODE_DETAIL = "nodes/{nodeId}"
    fun nodeDetail(nodeId: Long): String = "nodes/$nodeId"

    const val NODE_EDIT = "node_edit?projectId={projectId}&nodeId={nodeId}"
    fun nodeEdit(projectId: Long, nodeId: Long?): String =
        "node_edit?projectId=$projectId&nodeId=${nodeId ?: 0L}"

    const val NOTE_EDIT = "note_edit?projectId={projectId}&nodeId={nodeId}&noteId={noteId}"
    fun noteEdit(projectId: Long, nodeId: Long?, noteId: Long?): String =
        "note_edit?projectId=$projectId&nodeId=${nodeId ?: 0L}&noteId=${noteId ?: 0L}"

    const val PREVIEW = "preview/{fileId}"
    fun preview(fileId: Long): String = "preview/$fileId"
}

@Composable
fun ProjectTrackNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.PROJECT_LIST,
        modifier = modifier,
    ) {
        composable(Routes.PROJECT_LIST) {
            ProjectListScreen(
                onCreateProject = { navController.navigate(Routes.projectEdit(null)) },
                onOpenProject = { projectId ->
                    navController.navigate(Routes.projectDetail(projectId))
                },
                onOpenAllFiles = { navController.navigate(Routes.GLOBAL_FILES) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
            )
        }

        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.PROJECT_EDIT,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            ProjectEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROJECT_DETAIL,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
        ) { entry ->
            val projectId = entry.arguments?.getLong("projectId") ?: 0L
            ProjectDetailScreen(
                onBack = { navController.popBackStack() },
                onEditProject = { navController.navigate(Routes.projectEdit(projectId)) },
                onAddNode = { navController.navigate(Routes.nodeEdit(projectId, null)) },
                onOpenNode = { nodeId -> navController.navigate(Routes.nodeDetail(nodeId)) },
                onEditNode = { nodeId -> navController.navigate(Routes.nodeEdit(projectId, nodeId)) },
                onAddNote = { pid, nid -> navController.navigate(Routes.noteEdit(pid, nid, null)) },
                onEditNote = { pid, nid, noteId ->
                    navController.navigate(Routes.noteEdit(pid, nid, noteId))
                },
                onOpenProjectFiles = { navController.navigate(Routes.projectFiles(projectId)) },
                onOpenPreview = { fileId -> navController.navigate(Routes.preview(fileId)) },
                onProjectDeleted = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROJECT_FILES,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
        ) {
            FilesScreen(
                onBack = { navController.popBackStack() },
                onOpenPreview = { fileId -> navController.navigate(Routes.preview(fileId)) },
            )
        }

        composable(Routes.GLOBAL_FILES) {
            FilesScreen(
                onBack = { navController.popBackStack() },
                onOpenPreview = { fileId -> navController.navigate(Routes.preview(fileId)) },
            )
        }

        composable(
            route = Routes.NODE_DETAIL,
            arguments = listOf(navArgument("nodeId") { type = NavType.LongType }),
        ) { entry ->
            val nodeId = entry.arguments?.getLong("nodeId") ?: 0L
            NodeDetailScreen(
                onBack = { navController.popBackStack() },
                onEditNode = { projectId -> navController.navigate(Routes.nodeEdit(projectId, nodeId)) },
                onAddNote = { pid, nid -> navController.navigate(Routes.noteEdit(pid, nid, null)) },
                onEditNote = { pid, nid, noteId ->
                    navController.navigate(Routes.noteEdit(pid, nid, noteId))
                },
                onOpenPreview = { fileId -> navController.navigate(Routes.preview(fileId)) },
                onDeleted = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.NODE_EDIT,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("nodeId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            NodeEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.NOTE_EDIT,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("nodeId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("noteId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            NoteEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType }),
        ) {
            FilePreviewScreen(onBack = { navController.popBackStack() })
        }
    }
}
