package com.projecttrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projecttrack.app.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

data class FileWithNameRow(
    @Embedded val file: FileEntity,
    val projectName: String,
    val nodeTitle: String?,
)

@Dao
interface FileDao {
    @Query(
        "SELECT f.*, p.name AS projectName, n.title AS nodeTitle FROM files f " +
            "INNER JOIN projects p ON p.id = f.projectId " +
            "LEFT JOIN nodes n ON n.id = f.nodeId " +
            "ORDER BY f.importedAt DESC, f.id DESC",
    )
    fun observeAll(): Flow<List<FileWithNameRow>>

    @Query(
        "SELECT f.*, p.name AS projectName, n.title AS nodeTitle FROM files f " +
            "INNER JOIN projects p ON p.id = f.projectId " +
            "LEFT JOIN nodes n ON n.id = f.nodeId " +
            "WHERE f.projectId = :projectId " +
            "ORDER BY f.importedAt DESC, f.id DESC",
    )
    fun observeByProject(projectId: Long): Flow<List<FileWithNameRow>>

    @Query(
        "SELECT f.*, p.name AS projectName, n.title AS nodeTitle FROM files f " +
            "INNER JOIN projects p ON p.id = f.projectId " +
            "LEFT JOIN nodes n ON n.id = f.nodeId " +
            "WHERE f.nodeId = :nodeId " +
            "ORDER BY f.importedAt DESC, f.id DESC",
    )
    fun observeByNode(nodeId: Long): Flow<List<FileWithNameRow>>

    @Query(
        "SELECT f.*, p.name AS projectName, n.title AS nodeTitle FROM files f " +
            "INNER JOIN projects p ON p.id = f.projectId " +
            "LEFT JOIN nodes n ON n.id = f.nodeId " +
            "WHERE f.id = :fileId",
    )
    fun observeById(fileId: Long): Flow<FileWithNameRow?>

    @Query("SELECT * FROM files WHERE id = :fileId")
    suspend fun getById(fileId: Long): FileEntity?

    @Insert
    suspend fun insert(file: FileEntity): Long

    @Update
    suspend fun update(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :fileId")
    suspend fun deleteById(fileId: Long)

    @Query("UPDATE files SET nodeId = NULL WHERE nodeId = :nodeId")
    suspend fun detachFromNode(nodeId: Long)

    @Query("SELECT relativePath FROM files WHERE projectId = :projectId")
    suspend fun pathsForProject(projectId: Long): List<String>

    @Query("SELECT relativePath FROM files")
    suspend fun allPaths(): List<String>

    @Query("SELECT * FROM files")
    suspend fun getAll(): List<FileEntity>
}
