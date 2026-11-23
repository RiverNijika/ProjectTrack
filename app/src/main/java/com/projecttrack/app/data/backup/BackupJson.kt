package com.projecttrack.app.data.backup

import com.projecttrack.app.data.model.FileCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * manifest.json / data.json 的编码与严格解码。
 *
 * 所有必需字段都必须存在且类型正确；未知字段被忽略，未知格式版本和数据库版本会明确拒绝。
 */
object BackupJson {
    private val json = Json
    private val prettyJson = Json { prettyPrint = true }

    fun encodeManifest(manifest: BackupManifest): String =
        prettyJson.encodeToString(JsonElement.serializer(), buildManifest(manifest))

    fun encodeData(data: BackupData): String =
        prettyJson.encodeToString(JsonElement.serializer(), buildData(data))

    fun decodeManifest(text: String, expectedDatabaseVersion: Int): BackupManifest {
        val root = parseObject(text, "manifest.json")
        val formatName = root.requiredString("formatName", "manifest.json")
        if (formatName != BackupFormat.FORMAT_NAME) {
            throw BackupValidationException("这不是 ProjectTrack 备份：manifest 格式名称不匹配")
        }
        val formatVersion = root.requiredInt("formatVersion", "manifest.json")
        if (formatVersion != BackupFormat.FORMAT_VERSION) {
            throw BackupValidationException("不支持的备份格式版本：$formatVersion（当前支持版本 ${BackupFormat.FORMAT_VERSION}）")
        }
        val appVersionName = root.requiredString("appVersionName", "manifest.json")
        if (appVersionName.isBlank()) {
            throw BackupValidationException("manifest.json 缺少有效的应用版本名称")
        }
        val appVersionCode = root.requiredLong("appVersionCode", "manifest.json")
        if (appVersionCode < 0L) {
            throw BackupValidationException("manifest.json 的应用版本号无效")
        }
        val databaseVersion = root.requiredInt("databaseVersion", "manifest.json")
        if (databaseVersion != expectedDatabaseVersion) {
            throw BackupValidationException(
                "备份数据库版本 $databaseVersion 与当前版本 $expectedDatabaseVersion 不兼容，当前版本不支持旧版或新版数据库备份恢复",
            )
        }
        val createdAt = root.requiredLong("createdAt", "manifest.json")
        if (createdAt < 0L) {
            throw BackupValidationException("manifest.json 的创建时间无效")
        }
        val projectCount = root.requiredInt("projectCount", "manifest.json")
        val nodeCount = root.requiredInt("nodeCount", "manifest.json")
        val noteCount = root.requiredInt("noteCount", "manifest.json")
        val fileCount = root.requiredInt("fileCount", "manifest.json")
        val totalFileBytes = root.requiredLong("totalFileBytes", "manifest.json")
        if (projectCount < 0 || nodeCount < 0 || noteCount < 0 || fileCount < 0 || totalFileBytes < 0L) {
            throw BackupValidationException("manifest.json 中存在负数计数")
        }
        return BackupManifest(
            formatName = formatName,
            formatVersion = formatVersion,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            databaseVersion = databaseVersion,
            createdAt = createdAt,
            projectCount = projectCount,
            nodeCount = nodeCount,
            noteCount = noteCount,
            fileCount = fileCount,
            totalFileBytes = totalFileBytes,
        )
    }

