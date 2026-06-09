package com.ice.hitomimanager.domain.scanner

data class ScanProgress(
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String? = null
)