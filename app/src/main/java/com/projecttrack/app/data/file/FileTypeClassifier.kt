package com.projecttrack.app.data.file

import com.projecttrack.app.data.model.FileCategory
import java.util.Locale

/**
 * 文件分类识别：综合 MIME 与扩展名判断文件属于音频、视频、纯文本还是其他。
 *
 * 原则：
 * - MIME 是强信号时优先使用 MIME（例如以 audio、video 开头的 MIME，或 application/json）。
 * - MIME 缺失或仅为通用容器类型（如 application/octet-stream）时，使用扩展名判断。
 * - 代码与 JSON/XML/YAML/CSV 等都归入纯文本，由文本预览读取。
 * - 无法识别时归入其他，仅提供外部应用打开。
 */
object FileTypeClassifier {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "wave", "flac", "ogg", "oga", "opus", "aac", "m4a", "m4b",
        "mka", "amr", "awb", "wma", "mid", "midi", "aiff", "aif", "aifc", "ape",
        "wv", "tta", "dsf", "dff", "ac3", "eac3", "dts", "ra", "ram", "3ga",
        "caf", "au", "snd", "mpc", "spx",
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "f4v", "mpg",
        "mpeg", "mpe", "mts", "m2ts", "3gp", "3g2", "ogv", "rm", "rmvb", "asf",
        "vob", "divx", "xvid", "ogm", "mxf", "m2v", "h264", "h265", "hevc", "av1",
        "mjpeg", "dv", "wtv", "mp2t",
    )

    private val TEXT_EXTENSIONS = setOf(
        "txt", "text", "log", "md", "markdown", "json", "jsonl", "ndjson", "xml",
        "xsl", "xslt", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
        "env", "csv", "tsv", "kt", "kts", "java", "py", "js", "mjs", "cjs", "jsx",
        "ts", "tsx", "html", "htm", "xhtml", "css", "scss", "sass", "less", "sql",
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "psm1", "c", "h", "cc",
        "cpp", "cxx", "hpp", "hh", "cs", "rs", "go", "rb", "php", "swift", "m",
        "mm", "lua", "pl", "pm", "r", "dart", "scala", "groovy", "gradle", "make",
        "cmake", "pro", "patch", "diff", "srt", "vtt", "ass", "ssa", "rtf",
    )

    private val TEXT_APPLICATION_MIME_TYPES = setOf(
        "application/json",
        "application/ld+json",
        "application/xml",
        "application/xhtml+xml",
        "application/xml-dtd",
        "application/javascript",
        "application/x-javascript",
        "application/ecmascript",
        "application/typescript",
        "application/x-typescript",
        "application/yaml",
        "application/x-yaml",
        "application/toml",
        "application/sql",
        "application/graphql",
        "application/x-sh",
        "application/x-shellscript",
        "application/x-httpd-php",
        "application/x-httpd-php-source",
        "application/x-properties",
        "application/x-ndjson",
        "application/x-json",
    )

    private val TEXT_FILE_NAMES = setOf(
        "makefile", "dockerfile", "readme", "license", "licence", "changelog",
        ".gitignore", ".gitattributes", ".editorconfig", ".env", ".npmrc",
    )

    private val GENERIC_MIME_TYPES = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/unknown",
        "application/binary",
    )

    fun normalizeMimeType(mimeType: String?): String? {
        val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return normalized?.takeIf { it.isNotEmpty() }
    }

    /** 返回小写扩展名（不含点）；没有扩展名时返回空字符串。 */
    fun extensionOf(displayName: String): String {
        val fileName = displayName.substringAfterLast('/').substringAfterLast('\\')
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == fileName.lastIndex) return ""
        return fileName.substring(dotIndex + 1).lowercase(Locale.ROOT).take(16)
    }

    fun classify(displayName: String, mimeType: String?): FileCategory {
        val mime = normalizeMimeType(mimeType)
        val extension = extensionOf(displayName)
        val extensionCategory = categoryForExtension(extension)

        val mimeCategory = categoryForMime(mime)
        if (mimeCategory != null) {
            // 通用容器 MIME 不可靠，交给扩展名判断。
            if (mime in GENERIC_MIME_TYPES && extensionCategory != null) return extensionCategory
            return mimeCategory
        }
        if (extensionCategory != null) return extensionCategory
        if (displayName.lowercase(Locale.ROOT) in TEXT_FILE_NAMES) return FileCategory.TEXT
        return FileCategory.OTHER
    }

    fun categoryForMime(mimeType: String?): FileCategory? {
        val mime = normalizeMimeType(mimeType) ?: return null
        if (mime in GENERIC_MIME_TYPES) return null
        return when {
            mime.startsWith("audio/") -> FileCategory.AUDIO
            mime.startsWith("video/") -> FileCategory.VIDEO
            mime.startsWith("text/") -> FileCategory.TEXT
            mime in TEXT_APPLICATION_MIME_TYPES -> FileCategory.TEXT
            mime.endsWith("+json") || mime.endsWith("+xml") -> FileCategory.TEXT
            else -> null
        }
    }

    fun categoryForExtension(extension: String): FileCategory? {
        val normalized = extension.trim().lowercase(Locale.ROOT).removePrefix(".")
        if (normalized.isEmpty()) return null
        return when {
            normalized in AUDIO_EXTENSIONS -> FileCategory.AUDIO
            normalized in VIDEO_EXTENSIONS -> FileCategory.VIDEO
            normalized in TEXT_EXTENSIONS -> FileCategory.TEXT
            else -> null
        }
    }

    fun supportsInAppPreview(category: FileCategory): Boolean = when (category) {
        FileCategory.AUDIO, FileCategory.VIDEO, FileCategory.TEXT -> true
        FileCategory.OTHER -> false
    }
}
