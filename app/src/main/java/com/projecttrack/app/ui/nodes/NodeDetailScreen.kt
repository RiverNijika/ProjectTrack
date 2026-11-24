package com.projecttrack.app.ui.nodes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projecttrack.app.data.file.FileTypeClassifier
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.Note
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.ConfirmDialog
import com.projecttrack.app.ui.components.EmptyState
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.components.FileRowCard
import com.projecttrack.app.ui.components.LoadingState
import com.projecttrack.app.ui.components.SectionHeader
import com.projecttrack.app.ui.components.rememberFileExternalOpener
import com.projecttrack.app.ui.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailScreen(
    onBack: () -> Unit,
    onEditNode: (projectId: Long) -> Unit,
    onAddNote: (projectId: Long, nodeId: Long?) -> Unit,
    onEditNote: (projectId: Long, nodeId: Long?, noteId: Long) -> Unit,
    onOpenPreview: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: NodeDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val externalOpener = rememberFileExternalOpener(snackbarHostState)
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
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
        if (uiState.deleted) onDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.node?.title ?: "节点详情",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "进度节点",
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
                actions = {
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
                                    uiState.node?.let { node -> onEditNode(node.projectId) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除节点") },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.node != null) {
                ExtendedFloatingActionButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "导入文件") },
                    text = { Text("导入文件") },
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
            uiState.node == null -> EmptyState(
                title = "节点不存在",
                description = "该节点可能已经被删除。",
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val node = uiState.node ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 96.dp),
                ) {
                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "发生时间：${Formatters.dateTime(node.occurredAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "所属项目：${uiState.projectName ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "创建时间：${Formatters.dateTime(node.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (node.description.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = node.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    SectionHeader(
                        title = "文字记录（${uiState.notes.size}）",
                        trailing = {
                            TextButton(onClick = { onAddNote(node.projectId, node.id) }) {
                                Text("添加记录")
                            }
                        },
                    )
                    if (uiState.notes.isEmpty()) {
                        EmptyState(
                            title = "该节点还没有文字记录",
                            description = "节点的情况说明写在节点里；更长的过程记录可以添加在这里。",
                            actionLabel = "添加记录",
                            onAction = { onAddNote(node.projectId, node.id) },
                        )
                    } else {
                        uiState.notes.forEach { note ->
                            SimpleNoteCard(
                                note = note,
                                onEdit = { onEditNote(node.projectId, node.id, note.id) },
                                onDelete = { noteToDelete = note },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    SectionHeader(
                        title = "文件（${uiState.files.size}）",
                        trailing = {
                            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                                Text("导入文件")
                            }
                        },
                    )
                    if (uiState.files.isEmpty()) {
                        EmptyState(
                            title = "该节点还没有文件",
                            description = "导入的文件会自动关联到当前节点。",
                            actionLabel = "导入文件",
                            onAction = { importLauncher.launch(arrayOf("*/*")) },
                        )
                    } else {
                        uiState.files.forEach { row ->
                            FileRowCard(
                                row = row,
                                showProject = false,
                                onOpen = {
                                    if (FileTypeClassifier.supportsInAppPreview(row.file.category)) {
                                        onOpenPreview(row.file.id)
                                    } else {
                                        externalOpener(row)
                                    }
                                },
                                onExternalOpen = { externalOpener(row) },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除节点",
            message = "节点删除后，其文字记录和文件不会被删除，而是保留在项目中，成为“未关联节点”的内容。",
            confirmLabel = "删除节点",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteNode()
            },
            onDismiss = { showDeleteDialog = false },
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
private fun SimpleNoteCard(
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
