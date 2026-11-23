package com.projecttrack.app.ui.util

import com.projecttrack.app.data.model.FileCategory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    fun dateTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormatter)

    fun date(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)

    fun fileSize(bytes: Long): String {
        if (bytes < 0) return "大小未知"
        if (bytes < 1024) return "$bytes B"
        val kilo = bytes / 1024.0
        if (kilo < 1024) return String.format(Locale.CHINA, "%.1f KB", kilo)
        val mega = kilo / 1024.0
        if (mega < 1024) return String.format(Locale.CHINA, "%.1f MB", mega)
        return String.format(Locale.CHINA, "%.2f GB", mega / 1024.0)
    }

    fun categoryLabel(category: FileCategory): String = when (category) {
        FileCategory.AUDIO -> "音频"
        FileCategory.VIDEO -> "视频"
        FileCategory.TEXT -> "文本"
        FileCategory.OTHER -> "其他"
    }

    fun nodeLocation(projectName: String, nodeTitle: String?): String =
        if (nodeTitle == null) "$projectName · 未关联节点" else "$projectName · $nodeTitle"

    /** 播放进度格式化：mm:ss，超过一小时为 h:mm:ss。 */
    fun duration(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
        }
    }
}
