package com.projecttrack.app.ui.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projecttrack.app.data.model.ProjectSummary
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.EmptyState
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.components.LoadingState
import com.projecttrack.app.ui.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onCreateProject: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenAllFiles: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: ProjectListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ProjectTrack", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "本地项目进度跟踪",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAllFiles) {
                        Icon(Icons.Default.List, contentDescription = "全部文件")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProject,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建项目") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            BackupEntryCard(onClick = onOpenBackup)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> ErrorState(message = uiState.error.orEmpty())
                    uiState.projects.isEmpty() -> EmptyState(
                        title = "还没有项目",
                        description = "创建一个项目，然后随时记录进度节点、文字记录和文件资料。所有数据只保存在本机。",
                        actionLabel = "新建项目",
                        onAction = onCreateProject,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    ) {
                        items(items = uiState.projects, key = { it.project.id }) { summary ->
                            ProjectSummaryCard(
                                summary = summary,
                                onClick = { onOpenProject(summary.project.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "备份与恢复",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "导出完整本地备份，或恢复项目副本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectSummaryCard(
    summary: ProjectSummary,
    onClick: () -> Unit,
) {
    val project = summary.project
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (summary.latestNodeTitle == null) {
                Text(
                    text = "暂无进度记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前状态：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = summary.latestNodeTitle.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    summary.latestNodeOccurredAt?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Formatters.dateTime(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "更新于 ${Formatters.dateTime(project.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
