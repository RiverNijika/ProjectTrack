package com.projecttrack.app.data.repository

import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.NodeDao
import com.projecttrack.app.data.local.dao.NoteDao
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.local.toModel
import com.projecttrack.app.data.model.Note
import com.projecttrack.app.data.model.NodeLabel
import com.projecttrack.app.data.model.ProgressNode
import com.projecttrack.app.data.model.Project
import com.projecttrack.app.data.model.ProjectOption
import com.projecttrack.app.data.model.ProjectSummary
import com.projecttrack.app.domain.LatestNodeSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

/** 项目、节点、文字记录的业务仓库。 */
class ProjectRepository(
    private val projectDao: ProjectDao,
    private val nodeDao: NodeDao,
    private val noteDao: NoteDao,
    private val fileDao: FileDao,
    private val storage: FileStorage,
    private val transactions: TransactionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeProjectSummaries(): Flow<List<ProjectSummary>> =
        combine(projectDao.observeAll(), nodeDao.observeAll()) { projects, nodes ->
            val nodesByProject = nodes.map { it.toModel() }.groupBy { it.projectId }
            projects.map { entity ->
                val project = entity.toModel()
                val latest = LatestNodeSelector.select(nodesByProject[project.id].orEmpty())
                ProjectSummary(
                    project = project,
                    latestNodeTitle = latest?.title,
                    latestNodeOccurredAt = latest?.occurredAt,
                )
            }
        }

    fun observeProject(projectId: Long): Flow<Project?> =
        projectDao.observeById(projectId).map { it?.toModel() }

    fun observeNodes(projectId: Long): Flow<List<ProgressNode>> =
        nodeDao.observeByProject(projectId).map { nodes -> nodes.map { it.toModel() } }

    fun observeNode(nodeId: Long): Flow<ProgressNode?> =
        nodeDao.observeById(nodeId).map { it?.toModel() }

    fun observeProjectNotes(projectId: Long): Flow<List<Note>> =
        noteDao.observeProjectNotes(projectId).map { notes -> notes.map { it.toModel() } }

    fun observeNodeNotes(nodeId: Long): Flow<List<Note>> =
        noteDao.observeNodeNotes(nodeId).map { notes -> notes.map { it.toModel() } }

    fun observeNodeLabels(projectId: Long? = null): Flow<List<NodeLabel>> =
        nodeDao.observeAllWithProjectName().map { rows ->
            rows.map { row ->
                NodeLabel(
                    nodeId = row.node.id,
                    projectId = row.node.projectId,
                    projectName = row.projectName,
                    nodeTitle = row.node.title,
                )
            }.filter { projectId == null || it.projectId == projectId }
        }

    fun observeProjectOptions(): Flow<List<ProjectOption>> =
        projectDao.observeAll().map { projects ->
            projects.map { ProjectOption(projectId = it.id, name = it.name) }
        }

    suspend fun getProject(projectId: Long): Project? = projectDao.getById(projectId)?.toModel()

    suspend fun getNode(nodeId: Long): ProgressNode? = nodeDao.getById(nodeId)?.toModel()

    suspend fun getNote(noteId: Long): Note? = noteDao.getById(noteId)?.toModel()

    suspend fun createProject(name: String, description: String): Long = transactions.run {
        val now = clock()
        projectDao.insert(
            ProjectEntity(
                name = name.trim(),
                description = description.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateProject(projectId: Long, name: String, description: String) {
        transactions.run {
            val existing = projectDao.getById(projectId) ?: return@run
            projectDao.update(
                existing.copy(
                    name = name.trim(),
                    description = description.trim(),
                    updatedAt = clock(),
                ),
            )
        }
    }

    /**
     * 删除项目。数据库中级联删除节点、记录和文件元数据；随后删除应用内的文件副本。
     * 返回删除失败的文件数量（0 表示全部删除成功）。
     */
    suspend fun deleteProject(projectId: Long): Int {
        val filePaths = fileDao.pathsForProject(projectId)
        transactions.run { projectDao.deleteById(projectId) }
        return filePaths.count { relativePath -> !storage.delete(relativePath) }
    }

    suspend fun createNode(
        projectId: Long,
        title: String,
        description: String,
        occurredAt: Long,
    ): Long = transactions.run {
        val now = clock()
        val nodeId = nodeDao.insert(
            NodeEntity(
                projectId = projectId,
                title = title.trim(),
                description = description.trim(),
                occurredAt = occurredAt,
                createdAt = now,
            ),
        )
        projectDao.touch(projectId, now)
        nodeId
    }

    suspend fun updateNode(
        nodeId: Long,
        title: String,
        description: String,
        occurredAt: Long,
    ) {
        transactions.run {
            val existing = nodeDao.getById(nodeId) ?: return@run
            nodeDao.update(
                existing.copy(
                    title = title.trim(),
                    description = description.trim(),
                    occurredAt = occurredAt,
                ),
            )
            projectDao.touch(existing.projectId, clock())
        }
    }

    /**
     * 删除节点，但保留其文字记录与文件：先把两者的 nodeId 置空，再删除节点。
     * 这保证节点删除后资料仍属于项目，出现在“未关联节点”内容中。
     */
    suspend fun deleteNode(nodeId: Long): Boolean = transactions.run {
        val node = nodeDao.getById(nodeId) ?: return@run false
        val now = clock()
        noteDao.detachFromNode(nodeId, now)
        fileDao.detachFromNode(nodeId)
        nodeDao.deleteById(nodeId)
        projectDao.touch(node.projectId, now)
        true
    }

    suspend fun createNote(projectId: Long, nodeId: Long?, content: String): Long = transactions.run {
        val now = clock()
        val noteId = noteDao.insert(
            NoteEntity(
                projectId = projectId,
                nodeId = nodeId,
                content = content.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        projectDao.touch(projectId, now)
        noteId
    }

    suspend fun updateNote(noteId: Long, content: String) {
        transactions.run {
            val existing = noteDao.getById(noteId) ?: return@run
            val now = clock()
            noteDao.update(existing.copy(content = content.trim(), updatedAt = now))
            projectDao.touch(existing.projectId, now)
        }
    }

    suspend fun deleteNote(noteId: Long) {
        transactions.run {
            val existing = noteDao.getById(noteId) ?: return@run
            noteDao.deleteById(noteId)
            projectDao.touch(existing.projectId, clock())
        }
    }

    suspend fun projectFileCount(projectId: Long): Int = fileDao.pathsForProject(projectId).size

    @Suppress("unused")
    private fun securityMessage(error: Throwable): String = when (error) {
        is SecurityException -> "没有访问该文件的权限"
        is IOException -> "读取或写入文件失败"
        else -> error.message ?: "操作失败"
    }
}
