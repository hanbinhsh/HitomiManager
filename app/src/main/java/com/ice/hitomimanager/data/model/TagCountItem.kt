package com.ice.hitomimanager.data.model

data class TagCountItem(
    val tagKey: String,
    val namespace: String,
    val name: String,
    val translatedName: String?,
    val bookCount: Int
)

enum class TagSortMode {
    CountDesc,
    NameAsc
}

enum class HomeTab {
    Library,
    Tags,
    Search,
    Tasks
}

enum class TagFilterTab {
    Tag,
    Artist,
    Group,
    Series,
    Character
}