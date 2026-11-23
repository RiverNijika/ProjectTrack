package com.projecttrack.app.testutil

import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.FileWithNameRow
import com.projecttrack.app.data.local.dao.NodeDao
import com.projecttrack.app.data.local.dao.NodeWithProjectRow
import com.projecttrack.app.data.local.dao.NoteDao
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 内存版 DAO，供 JVM 单元测试验证仓库层的删除与查询行为。
 * 只实现测试用到的路径，其余方法保持简单实现。
 */
class FakeProjectDao : ProjectDao {
    val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    override fun observeAll(): Flow<List<ProjectEntity>> = projects

    override fun observeById(projectId: Long): Flow<ProjectEntity?> =
        projects.map { list -> list.firstOrNull { it.id == projectId } }

    override suspend fun getById(projectId: Long): ProjectEntity? =
        projects.value.firstOrNull { it.id == projectId }

    override suspend fun insert(project: ProjectEntity): Long {
        val newId = if (project.id != 0L) project.id else nextId(projects.value.map { it.id })
        projects.value = projects.value + project.copy(id = newId)
        return newId
    }

    override suspend fun update(project: ProjectEntity) {
        projects.value = projects.value.map { if (it.id == project.id) project else it }
    }

    override suspend fun touch(projectId: Long, updatedAt: Long) {
        projects.value = projects.value.map {
            if (it.id == projectId) it.copy(updatedAt = updatedAt) else it
        }
    }

    override suspend fun deleteById(projectId: Long) {
        projects.value = projects.value.filterNot { it.id == projectId }
    }
}

class FakeNodeDao : NodeDao {
    val nodes = MutableStateFlow<List<NodeEntity>>(emptyList())
    var projectNames: Map<Long, String> = emptyMap()

    override fun observeByProject(projectId: Long): Flow<List<NodeEntity>> =
        nodes.map { list -> list.filter { it.projectId == projectId } }

    override fun observeAll(): Flow<List<NodeEntity>> = nodes

    override fun observeById(nodeId: Long): Flow<NodeEntity?> =
        nodes.map { list -> list.firstOrNull { it.id == nodeId } }

    override suspend fun getById(nodeId: Long): NodeEntity? =
        nodes.value.firstOrNull { it.id == nodeId }

    override suspend fun insert(node: NodeEntity): Long {
        val newId = if (node.id != 0L) node.id else nextId(nodes.value.map { it.id })
        nodes.value = nodes.value + node.copy(id = newId)
        return newId
    }

    override suspend fun update(node: NodeEntity) {
        nodes.value = nodes.value.map { if (it.id == node.id) node else it }
    }

    override suspend fun deleteById(nodeId: Long) {
        nodes.value = nodes.value.filterNot { it.id == nodeId }
    }

    override fun observeAllWithProjectName(): Flow<List<NodeWithProjectRow>> =
        nodes.map { list ->
            list.map { node ->
                NodeWithProjectRow(
                    node = node,
                    projectName = projectNames[node.projectId] ?: "",
                )
            }
        }
}

class FakeNoteDao : NoteDao {
    val notes = MutableStateFlow<List<NoteEntity>>(emptyList())

    override fun observeProjectNotes(projectId: Long): Flow<List<NoteEntity>> =
        notes.map { list -> list.filter { it.projectId == projectId && it.nodeId == null } }

    override fun observeNodeNotes(nodeId: Long): Flow<List<NoteEntity>> =
        notes.map { list -> list.filter { it.nodeId == nodeId } }

    override fun observeById(noteId: Long): Flow<NoteEntity?> =
        notes.map { list -> list.firstOrNull { it.id == noteId } }

    override suspend fun getById(noteId: Long): NoteEntity? =
        notes.value.firstOrNull { it.id == noteId }

    override suspend fun insert(note: NoteEntity): Long {
        val newId = if (note.id != 0L) note.id else nextId(notes.value.map { it.id })
        notes.value = notes.value + note.copy(id = newId)
        return newId
    }

    override suspend fun update(note: NoteEntity) {
        notes.value = notes.value.map { if (it.id == note.id) note else it }
    }

    override suspend fun deleteById(noteId: Long) {
        notes.value = notes.value.filterNot { it.id == noteId }
    }

    override suspend fun detachFromNode(nodeId: Long, updatedAt: Long) {
        notes.value = notes.value.map {
            if (it.nodeId == nodeId) it.copy(nodeId = null, updatedAt = updatedAt) else it
        }
    }
}

class FakeFileDao : FileDao {
    val files = MutableStateFlow<List<FileEntity>>(emptyList())
    var projectNames: Map<Long, String> = emptyMap()
    var nodeTitles: Map<Long, String> = emptyMap()
    var failOnInsert: Boolean = false

    override fun observeAll(): Flow<List<FileWithNameRow>> =
        files.map { list -> list.map { it.toRow() } }

    override fun observeByProject(projectId: Long): Flow<List<FileWithNameRow>> =
        files.map { list -> list.filter { it.projectId == projectId }.map { it.toRow() } }

    override fun observeByNode(nodeId: Long): Flow<List<FileWithNameRow>> =
        files.map { list -> list.filter { it.nodeId == nodeId }.map { it.toRow() } }

    override fun observeById(fileId: Long): Flow<FileWithNameRow?> =
        files.map { list -> list.firstOrNull { it.id == fileId }?.toRow() }

    override suspend fun getById(fileId: Long): FileEntity? =
        files.value.firstOrNull { it.id == fileId }

    override suspend fun insert(file: FileEntity): Long {
        if (failOnInsert) throw IOException("模拟数据库写入失败")
        val newId = if (file.id != 0L) file.id else nextId(files.value.map { it.id })
        files.value = files.value + file.copy(id = newId)
        return newId
    }

    override suspend fun update(file: FileEntity) {
        files.value = files.value.map { if (it.id == file.id) file else it }
    }

    override suspend fun deleteById(fileId: Long) {
        files.value = files.value.filterNot { it.id == fileId }
    }

    override suspend fun detachFromNode(nodeId: Long) {
        files.value = files.value.map { if (it.nodeId == nodeId) it.copy(nodeId = null) else it }
    }

    override suspend fun pathsForProject(projectId: Long): List<String> =
        files.value.filter { it.projectId == projectId }.map { it.relativePath }

    override suspend fun allPaths(): List<String> = files.value.map { it.relativePath }

    override suspend fun getAll(): List<FileEntity> = files.value

    private fun FileEntity.toRow(): FileWithNameRow = FileWithNameRow(
        file = this,
        projectName = projectNames[projectId] ?: "",
        nodeTitle = nodeId?.let { nodeTitles[it] },
    )
}

internal fun nextId(existingIds: List<Long>): Long = (existingIds.maxOrNull() ?: 0L) + 1L
