package com.projecttrack.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projecttrack.app.data.file.FileTypeClassifier
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.ui.util.Formatters

/**
 * 文件列表行：显示文件名、分类、大小、所属项目/节点，支持菜单中的预览、外部打开与删除。
 */
@Composable
fun FileRowCard(
    row: FileRow,
    showProject: Boolean,
    onOpen: () -> Unit,
    onExternalOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val supportsPreview = FileTypeClassifier.supportsInAppPreview(row.file.category)
    val details = buildList {
        add(Formatters.categoryLabel(row.file.category))
        add(Formatters.fileSize(row.file.sizeBytes))
        if (showProject) add(row.projectName)
        add(row.nodeTitle ?: "未关联节点")
    }.joinToString(" · ")

    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.file.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!row.exists) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "文件缺失，可能已被删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "文件操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (supportsPreview) {
                        DropdownMenuItem(
                            text = { Text("应用内预览") },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("用其他应用打开") },
                        onClick = {
                            menuExpanded = false
                            onExternalOpen()
                        },
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("删除文件") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}
