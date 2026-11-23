package com.projecttrack.app.data

import android.content.Context
import com.projecttrack.app.data.file.FileImportManager
import com.projecttrack.app.data.file.FileMetadataResolver
import com.projecttrack.app.data.file.FileOpener
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.ProjectTrackDatabase
import com.projecttrack.app.data.repository.FileRepository
import com.projecttrack.app.data.repository.ProjectRepository
import com.projecttrack.app.data.repository.RoomTransactionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手工依赖注入容器。首阶段不引入 Hilt，所有单例都在应用级容器中创建。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: ProjectTrackDatabase = ProjectTrackDatabase.build(appContext)
    private val transactions = RoomTransactionRunner(database)

    val storage: FileStorage = FileStorage(appContext.filesDir).also { it.ensureDirectories() }

    val projectRepository = ProjectRepository(
        projectDao = database.projectDao(),
        nodeDao = database.nodeDao(),
        noteDao = database.noteDao(),
        fileDao = database.fileDao(),
        storage = storage,
        transactions = transactions,
    )

    val fileRepository = FileRepository(
        fileDao = database.fileDao(),
        projectDao = database.projectDao(),
        storage = storage,
        transactions = transactions,
    )

    private val metadataResolver = FileMetadataResolver(appContext)

    val fileOpener = FileOpener(appContext, storage)

    val importManager = FileImportManager(
        metadataResolver = metadataResolver,
        fileRepository = fileRepository,
        scope = applicationScope,
    )

    /** 启动时做一次轻量维护：清理过期且未入库的孤立文件。 */
    fun startMaintenance() {
        applicationScope.launch {
            runCatching { fileRepository.cleanupOrphanFiles() }
        }
    }
}
