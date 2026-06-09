package com.ice.hitomimanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "match_candidate",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["galleryId"])
    ]
)
data class MatchCandidateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val taskId: Long,

    val galleryId: String,
    val title: String,
    val japaneseTitle: String?,
    val language: String?,
    val type: String?,
    val date: String?,
    val pageCount: Int,

    val selected: Boolean = false
)