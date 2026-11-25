package com.projecttrack.app.data.backup

/**
 * 备份格式版本与数据库版本的兼容层。
 *
 * - `formatVersion` 描述 ZIP/JSON 容器结构，由对应的 reader 负责解码。
 * - `databaseVersion` 描述备份生成时的 Room schema；reader 负责把旧 schema 的 DTO
 *   映射到当前业务模型，而不是简单地要求它等于当前 Room 版本。
 *
 * 当前只发布 format v1 / database v1。未来 Room 升级时，必须在 [BackupV1Reader]
 * 中补充 v1 数据到当前业务模型的映射，并新增相应迁移测试；不能通过改写旧备份中的
 * databaseVersion 或跳过字段/关系/SHA-256 校验来伪造兼容。
 */
internal data class DecodedBackup(
    val manifest: BackupManifest,
    val data: BackupData,
)

internal object BackupCompatibility {

    fun decode(
        manifestJson: String,
        dataJson: String,
        currentDatabaseVersion: Int,
    ): DecodedBackup {
        val manifest = BackupJson.decodeManifest(manifestJson)

        if (manifest.formatVersion > BackupFormat.FORMAT_VERSION) {
            throw BackupValidationException(
                "不支持的备份格式版本：${manifest.formatVersion}（当前支持版本 ${BackupFormat.FORMAT_VERSION}），请升级应用后再恢复",
            )
        }
        val reader: BackupDataReader = when (manifest.formatVersion) {
            1 -> BackupV1Reader
            else -> throw BackupValidationException(
                "不支持的备份格式版本：${manifest.formatVersion}（当前支持版本 ${BackupFormat.FORMAT_VERSION}）",
            )
        }
        if (manifest.databaseVersion > currentDatabaseVersion) {
            throw BackupValidationException(
                "备份数据库版本 ${manifest.databaseVersion} 高于当前应用支持的数据库版本 " +
                    "$currentDatabaseVersion，请升级应用后再恢复；现有数据未被修改",
            )
        }
        if (!reader.supportsDatabaseVersion(manifest.databaseVersion)) {
            throw BackupValidationException(
                "当前应用不支持从数据库版本 ${manifest.databaseVersion} 的备份恢复",
            )
        }

        val rawData = BackupJson.decodeData(dataJson)
        val mappedData = reader.mapToCurrent(rawData, manifest.databaseVersion, currentDatabaseVersion)
        return DecodedBackup(manifest = manifest, data = mappedData)
    }
}

private interface BackupDataReader {
    fun supportsDatabaseVersion(version: Int): Boolean

    fun mapToCurrent(
        data: BackupData,
        sourceDatabaseVersion: Int,
        currentDatabaseVersion: Int,
    ): BackupData
}

/**
 * 固定 v1 读取器。v1 备份的 DTO 与当前业务模型一致，因此这里是恒等映射；
 * 未来 Room 升级时在此加入显式字段映射，并保留对所有仍支持 source 版本的测试。
 */
private object BackupV1Reader : BackupDataReader {
    override fun supportsDatabaseVersion(version: Int): Boolean = version == 1

    override fun mapToCurrent(
        data: BackupData,
        sourceDatabaseVersion: Int,
        currentDatabaseVersion: Int,
    ): BackupData {
        // 这里不能静默丢弃字段：未来 schema 变化时逐字段映射，保持关系与附件元数据完整。
        return data
    }
}
