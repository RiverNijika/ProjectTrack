package com.projecttrack.app.ui.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.components.LoadingState
import com.projecttrack.app.ui.components.OccurredAtField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: NodeEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "添加进度节点" else "编辑进度节点") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.loadError != null -> ErrorState(
                message = uiState.loadError.orEmpty(),
                modifier = Modifier.padding(innerPadding),
                onRetry = onBack,
            )
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("节点标题（必填）") },
                    placeholder = { Text("例如：等待设计确认、接口联调完成、暂时搁置……") },
                    singleLine = false,
                    isError = uiState.titleError != null,
                    supportingText = uiState.titleError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    label = { Text("情况说明") },
                    placeholder = { Text("这个节点发生了什么？可以记录进展、暂停、阻塞或异常情况。") },
                )
                OccurredAtField(
                    epochMillis = uiState.occurredAt,
                    onTimeChange = viewModel::onOccurredAtChange,
                )
                Text(
                    text = "发生时间默认当前时间，可以修改为过去的日期来补录历史进展。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.saveError != null) {
                    Text(
                        text = uiState.saveError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving,
                ) {
                    Text(if (uiState.isSaving) "保存中…" else "保存节点")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
