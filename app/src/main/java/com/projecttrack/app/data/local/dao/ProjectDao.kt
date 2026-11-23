package com.projecttrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projecttrack.app.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeById(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: Long): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun getAll(): List<ProjectEntity>

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun touch(projectId: Long, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)
}
