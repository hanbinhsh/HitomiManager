package com.ice.hitomimanager.domain.scanner

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File

class CoverCache(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "cover_cache",
        Context.MODE_PRIVATE
    )

    fun getValidCover(file: DocumentFile): String? {
        val key = keyOf(file)

        val savedPath = prefs.getString("${key}_path", null) ?: return null
        val savedLastModified = prefs.getLong("${key}_last_modified", -1L)
        val savedLength = prefs.getLong("${key}_length", -1L)

        val currentLastModified = file.lastModified()
        val currentLength = file.length()

        val coverFile = File(savedPath)

        return if (
            coverFile.exists() &&
            coverFile.length() > 0L &&
            savedLastModified == currentLastModified &&
            savedLength == currentLength
        ) {
            savedPath
        } else {
            null
        }
    }

    fun saveCover(
        file: DocumentFile,
        coverPath: String
    ) {
        val key = keyOf(file)

        prefs.edit()
            .putString("${key}_path", coverPath)
            .putLong("${key}_last_modified", file.lastModified())
            .putLong("${key}_length", file.length())
            .apply()
    }

    private fun keyOf(file: DocumentFile): String {
        return file.uri.toString()
            .hashCode()
            .toString()
            .replace("-", "m")
    }
}