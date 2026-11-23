package com.projecttrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projecttrack.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    /** 项目下未关联节点的记录。节点删除后，其记录会归入这里。 */
    @Query("SELECT * FROM notes WHERE projectId = :projectId AND nodeId IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeProjectNotes(projectId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE nodeId = :nodeId ORDER BY createdAt DESC, id DESC")
    fun observeNodeNotes(nodeId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun observeById(noteId: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun getAll(): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Query("UPDATE notes SET nodeId = NULL, updatedAt = :updatedAt WHERE nodeId = :nodeId")
    suspend fun detachFromNode(nodeId: Long, updatedAt: Long)
}
