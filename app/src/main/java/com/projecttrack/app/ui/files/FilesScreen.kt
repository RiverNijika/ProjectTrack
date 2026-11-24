package com.projecttrack.app.ui.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projecttrack.app.ProjectTrackApplication
import com.projecttrack.app.data.file.FileTypeClassifier
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.FileSort
import com.projecttrack.app.data.model.NodeFilter
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.CategoryFilterRow
import com.projecttrack.app.ui.components.ConfirmDialog
import com.projecttrack.app.ui.components.DropdownFilter
import com.projecttrack.app.ui.components.EmptyState
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.components.FileRowCard
import com.projecttrack.app.ui.components.FileSearchField
import com.projecttrack.app.ui.components.LoadingState
import com.projecttrack.app.ui.components.rememberFileExternalOpener

/**
 * 文件浏览器。项目内文件页与跨项目文件页共用，通过 [FilesViewModel] 读取路由参数区分。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    viewModel: FilesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val externalOpener = rememberFileExternalOpener(snackbarHostState)
    val context = LocalContext.current
    val importManager = remember(context) {
        (context.applicationContext as ProjectTrackApplication).container.importManager
    }

    var fileToDelete by remember { mutableStateOf<FileRow?>(null) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (uiState.isProjectScope) {
                viewModel.importFiles(uris)
            } else {
                pendingUris = uris
                showImportDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.isProjectScope) {
                                "项目文件"
                            } else {
                                "全部文件"
                            },
                        )
                        Text(
                            text = if (uiState.isProjectScope) {
                                uiState.projectName ?: "当前项目"
                            } else {
                                "跨所有项目"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, contentDescription = "导入文件") },
                text = { Text("导入文件") },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Spacer(Modifier.height(8.dp))
            FileSearchField(
                value = uiState.filter.search,
                onValueChange = viewModel::onSearchChange,
            )
            Spacer(Modifier.height(4.dp))
            CategoryFilterRow(
                selected = uiState.filter.category,
                onSelect = viewModel::onCategoryChange,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!uiState.isProjectScope) {
                    val selectedProjectText = uiState.projects
                        .firstOrNull { it.projectId == uiState.filter.projectFilter }
                        ?.name
                        ?: "全部项目"
                    val projectOptions = listOf(0L to "全部项目") +
                        uiState.projects.map { it.projectId to it.name }
                    DropdownFilter(
                        label = "项目",
                        selectedText = selectedProjectText,
                        options = projectOptions,
                        onSelect = viewModel::onProjectFilterChange,
                    )
                }

                val nodeSelectedText = when (val nodeFilter = uiState.filter.nodeFilter) {
                    NodeFilter.All -> "全部节点"
                    NodeFilter.Unlinked -> "未关联节点"
                    is NodeFilter.ById -> uiState.nodeLabels
                        .firstOrNull { it.nodeId == nodeFilter.nodeId }
                        ?.let { label ->
                            if (uiState.isProjectScope) {
                                label.nodeTitle
                            } else {
                                "${label.projectName} · ${label.nodeTitle}"
                            }
                        }
                        ?: "全部节点"
                }
                val nodeOptions = buildList {
                    add(0L to "全部节点")
                    add(-1L to "未关联节点")
                    uiState.nodeLabels.forEach { label ->
                        val text = if (uiState.isProjectScope) {
                            label.nodeTitle
                        } else {
                            "${label.projectName} · ${label.nodeTitle}"
                        }
                        add(label.nodeId to text)
                    }
                }
                DropdownFilter(
                    label = "节点",
                    selectedText = nodeSelectedText,
                    options = nodeOptions,
                    onSelect = { value ->
                        viewModel.onNodeFilterChange(
                            when (value) {
                                0L -> NodeFilter.All
                                -1L -> NodeFilter.Unlinked
                                else -> NodeFilter.ById(value)
                            },
                        )
                    },
                )

                val sortText = if (uiState.filter.sort == FileSort.IMPORTED_DESC) {
                    "按导入时间"
                } else {
                    "按名称"
                }
                DropdownFilter(
                    label = "排序",
                    selectedText = sortText,
                    options = listOf(0L to "按导入时间", 1L to "按名称"),
                    onSelect = { value ->
                        viewModel.onSortChange(
                            if (value == 0L) FileSort.IMPORTED_DESC else FileSort.NAME_ASC,
                        )
                    },
                )

                if (uiState.hasActiveFilters) {
                    TextButton(onClick = viewModel::clearFilters) {
                        Text("清除筛选")
                    }
                }
            }
            Text(
                text = "显示 ${uiState.files.size} / 共 ${uiState.totalFileCount} 个文件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> ErrorState(message = uiState.error.orEmpty())
                    uiState.totalFileCount == 0 -> EmptyState(
                        title = "还没有文件",
                        description = "导入任意类型的文件。文件副本会保存在应用私有目录中，不会上传到任何服务器。",
                        actionLabel = "导入文件",
                        onAction = { importLauncher.launch(arrayOf("*/*")) },
                    )
                    uiState.files.isEmpty() -> EmptyState(
                        title = "没有符合筛选条件的文件",
                        description = "可以调整关键词、分类或节点筛选后再试。",
                        actionLabel = "清除筛选",
                        onAction = viewModel::clearFilters,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items = uiState.files, key = { it.file.id }) { row ->
                            FileRowCard(
                                row = row,
                                showProject = true,
                                onOpen = {
                                    if (FileTypeClassifier.supportsInAppPreview(row.file.category)) {
                                        onOpenPreview(row.file.id)
                                    } else {
                                        externalOpener(row)
                                    }
                                },
                                onExternalOpen = { externalOpener(row) },
                                onDelete = { fileToDelete = row },
                            )
                        }
                    }
                }
            }
        }
    }

    fileToDelete?.let { row ->
        ConfirmDialog(
            title = "删除文件",
            message = "删除「${row.file.displayName}」后，应用内保存的文件副本和记录都会被删除，无法恢复。",
            onConfirm = {
                fileToDelete = null
                viewModel.deleteFile(row.file.id)
            },
            onDismiss = { fileToDelete = null },
        )
    }

    if (showImportDialog) {
        ImportTargetDialog(
            projects = uiState.projects,
            nodeLabels = uiState.nodeLabels,
            onDismiss = {
                showImportDialog = false
                pendingUris = emptyList()
            },
            onConfirm = { projectId, nodeId ->
                showImportDialog = false
                importManager.importFiles(projectId, nodeId, pendingUris)
                pendingUris = emptyList()
            },
        )
    }
}