    fun decodeData(text: String): BackupData {
        val root = parseObject(text, "data.json")
        val projects = root.requiredArray("projects", "data.json").mapIndexed { index, element ->
            val item = asObject(element, "data.json.projects[$index]")
            BackupProject(
                id = item.requiredLong("id", "data.json.projects[$index]"),
                name = item.requiredString("name", "data.json.projects[$index]"),
                description = item.requiredString("description", "data.json.projects[$index]"),
                createdAt = item.requiredLong("createdAt", "data.json.projects[$index]"),
                updatedAt = item.requiredLong("updatedAt", "data.json.projects[$index]"),
            )
        }
        val nodes = root.requiredArray("nodes", "data.json").mapIndexed { index, element ->
            val item = asObject(element, "data.json.nodes[$index]")
            BackupNode(
                id = item.requiredLong("id", "data.json.nodes[$index]"),
                projectId = item.requiredLong("projectId", "data.json.nodes[$index]"),
                title = item.requiredString("title", "data.json.nodes[$index]"),
                description = item.requiredString("description", "data.json.nodes[$index]"),
                occurredAt = item.requiredLong("occurredAt", "data.json.nodes[$index]"),
                createdAt = item.requiredLong("createdAt", "data.json.nodes[$index]"),
            )
        }
        val notes = root.requiredArray("notes", "data.json").mapIndexed { index, element ->
            val item = asObject(element, "data.json.notes[$index]")
            BackupNote(
                id = item.requiredLong("id", "data.json.notes[$index]"),
                projectId = item.requiredLong("projectId", "data.json.notes[$index]"),
                nodeId = item.nullableLong("nodeId", "data.json.notes[$index]"),
                content = item.requiredString("content", "data.json.notes[$index]"),
                createdAt = item.requiredLong("createdAt", "data.json.notes[$index]"),
                updatedAt = item.requiredLong("updatedAt", "data.json.notes[$index]"),
            )
        }
        val files = root.requiredArray("files", "data.json").mapIndexed { index, element ->
            val item = asObject(element, "data.json.files[$index]")
            val categoryName = item.requiredString("category", "data.json.files[$index]")
            val category = FileCategory.entries.firstOrNull { it.name == categoryName }
                ?: throw BackupValidationException("data.json.files[$index] 的文件分类无效：$categoryName")
            BackupFileRecord(
                id = item.requiredLong("id", "data.json.files[$index]"),
                projectId = item.requiredLong("projectId", "data.json.files[$index]"),
                nodeId = item.nullableLong("nodeId", "data.json.files[$index]"),
                displayName = item.requiredString("displayName", "data.json.files[$index]"),
                mimeType = item.nullableString("mimeType", "data.json.files[$index]"),
                extension = item.requiredString("extension", "data.json.files[$index]"),
                sizeBytes = item.requiredLong("sizeBytes", "data.json.files[$index]"),
                category = category,
                importedAt = item.requiredLong("importedAt", "data.json.files[$index]"),
                archivePath = item.requiredString("archivePath", "data.json.files[$index]"),
                sha256 = item.requiredString("sha256", "data.json.files[$index]"),
            )
        }
        return BackupData(projects = projects, nodes = nodes, notes = notes, files = files)
    }

    private fun buildManifest(manifest: BackupManifest): JsonObject = buildJsonObject {
        put("formatName", manifest.formatName)
        put("formatVersion", manifest.formatVersion)
        put("appVersionName", manifest.appVersionName)
        put("appVersionCode", manifest.appVersionCode)
        put("databaseVersion", manifest.databaseVersion)
        put("createdAt", manifest.createdAt)
        put("projectCount", manifest.projectCount)
        put("nodeCount", manifest.nodeCount)
        put("noteCount", manifest.noteCount)
        put("fileCount", manifest.fileCount)
        put("totalFileBytes", manifest.totalFileBytes)
    }

