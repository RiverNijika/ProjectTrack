package com.projecttrack.app.data.local

import androidx.room.TypeConverter
import com.projecttrack.app.data.model.FileCategory

class FileCategoryConverter {
    @TypeConverter
    fun fromCategory(category: FileCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): FileCategory = FileCategory.fromName(value)
}