@Composable
private fun ImportTargetDialog(
    projects: List<com.projecttrack.app.data.model.ProjectOption>,
    nodeLabels: List<com.projecttrack.app.data.model.NodeLabel>,
    onDismiss: () -> Unit,
    onConfirm: (projectId: Long, nodeId: Long?) -> Unit,
) {
    var selectedProjectId by rememberSaveable {
        mutableLongStateOf(projects.firstOrNull()?.projectId ?: 0L)
    }
    var selectedNodeId by rememberSaveable { mutableLongStateOf(0L) }

    val projectNodes = remember(nodeLabels, selectedProjectId) {
        nodeLabels.filter { it.projectId == selectedProjectId }
    }
    val projectText = projects.firstOrNull { it.projectId == selectedProjectId }?.name
        ?: "请选择项目"
    val nodeText = projectNodes.firstOrNull { it.nodeId == selectedNodeId }?.nodeTitle
        ?: "不关联节点"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入位置") },
        text = {
            Column {
                Text(
                    text = "文件会复制到所选项目的应用私有目录中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (projects.isEmpty()) {
                    Text(
                        text = "还没有项目，请先在项目列表中创建项目后再导入文件。",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    DropdownFilter(
                        label = "项目",
                        selectedText = projectText,
                        options = projects.map { it.projectId to it.name },
                        onSelect = { value ->
                            selectedProjectId = value
                            selectedNodeId = 0L
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    DropdownFilter(
                        label = "节点",
                        selectedText = nodeText,
                        options = listOf(0L to "不关联节点") +
                            projectNodes.map { it.nodeId to it.nodeTitle },
                        onSelect = { selectedNodeId = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedProjectId > 0,
                onClick = {
                    onConfirm(
                        selectedProjectId,
                        selectedNodeId.takeIf { it > 0 },
                    )
                },
            ) {
                Text("开始导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
