package com.projecttrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projecttrack.app.data.local.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

data class NodeWithProjectRow(
    @Embedded val node: NodeEntity,
    val projectName: String,
)

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE projectId = :projectId ORDER BY occurredAt DESC, createdAt DESC, id DESC")
    fun observeByProject(projectId: Long): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes ORDER BY occurredAt DESC, createdAt DESC, id DESC")
    fun observeAll(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE id = :nodeId")
    fun observeById(nodeId: Long): Flow<NodeEntity?>

    @Query("SELECT * FROM nodes WHERE id = :nodeId")
    suspend fun getById(nodeId: Long): NodeEntity?

    @Query("SELECT * FROM nodes ORDER BY id ASC")
    suspend fun getAll(): List<NodeEntity>

    @Insert
    suspend fun insert(node: NodeEntity): Long

    @Update
    suspend fun update(node: NodeEntity)

    @Query("DELETE FROM nodes WHERE id = :nodeId")
    suspend fun deleteById(nodeId: Long)

    @Query(
        "SELECT n.*, p.name AS projectName FROM nodes n " +
            "INNER JOIN projects p ON p.id = n.projectId " +
            "ORDER BY p.name COLLATE NOCASE ASC, n.occurredAt DESC, n.id DESC",
    )
    fun observeAllWithProjectName(): Flow<List<NodeWithProjectRow>>
}
