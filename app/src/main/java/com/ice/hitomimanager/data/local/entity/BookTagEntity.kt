package com.ice.hitomimanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "book_tag",
    primaryKeys = ["bookUriString", "tagKey"],
    indices = [
        Index(value = ["bookUriString"]),
        Index(value = ["tagKey"]),
        // Supports the paging filter's JOIN/GROUP BY lookup by tag first.
        Index(value = ["tagKey", "bookUriString"])
    ]
)
data class BookTagEntity(
    val bookUriString: String,
    val tagKey: String
)
