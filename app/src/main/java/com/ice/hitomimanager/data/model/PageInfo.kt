package com.ice.hitomimanager.data.model

data class PageInfo(
    val entryName: String,
    val modifiedTimeMillis: Long?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int
)