package com.ice.hitomimanager.data.model

object MatchTaskStatus {
    const val Pending = "pending"
    const val Running = "running"
    const val AutoMatched = "auto_matched"
    const val NeedReview = "need_review"
    const val Failed = "failed"
    const val Skipped = "skipped"
}

enum class MatchTaskFilter {
    All,
    Running,
    Success,
    NeedReview,
    Failed,
    Skipped,
    Unqueued
}

enum class SettingsTab {
    General,
    Display,
    Match
}