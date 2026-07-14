package com.ice.hitomimanager.ui.screen

import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.MatchTaskStatus
import com.ice.hitomimanager.data.model.TagCountItem

fun formatTagLabel(
    tag: TagCountItem,
    showNamespace: Boolean,
    distinguishGenderTags: Boolean
): String = formatTagLabel(
    namespace = tag.namespace,
    name = tag.name,
    translatedName = tag.translatedName,
    showNamespace = showNamespace,
    distinguishGenderTags = distinguishGenderTags
)

fun formatTagLabel(
    tag: TagEntity,
    showNamespace: Boolean,
    distinguishGenderTags: Boolean
): String = formatTagLabel(
    namespace = tag.namespace,
    name = tag.name,
    translatedName = tag.translatedName,
    showNamespace = showNamespace,
    distinguishGenderTags = distinguishGenderTags
)

private fun formatTagLabel(
    namespace: String,
    name: String,
    translatedName: String?,
    showNamespace: Boolean,
    distinguishGenderTags: Boolean
): String {
    val displayName = translatedName ?: name
    return when {
        showNamespace -> "[$namespace] $displayName"
        distinguishGenderTags && namespace == "male" -> "♂ $displayName"
        distinguishGenderTags && namespace == "female" -> "♀ $displayName"
        else -> displayName
    }
}

fun matchTaskStatusLabel(status: String): String = when (status) {
    MatchTaskStatus.Pending -> "等待中"
    MatchTaskStatus.Running -> "匹配中"
    MatchTaskStatus.AutoMatched -> "成功"
    MatchTaskStatus.NeedReview -> "需复核"
    MatchTaskStatus.Failed -> "失败"
    MatchTaskStatus.Skipped -> "跳过"
    else -> status
}
