package com.projecttrack.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.ui.util.Formatters

/** 文件搜索框。 */
@Composable
fun FileSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        singleLine = true,
        label = { Text("搜索文件名") },
        placeholder = { Text("输入文件名关键字") },
    )
}

/** 分类筛选：全部、音频、视频、文本、其他。 */
@Composable
fun CategoryFilterRow(
    selected: FileCategory?,
    onSelect: (FileCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        val options = listOf<Pair<String, FileCategory?>>(
            "全部" to null,
            "音频" to FileCategory.AUDIO,
            "视频" to FileCategory.VIDEO,
            "文本" to FileCategory.TEXT,
            "其他" to FileCategory.OTHER,
        )
        options.forEachIndexed { index, (label, category) ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(label) },
            )
        }
    }
}

/** 下拉筛选按钮。 */
@Composable
fun DropdownFilter(
    label: String,
    selectedText: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label：$selectedText")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

@Composable
fun CategoryTag(category: FileCategory) {
    Text(
        text = Formatters.categoryLabel(category),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    )
}
