package com.projecttrack.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.NoteDao
import com.projecttrack.app.data.local.dao.NodeDao
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        NodeEntity::class,
        NoteEntity::class,
        FileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(FileCategoryConverter::class)
abstract class ProjectTrackDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeDao(): NodeDao
    abstract fun noteDao(): NoteDao
    abstract fun fileDao(): FileDao

    companion object {
        private const val DATABASE_NAME = "projecttrack.db"

        fun build(context: Context): ProjectTrackDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ProjectTrackDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
