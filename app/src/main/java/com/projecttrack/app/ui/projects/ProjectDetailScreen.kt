package com.projecttrack.app.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.Note
import com.projecttrack.app.data.model.ProgressNode
import com.projecttrack.app.data.model.Project
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.ConfirmDialog
import com.projecttrack.app.ui.components.EmptyState
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.components.FileRowCard
import com.projecttrack.app.ui.components.LoadingState
import com.projecttrack.app.ui.components.rememberFileExternalOpener
import com.projecttrack.app.ui.util.Formatters

private val TAB_TITLES = listOf("时间线", "记录", "文件")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onAddNode: () -> Unit,
    onOpenNode: (Long) -> Unit,
    onEditNode: (Long) -> Unit,
    onAddNote: (projectId: Long, nodeId: Long?) -> Unit,
    onEditNote: (projectId: Long, nodeId: Long?, noteId: Long) -> Unit,
    onOpenProjectFiles: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onProjectDeleted: () -> Unit,
    viewModel: ProjectDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val externalOpener = rememberFileExternalOpener(snackbarHostState)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteProjectDialog by remember { mutableStateOf(false) }
    var nodeToDelete by remember { mutableStateOf<ProgressNode?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onProjectDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.project?.name ?: "项目详情",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (uiState.project != null) {
                            Text(
                                text = "本地项目",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "项目操作")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑项目") },
                                onClick = {
                                    menuExpanded = false
                                    onEditProject()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("项目文件") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenProjectFiles()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除项目") },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteProjectDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.project != null) {
                val (label, icon) = when (selectedTab) {
                    0 -> "添加节点" to Icons.Default.Add
                    1 -> "添加记录" to Icons.Default.Add
                    else -> "导入文件" to Icons.Default.Add
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> onAddNode()
                            1 -> uiState.project?.let { project -> onAddNote(project.id, null) }
                            else -> importLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    icon = { Icon(icon, contentDescription = null) },
                    text = { Text(label) },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.error != null -> ErrorState(
                message = uiState.error.orEmpty(),
                modifier = Modifier.padding(innerPadding),
            )
            uiState.project == null -> EmptyState(
                title = "项目不存在",
                description = "该项目可能已经被删除。",
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val project = uiState.project ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    ProjectHeader(
                        project = project,
                        latestNode = uiState.latestNode,
                    )
                    TabRow(selectedTabIndex = selectedTab) {
                        TAB_TITLES.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> TimelineTab(
                                nodes = uiState.nodes,
                                onOpenNode = onOpenNode,
                                onEditNode = onEditNode,
                                onDeleteNode = { nodeToDelete = it },
                                onAddNode = onAddNode,
                            )
                            1 -> NotesTab(
                                notes = uiState.notes,
                                onEditNote = { note ->
                                    onEditNote(project.id, note.nodeId, note.id)
                                },
                                onDeleteNote = { noteToDelete = it },
                                onAddNote = { onAddNote(project.id, null) },
                            )
                            else -> FilesTab(
                                files = uiState.files,
                                onOpenFiles = onOpenProjectFiles,
                                onImport = { importLauncher.launch(arrayOf("*/*")) },
                                onOpenPreview = onOpenPreview,
                                onExternalOpen = externalOpener,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteProjectDialog) {
        ConfirmDialog(
            title = "删除项目",
            message = "删除项目后，该项目下的所有进度节点、文字记录和已导入的文件都会被永久删除，无法恢复。确定删除吗？",
            confirmLabel = "删除项目",
            onConfirm = {
                showDeleteProjectDialog = false
                viewModel.deleteProject()
            },
            onDismiss = { showDeleteProjectDialog = false },
        )
    }

    nodeToDelete?.let { node ->
        ConfirmDialog(
            title = "删除节点",
            message = "节点“${node.title}”删除后，其文字记录和文件不会被删除，而是保留在项目中，成为“未关联节点”的内容。",
            confirmLabel = "删除节点",
            onConfirm = {
                val id = node.id
                nodeToDelete = null
                viewModel.deleteNode(id)
            },
            onDismiss = { nodeToDelete = null },
        )
    }

    noteToDelete?.let { note ->
        ConfirmDialog(
            title = "删除记录",
            message = "删除后该文字记录无法恢复，确定删除吗？",
            onConfirm = {
                noteToDelete = null
                viewModel.deleteNote(note.id)
            },
            onDismiss = { noteToDelete = null },
        )
    }
}

@Composable
private fun ProjectHeader(
    project: Project,
    latestNode: ProgressNode?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (project.description.isNotBlank()) {
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "当前状态（最新节点）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                if (latestNode == null) {
                    Text(
                        text = "暂无进度记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        text = latestNode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = Formatters.dateTime(latestNode.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "创建于 ${Formatters.dateTime(project.createdAt)} · 更新于 ${Formatters.dateTime(project.updatedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineTab(
    nodes: List<ProgressNode>,
    onOpenNode: (Long) -> Unit,
    onEditNode: (Long) -> Unit,
    onDeleteNode: (ProgressNode) -> Unit,
    onAddNode: () -> Unit,
) {
    if (nodes.isEmpty()) {
        EmptyState(
            title = "还没有进度节点",
            description = "随时添加一个进度节点，写下当前发生了什么。节点标题由你决定，可以是进展、暂停、阻塞或任何情况。",
            actionLabel = "添加节点",
            onAction = onAddNode,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = nodes, key = { it.id }) { node ->
                NodeCard(
                    node = node,
                    onClick = { onOpenNode(node.id) },
                    onEdit = { onEditNode(node.id) },
                    onDelete = { onDeleteNode(node) },
                )
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: ProgressNode,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "发生时间：${Formatters.dateTime(node.occurredAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "节点操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑节点") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除节点") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (node.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = node.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: List<Note>,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onAddNote: () -> Unit,
) {
    if (notes.isEmpty()) {
        EmptyState(
            title = "还没有文字记录",
            description = "在项目下随手记录想法、过程说明或任何需要保存的文字。",
            actionLabel = "添加记录",
            onAction = onAddNote,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onEdit = { onEditNote(note) },
                    onDelete = { onDeleteNote(note) },
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "记录操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑记录") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除记录") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "更新于 ${Formatters.dateTime(note.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilesTab(
    files: List<FileRow>,
    onOpenFiles: () -> Unit,
    onImport: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onExternalOpen: (FileRow) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${files.size} 个文件",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenFiles) {
                Text("管理全部文件")
            }
        }
        if (files.isEmpty()) {
            EmptyState(
                title = "还没有文件",
                description = "导入任意类型的文件，副本会保存在应用私有目录中，不受原文件移动或删除影响。",
                actionLabel = "导入文件",
                onAction = onImport,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items = files.take(20), key = { it.file.id }) { row ->
                    FileRowCard(
                        row = row,
                        showProject = false,
                        onOpen = {
                            if (com.projecttrack.app.data.file.FileTypeClassifier.supportsInAppPreview(row.file.category)) {
                                onOpenPreview(row.file.id)
                            } else {
                                onExternalOpen(row)
                            }
                        },
                        onExternalOpen = { onExternalOpen(row) },
                    )
                }
            }
        }
        if (files.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text("导入文件到项目")
            }
        }
    }
}
