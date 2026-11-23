package com.projecttrack.app.data.local

import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.Note
import com.projecttrack.app.data.model.ProgressNode
import com.projecttrack.app.data.model.Project
import com.projecttrack.app.data.model.StoredFile

fun ProjectEntity.toModel(): Project = Project(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NodeEntity.toModel(): ProgressNode = ProgressNode(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    occurredAt = occurredAt,
    createdAt = createdAt,
)

fun NoteEntity.toModel(): Note = Note(
    id = id,
    projectId = projectId,
    nodeId = nodeId,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FileEntity.toModel(): StoredFile = StoredFile(
    id = id,
    projectId = projectId,
    nodeId = nodeId,
    displayName = displayName,
    mimeType = mimeType,
    extension = extension,
    sizeBytes = sizeBytes,
    category = category,
    relativePath = relativePath,
    importedAt = importedAt,
)
