package com.projecttrack.app.data.backup

/**
 * 备份内容语义校验：跨条目关系、时间、分类、附件完整性与 manifest 计数。
 * 只有全部校验通过后，恢复流程才允许触碰正式数据库与附件目录。
 */
object BackupValidator {

    fun validate(
        manifest: BackupManifest,
        data: BackupData,
        extraction: BackupExtraction,
    ) {
        requireCount(manifest.projectCount, data.projects.size, "项目")
        requireCount(manifest.nodeCount, data.nodes.size, "节点")
        requireCount(manifest.noteCount, data.notes.size, "记录")
        requireCount(manifest.fileCount, data.files.size, "文件")

        val projectsById = mutableMapOf<Long, BackupProject>()
        data.projects.forEach { project ->
            if (project.id <= 0L) fail("项目 ID 必须为正数")
            if (projectsById.put(project.id, project) != null) fail("项目 ID 重复：${project.id}")
            if (project.name.isBlank()) fail("项目名称不能为空")
            if (project.createdAt < 0L || project.updatedAt < 0L) fail("项目时间字段无效")
        }

        val nodesById = mutableMapOf<Long, BackupNode>()
        data.nodes.forEach { node ->
            if (node.id <= 0L) fail("节点 ID 必须为正数")
            if (nodesById.put(node.id, node) != null) fail("节点 ID 重复：${node.id}")
            if (!projectsById.containsKey(node.projectId)) fail("节点引用了不存在的项目：${node.projectId}")
            if (node.title.isBlank()) fail("节点标题不能为空")
            if (node.occurredAt < 0L || node.createdAt < 0L) fail("节点时间字段无效")
        }

        val noteIds = mutableSetOf<Long>()
        data.notes.forEach { note ->
            if (note.id <= 0L) fail("记录 ID 必须为正数")
            if (!noteIds.add(note.id)) fail("记录 ID 重复：${note.id}")
            if (!projectsById.containsKey(note.projectId)) fail("记录引用了不存在的项目：${note.projectId}")
            if (note.content.isBlank()) fail("记录内容不能为空")
            if (note.createdAt < 0L || note.updatedAt < 0L) fail("记录时间字段无效")
            val nodeId = note.nodeId
            if (nodeId != null) {
                val node = nodesById[nodeId] ?: fail("记录引用了不存在的节点：$nodeId")
                if (node.projectId != note.projectId) fail("记录与节点不属于同一项目")
            }
        }

        val fileIds = mutableSetOf<Long>()
        val archivePaths = mutableSetOf<String>()
        var totalFileBytes = 0L
        data.files.forEach { file ->
            if (file.id <= 0L) fail("文件 ID 必须为正数")
            if (!fileIds.add(file.id)) fail("文件 ID 重复：${file.id}")
            if (!projectsById.containsKey(file.projectId)) fail("文件引用了不存在的项目：${file.projectId}")
            val nodeId = file.nodeId
            if (nodeId != null) {
                val node = nodesById[nodeId] ?: fail("文件引用了不存在的节点：$nodeId")
                if (node.projectId != file.projectId) fail("文件与节点不属于同一项目")
            }
            if (file.displayName.isBlank()) fail("文件展示名称不能为空")
            if (file.sizeBytes < 0L) fail("文件大小不能为负数")
            if (file.importedAt < 0L) fail("文件导入时间无效")
            if (file.mimeType != null && file.mimeType.isBlank()) fail("文件 MIME 不能为空白字符串")
            val archivePath = file.archivePath
            if (!archivePaths.add(archivePath)) fail("附件路径重复：$archivePath")
            validateArchivePath(archivePath)
            if (!BackupHashing.isSha256Hex(file.sha256)) fail("文件的 SHA-256 格式无效：${file.displayName}")
            totalFileBytes = Math.addExact(totalFileBytes, file.sizeBytes)
        }

        if (manifest.totalFileBytes != totalFileBytes) {
            fail("manifest 文件总大小与数据不一致")
        }

        val referencedPaths = archivePaths
        val extractedPaths = extraction.attachments.keys
        if (referencedPaths != extractedPaths) {
            val missing = referencedPaths - extractedPaths
            val extra = extractedPaths - referencedPaths
            if (missing.isNotEmpty()) fail("备份缺少附件：${missing.first()}")
            if (extra.isNotEmpty()) fail("备份包含未引用的附件：${extra.first()}")
        }

        data.files.forEach { file ->
            val attachment = extraction.attachments.getValue(file.archivePath)
            if (attachment.sizeBytes != file.sizeBytes) {
                fail("附件大小与 data.json 声明不符：${file.displayName}")
            }
            if (!attachment.sha256.equals(file.sha256, ignoreCase = true)) {
                fail("附件 SHA-256 校验失败：${file.displayName}")
            }
        }
    }

    private fun validateArchivePath(path: String) {
        if (!path.startsWith(BackupFormat.ATTACHMENTS_PREFIX)) {
            fail("附件路径必须以 ${BackupFormat.ATTACHMENTS_PREFIX} 开头：$path")
        }
        val fileName = path.removePrefix(BackupFormat.ATTACHMENTS_PREFIX)
        if (fileName.isEmpty() || fileName.contains('/') || fileName.contains('\\') ||
            fileName == "." || fileName == ".." || fileName.contains('\u0000')
        ) {
            fail("附件路径无效：$path")
        }
    }

    private fun requireCount(declared: Int, actual: Int, label: String) {
        if (declared != actual) fail("manifest 的${label}数量与数据不一致")
    }

    private fun fail(message: String): Nothing = throw BackupValidationException(message)
}
