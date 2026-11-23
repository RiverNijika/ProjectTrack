package com.projecttrack.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.projecttrack.app.ProjectTrackApplication
import com.projecttrack.app.data.file.TextPreviewResult
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.EmptyState
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.components.LoadingState
import com.projecttrack.app.ui.components.rememberFileExternalOpener
import com.projecttrack.app.ui.util.Formatters
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    onBack: () -> Unit,
    viewModel: FilePreviewViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val externalOpener = rememberFileExternalOpener(snackbarHostState)
    val fileRow = uiState.file

    LaunchedEffect(fileRow?.file?.id) {
        if (fileRow?.file?.category == FileCategory.TEXT) {
            viewModel.loadTextPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileRow?.file?.displayName ?: "文件预览",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        fileRow?.let { row ->
                            Text(
                                text = "${Formatters.categoryLabel(row.file.category)} · ${Formatters.fileSize(row.file.sizeBytes)}",
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
                    if (fileRow != null) {
                        TextButton(onClick = { externalOpener(fileRow) }) {
                            Text("外部打开")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            fileRow == null -> EmptyState(
                title = "文件不存在",
                description = uiState.error ?: "该文件记录可能已被删除。",
                modifier = Modifier.padding(innerPadding),
            )
            uiState.missing -> EmptyState(
                title = "文件副本不存在",
                description = "数据库仍有记录，但应用私有目录中的文件已丢失。可以回到文件列表删除该记录，或重新导入文件。",
                modifier = Modifier.padding(innerPadding),
            )
            uiState.error != null -> ErrorState(
                message = uiState.error.orEmpty(),
                modifier = Modifier.padding(innerPadding),
            )
            else -> when (fileRow.file.category) {
                FileCategory.AUDIO -> AudioPreview(
                    fileRow = fileRow,
                    modifier = Modifier.padding(innerPadding),
                    onExternalOpen = { externalOpener(fileRow) },
                )
                FileCategory.VIDEO -> VideoPreview(
                    fileRow = fileRow,
                    modifier = Modifier.padding(innerPadding),
                    onExternalOpen = { externalOpener(fileRow) },
                )
                FileCategory.TEXT -> TextPreview(
                    uiState = uiState,
                    modifier = Modifier.padding(innerPadding),
                    onExternalOpen = { externalOpener(fileRow) },
                    onRetry = viewModel::retryTextPreview,
                )
                FileCategory.OTHER -> EmptyState(
                    title = "此格式暂不支持应用内预览",
                    description = "图片、PDF、Office 文档、压缩包等文件请使用系统中已安装的应用打开。",
                    modifier = Modifier.padding(innerPadding),
                    actionLabel = "用其他应用打开",
                    onAction = { externalOpener(fileRow) },
                )
            }
        }
    }
}

@Composable
private fun AudioPreview(
    fileRow: FileRow,
    modifier: Modifier = Modifier,
    onExternalOpen: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as ProjectTrackApplication
    val uri = remember(fileRow.file.id) {
        application.container.fileOpener.contentUri(fileRow.file)
    }
    if (uri == null) {
        EmptyState(
            title = "音频文件不存在",
            description = "文件副本可能已被删除，请重新导入。",
            modifier = modifier,
        )
        return
    }

    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val player = remember(fileRow.file.id) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player, uri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "无法预览该音频，可能是设备不支持此编码或文件已损坏。"
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (player.playbackState == Player.STATE_READY) {
                duration = player.duration.coerceAtLeast(0L)
            }
            delay(300L)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "本地音频预览",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (player.isPlaying) player.pause() else player.play()
            },
            enabled = playerError == null,
        ) {
            Text(if (isPlaying) "暂停" else "播放")
        }
        Spacer(Modifier.height(16.dp))
        Slider(
            value = position.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
            onValueChange = { value -> player.seekTo(value.toLong()) },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = playerError == null && duration > 0L,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = Formatters.duration(position),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Formatters.duration(duration),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (playerError != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = playerError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onExternalOpen) {
                Text("用其他应用打开")
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "音频只在预览页面播放；离开页面或屏幕关闭时会停止并释放播放器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoPreview(
    fileRow: FileRow,
    modifier: Modifier = Modifier,
    onExternalOpen: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as ProjectTrackApplication
    val uri = remember(fileRow.file.id) {
        application.container.fileOpener.contentUri(fileRow.file)
    }
    if (uri == null) {
        EmptyState(
            title = "视频文件不存在",
            description = "文件副本可能已被删除，请重新导入。",
            modifier = modifier,
        )
        return
    }

    var playerError by remember { mutableStateOf<String?>(null) }
    val exoPlayer = remember(fileRow.file.id) { ExoPlayer.Builder(context).build() }

    DisposableEffect(exoPlayer, uri) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = "无法预览该视频，可能是设备不支持此编码或文件已损坏。"
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    player = exoPlayer
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        )
        if (playerError != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = playerError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onExternalOpen) {
                    Text("用其他应用打开")
                }
            }
        } else {
            Text(
                text = "视频在预览页面内播放，离开页面后停止并释放播放器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TextPreview(
    uiState: FilePreviewUiState,
    modifier: Modifier = Modifier,
    onExternalOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val result = uiState.textPreview) {
            null -> LoadingState(
                message = if (uiState.isTextLoading) "正在读取文本…" else "准备读取文本…",
            )
            is TextPreviewResult.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (result.truncated) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "文件较大，仅显示前 256 KB。完整内容请使用其他应用打开。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                SelectionContainer {
                    Text(
                        text = result.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
            TextPreviewResult.Missing -> EmptyState(
                title = "文本文件不存在",
                description = "文件副本可能已被删除，请重新导入。",
            )
            TextPreviewResult.InvalidUtf8 -> Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                EmptyState(
                    title = "无法按 UTF-8 解码此文件",
                    description = "文件可能使用了其他编码（例如 GBK、UTF-16），或者本身不是纯文本。为避免显示乱码，这里不做预览。",
                    actionLabel = "用其他应用打开",
                    onAction = onExternalOpen,
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                ) {
                    Text("重试读取")
                }
            }
            is TextPreviewResult.Failure -> Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                EmptyState(
                    title = "读取文本失败",
                    description = result.message,
                    actionLabel = "用其他应用打开",
                    onAction = onExternalOpen,
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                ) {
                    Text("重试读取")
                }
            }
        }
    }
}
