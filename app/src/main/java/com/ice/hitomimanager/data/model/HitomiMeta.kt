package com.ice.hitomimanager.data.model

data class HitomiCandidate(
    val id: String,
    val url: String,
    val searchTitle: String
)

data class HitomiTagMeta(
    val namespace: String,
    val name: String
)

data class HitomiBookMeta(
    val id: String,
    val title: String,
    val japaneseTitle: String?,
    val type: String?,
    val language: String?,
    val date: String?,
    val pageCount: Int,
    val artists: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<HitomiTagMeta> = emptyList()
)