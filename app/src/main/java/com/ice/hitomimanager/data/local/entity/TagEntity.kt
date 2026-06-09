package com.ice.hitomimanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tag",
    indices = [
        Index(value = ["namespace", "name"], unique = true)
    ]
)
data class TagEntity(
    @PrimaryKey
    val key: String,
    val namespace: String,
    val name: String,
    val translatedName: String? = null
)