    private fun buildData(data: BackupData): JsonObject = buildJsonObject {
        put(
            "projects",
            buildJsonArray {
                data.projects.forEach { project ->
                    add(
                        buildJsonObject {
                            put("id", project.id)
                            put("name", project.name)
                            put("description", project.description)
                            put("createdAt", project.createdAt)
                            put("updatedAt", project.updatedAt)
                        },
                    )
                }
            },
        )
        put(
            "nodes",
            buildJsonArray {
                data.nodes.forEach { node ->
                    add(
                        buildJsonObject {
                            put("id", node.id)
                            put("projectId", node.projectId)
                            put("title", node.title)
                            put("description", node.description)
                            put("occurredAt", node.occurredAt)
                            put("createdAt", node.createdAt)
                        },
                    )
                }
            },
        )
        put(
            "notes",
            buildJsonArray {
                data.notes.forEach { note ->
                    add(
                        buildJsonObject {
                            put("id", note.id)
                            put("projectId", note.projectId)
                            putNullableLong("nodeId", note.nodeId)
                            put("content", note.content)
                            put("createdAt", note.createdAt)
                            put("updatedAt", note.updatedAt)
                        },
                    )
                }
            },
        )
        put(
            "files",
            buildJsonArray {
                data.files.forEach { file ->
                    add(
                        buildJsonObject {
                            put("id", file.id)
                            put("projectId", file.projectId)
                            putNullableLong("nodeId", file.nodeId)
                            put("displayName", file.displayName)
                            putNullableString("mimeType", file.mimeType)
                            put("extension", file.extension)
                            put("sizeBytes", file.sizeBytes)
                            put("category", file.category.name)
                            put("importedAt", file.importedAt)
                            put("archivePath", file.archivePath)
                            put("sha256", file.sha256)
                        },
                    )
                }
            },
        )
    }

    private fun JsonObjectBuilder.putNullableLong(key: String, value: Long?) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun JsonObjectBuilder.putNullableString(key: String, value: String?) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun parseObject(text: String, fileName: String): JsonObject = try {
        val element = json.parseToJsonElement(text)
        element as? JsonObject ?: throw BackupValidationException("$fileName 顶层必须是 JSON 对象")
    } catch (validation: BackupValidationException) {
        throw validation
    } catch (error: Exception) {
        throw BackupValidationException("$fileName 不是有效的 JSON")
    }

    private fun JsonObject.requiredArray(key: String, context: String): List<JsonElement> {
        val element = this[key] ?: throw BackupValidationException("$context 缺少字段 $key")
        return element as? JsonArray ?: throw BackupValidationException("$context 的字段 $key 必须是数组")
    }

    private fun JsonObject.requiredString(key: String, context: String): String {
        val element = this[key] ?: throw BackupValidationException("$context 缺少字段 $key")
        val primitive = element as? JsonPrimitive
            ?: throw BackupValidationException("$context 的字段 $key 必须是字符串")
        if (!primitive.isString) {
            throw BackupValidationException("$context 的字段 $key 必须是字符串")
        }
        return primitive.content
    }

    private fun JsonObject.nullableString(key: String, context: String): String? {
        if (!containsKey(key)) throw BackupValidationException("$context 缺少字段 $key")
        val element = this[key]
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
            ?: throw BackupValidationException("$context 的字段 $key 必须是字符串或 null")
        if (!primitive.isString) {
            throw BackupValidationException("$context 的字段 $key 必须是字符串或 null")
        }
        return primitive.content
    }

    private fun JsonObject.requiredLong(key: String, context: String): Long {
        val element = this[key] ?: throw BackupValidationException("$context 缺少字段 $key")
        val primitive = element as? JsonPrimitive
            ?: throw BackupValidationException("$context 的字段 $key 必须是整数")
        if (primitive.isString) {
            throw BackupValidationException("$context 的字段 $key 必须是整数")
        }
        return primitive.longOrNull
            ?: throw BackupValidationException("$context 的字段 $key 必须是整数")
    }

    private fun JsonObject.requiredInt(key: String, context: String): Int {
        val value = requiredLong(key, context)
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
            throw BackupValidationException("$context 的字段 $key 超出整数范围")
        }
        return value.toInt()
    }

    private fun JsonObject.nullableLong(key: String, context: String): Long? {
        if (!containsKey(key)) throw BackupValidationException("$context 缺少字段 $key")
        val element = this[key]
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
            ?: throw BackupValidationException("$context 的字段 $key 必须是整数或 null")
        if (primitive.isString) {
            throw BackupValidationException("$context 的字段 $key 必须是整数或 null")
        }
        return primitive.longOrNull
            ?: throw BackupValidationException("$context 的字段 $key 必须是整数或 null")
    }

    private fun asObject(element: JsonElement, context: String): JsonObject =
        element as? JsonObject ?: throw BackupValidationException("$context 必须是 JSON 对象")
}
