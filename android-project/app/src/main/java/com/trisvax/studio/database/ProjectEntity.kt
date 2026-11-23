package com.trisvax.studio.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val thumbnailPath: String?,
    val durationMs: Long,
    val storageSizeBytes: Long,
    val timelineJson: String,
    val lastModifiedAt: Long,
    val createdAt: Long,
    val exportedUri: String? = null
)
