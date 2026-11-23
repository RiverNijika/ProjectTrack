package com.projecttrack.app.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.projecttrack.app.data.backup.BackupExporter
import com.projecttrack.app.data.backup.BackupImporter
import com.projecttrack.app.data.backup.BackupManager
import com.projecttrack.app.data.file.FileImportManager
import com.projecttrack.app.data.file.FileMetadataResolver
import com.projecttrack.app.data.file.FileOpener
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.PROJECT_TRACK_DATABASE_VERSION
import com.projecttrack.app.data.local.ProjectTrackDatabase
import com.projecttrack.app.data.repository.FileRepository
import com.projecttrack.app.data.repository.ProjectRepository
import com.projecttrack.app.data.repository.RoomTransactionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手工依赖注入容器。首阶段与当前阶段均不引入 Hilt，所有单例都在应用级容器中创建。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: ProjectTrackDatabase = ProjectTrackDatabase.build(appContext)
    private val transactions = RoomTransactionRunner(database)

    /** 所有会修改项目数据的操作共享同一把锁，备份/恢复期间阻止并发修改。 */
    val mutationGuard = DataMutationGuard()

    val storage: FileStorage = FileStorage(appContext.filesDir).also { it.ensureDirectories() }

    val projectRepository = ProjectRepository(
        projectDao = database.projectDao(),
        nodeDao = database.nodeDao(),
        noteDao = database.noteDao(),
        fileDao = database.fileDao(),
        storage = storage,
        transactions = transactions,
        mutationGuard = mutationGuard,
    )

    val fileRepository = FileRepository(
        fileDao = database.fileDao(),
        projectDao = database.projectDao(),
        nodeDao = database.nodeDao(),
        storage = storage,
        transactions = transactions,
        mutationGuard = mutationGuard,
    )

    private val metadataResolver = FileMetadataResolver(appContext)

    val fileOpener = FileOpener(appContext, storage)

    val importManager = FileImportManager(
        metadataResolver = metadataResolver,
        fileRepository = fileRepository,
        scope = applicationScope,
    )

    private val packageInfo = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    }.getOrNull()

    private val appVersionName: String = packageInfo?.versionName ?: "unknown"
    private val appVersionCode: Long = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

    private val backupExporter = BackupExporter(
        projectDao = database.projectDao(),
        nodeDao = database.nodeDao(),
        noteDao = database.noteDao(),
        fileDao = database.fileDao(),
        storage = storage,
        mutationGuard = mutationGuard,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        databaseVersion = PROJECT_TRACK_DATABASE_VERSION,
    )

    private val backupImporter = BackupImporter(
        projectDao = database.projectDao(),
        nodeDao = database.nodeDao(),
        noteDao = database.noteDao(),
        fileDao = database.fileDao(),
        storage = storage,
        transactions = transactions,
        mutationGuard = mutationGuard,
        cacheRoot = appContext.cacheDir,
        databaseVersion = PROJECT_TRACK_DATABASE_VERSION,
    )

    val backupManager = BackupManager(
        context = appContext,
        exporter = backupExporter,
        importer = backupImporter,
        scope = applicationScope,
    )

    /** 启动时做一次轻量维护：清理过期且未入库的孤立文件。 */
    fun startMaintenance() {
        applicationScope.launch {
            runCatching { fileRepository.cleanupOrphanFiles() }
        }
    }
}
