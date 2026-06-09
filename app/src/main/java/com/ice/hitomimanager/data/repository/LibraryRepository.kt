package com.ice.hitomimanager.data.repository

import android.content.Context
import android.net.Uri
import com.ice.hitomimanager.data.local.AppDatabase
import com.ice.hitomimanager.data.local.entity.BookEntity
import com.ice.hitomimanager.data.local.entity.BookTagEntity
import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.toBookItem
import com.ice.hitomimanager.domain.scanner.DocumentTreeScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.model.MatchTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.ice.hitomimanager.domain.scanner.ScanProgress

class LibraryRepository(
    private val context: Context
) {
    private val db = AppDatabase.get(context)
    private val bookDao = db.bookDao()
    private val tagDao = db.tagDao()
    private val scanner = DocumentTreeScanner(context)

    private val matchTaskDao = db.matchTaskDao()

    fun observeBooks(
        libraryRootUriString: String
    ): Flow<List<BookItem>> {
        return bookDao.observeBooks(libraryRootUriString)
            .map { list ->
                list.map { it.toBookItem() }
            }
    }

    fun observeMatchTasksByStatuses(
        libraryRootUriString: String,
        statuses: List<String>
    ): Flow<List<MatchTaskEntity>> {
        return if (statuses.isEmpty()) {
            matchTaskDao.observeTasks(libraryRootUriString)
        } else {
            matchTaskDao.observeTasksByStatuses(
                libraryRootUriString = libraryRootUriString,
                statuses = statuses
            )
        }
    }

    suspend fun getUnmatchedBooks(
        libraryRootUriString: String
    ): List<BookItem> {
        return bookDao.getUnmatchedBooks(libraryRootUriString)
            .map { it.toBookItem() }
    }

    suspend fun clearDatabase() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    suspend fun createMatchTask(
        book: BookItem,
        libraryRootUriString: String,
        query: String,
        localPageCount: Int?
    ): Long {
        val now = System.currentTimeMillis()

        return matchTaskDao.insertTask(
            MatchTaskEntity(
                libraryRootUriString = libraryRootUriString,
                bookUriString = book.uriString,
                displayName = book.displayName,
                coverFilePath = book.coverFilePath,
                query = query,
                localPageCount = localPageCount,
                status = MatchTaskStatus.Pending,
                matchedGalleryId = null,
                candidateCount = 0,
                errorMessage = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateMatchTask(
        task: MatchTaskEntity
    ) {
        matchTaskDao.updateTask(task)
    }

    suspend fun getMatchTask(
        taskId: Long
    ): MatchTaskEntity? {
        return matchTaskDao.getTask(taskId)
    }

    suspend fun replaceCandidatesForTask(
        taskId: Long,
        candidates: List<HitomiBookMeta>,
        selectedGalleryId: String? = null
    ) {
        matchTaskDao.deleteCandidatesForTask(taskId)

        val entities = candidates.map { meta ->
            MatchCandidateEntity(
                taskId = taskId,
                galleryId = meta.id,
                title = meta.title,
                japaneseTitle = meta.japaneseTitle,
                language = meta.language,
                type = meta.type,
                date = meta.date,
                pageCount = meta.pageCount,
                selected = selectedGalleryId == meta.id
            )
        }

        if (entities.isNotEmpty()) {
            matchTaskDao.insertCandidates(entities)
        }
    }

    fun observeTagCounts(
        libraryRootUriString: String
    ): Flow<List<TagCountItem>> {
        return tagDao.observeTagCounts(libraryRootUriString)
    }

    fun observeBooksByAllTags(
        libraryRootUriString: String,
        tagKeys: List<String>
    ): Flow<List<BookItem>> {
        if (tagKeys.isEmpty()) {
            return observeBooks(libraryRootUriString)
        }

        return bookDao.observeBooksByAllTags(
            libraryRootUriString = libraryRootUriString,
            tagKeys = tagKeys,
            tagCount = tagKeys.size
        ).map { list ->
            list.map { it.toBookItem() }
        }
    }

    fun observeBooksBySearch(
        libraryRootUriString: String,
        query: String
    ): Flow<List<BookItem>> {
        val cleaned = query.trim()

        if (cleaned.isBlank()) {
            return observeBooks(libraryRootUriString)
        }

        return bookDao.observeBooksBySearch(
            libraryRootUriString = libraryRootUriString,
            query = cleaned
        ).map { list ->
            list.map { it.toBookItem() }
        }
    }

    fun observeTagsForBook(uriString: String): Flow<List<TagEntity>> {
        return tagDao.observeTagsForBook(uriString)
    }

    suspend fun scanAndSync(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {}
    ) {
        val now = System.currentTimeMillis()
        val rootUriString = treeUri.toString()
        val scannedBooks = scanner.scan(
            treeUri = treeUri,
            onProgress = onProgress
        )

        for (scanned in scannedBooks) {
            db.withTransaction {
                val oldBySameUri = bookDao.findByUri(scanned.uriString)

                if (oldBySameUri != null) {
                    val entity = oldBySameUri.copy(
                        libraryRootUriString = rootUriString,
                        displayName = scanned.displayName,
                        fileSize = scanned.fileSize,
                        lastModified = scanned.lastModified,
                        coverFilePath = scanned.coverFilePath ?: oldBySameUri.coverFilePath,
                        updatedAt = now
                    )

                    bookDao.upsert(entity)
                    return@withTransaction
                }

                val movedOld = bookDao.findReusableMovedBook(
                    displayName = scanned.displayName,
                    fileSize = scanned.fileSize,
                    uriString = scanned.uriString
                )

                if (movedOld != null) {
                    bookDao.migrateBookUri(
                        oldUriString = movedOld.uriString,
                        newUriString = scanned.uriString,
                        libraryRootUriString = rootUriString,
                        displayName = scanned.displayName,
                        fileSize = scanned.fileSize,
                        lastModified = scanned.lastModified,
                        coverFilePath = scanned.coverFilePath,
                        updatedAt = now
                    )

                    tagDao.migrateBookUri(
                        oldUriString = movedOld.uriString,
                        newUriString = scanned.uriString
                    )

                    matchTaskDao.migrateBookUri(
                        oldUriString = movedOld.uriString,
                        newUriString = scanned.uriString,
                        libraryRootUriString = rootUriString,
                        displayName = scanned.displayName,
                        coverFilePath = scanned.coverFilePath,
                        updatedAt = now
                    )

                    return@withTransaction
                }

                val newEntity = BookEntity(
                    displayName = scanned.displayName,
                    uriString = scanned.uriString,
                    libraryRootUriString = rootUriString,
                    fileSize = scanned.fileSize,
                    lastModified = scanned.lastModified,
                    coverFilePath = scanned.coverFilePath,
                    createdAt = now,
                    updatedAt = now
                )

                bookDao.upsert(newEntity)
            }
        }

        // 不要 deleteMissing。
        // 多目录模式下，扫描 B 目录时不能删除 A 目录的数据。
    }

    fun observeMatchTask(taskId: Long): Flow<MatchTaskEntity?> {
        return matchTaskDao.observeTask(taskId)
    }

    fun observeUnqueuedUnmatchedBooks(
        libraryRootUriString: String
    ): Flow<List<BookItem>> {
        return bookDao.observeUnqueuedUnmatchedBooks(libraryRootUriString)
            .map { list ->
                list.map { it.toBookItem() }
            }
    }

    fun observeCandidatesForTask(taskId: Long): Flow<List<MatchCandidateEntity>> {
        return matchTaskDao.observeCandidatesForTask(taskId)
    }

    suspend fun markSelectedCandidate(
        taskId: Long,
        galleryId: String
    ) {
        matchTaskDao.clearSelectedCandidate(taskId)
        matchTaskDao.markSelectedCandidate(
            taskId = taskId,
            galleryId = galleryId
        )
    }

    suspend fun getMatchTasksByStatuses(
        libraryRootUriString: String,
        statuses: List<String>
    ): List<MatchTaskEntity> {
        return matchTaskDao.getTasksByStatuses(
            libraryRootUriString = libraryRootUriString,
            statuses = statuses
        )
    }

    suspend fun getMatchTasksByBookUri(
        bookUriString: String
    ): List<MatchTaskEntity> {
        return matchTaskDao.getTasksByBookUri(bookUriString)
    }

    suspend fun markInterruptedMatchTasksAsFailed() {
        matchTaskDao.markTasksByStatuses(
            oldStatuses = listOf(
                MatchTaskStatus.Pending,
                MatchTaskStatus.Running
            ),
            newStatus = MatchTaskStatus.Failed,
            errorMessage = "应用关闭或进程被系统回收，任务已中断",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun bindHitomiMeta(
        uriString: String,
        meta: HitomiBookMeta
    ) {
        val now = System.currentTimeMillis()

        bookDao.updateHitomiMeta(
            uriString = uriString,
            galleryId = meta.id,
            title = meta.title,
            japaneseTitle = meta.japaneseTitle,
            language = meta.language,
            type = meta.type,
            pageCount = meta.pageCount,
            matchStatus = "manual_matched",
            updatedAt = now
        )

        val extraTags = buildList {
            meta.artists.forEach { add("artist" to it) }
            meta.groups.forEach { add("group" to it) }
            meta.series.forEach { add("series" to it) }
            meta.characters.forEach { add("character" to it) }
            meta.tags.forEach { add(it.namespace to it.name) }
        }.distinctBy { "${it.first}:${it.second}" }

        val tagEntities = extraTags.map { (namespace, name) ->
            TagEntity(
                key = makeTagKey(namespace, name),
                namespace = namespace,
                name = name
            )
        }

        val bookTags = tagEntities.map { tag ->
            BookTagEntity(
                bookUriString = uriString,
                tagKey = tag.key
            )
        }

        tagDao.deleteTagsForBook(uriString)

        if (tagEntities.isNotEmpty()) {
            tagDao.upsertTags(tagEntities)
            tagDao.insertBookTags(bookTags)
        }
    }

    suspend fun getNextNeedReviewTask(
        currentTaskId: Long,
        libraryRootUriString: String?
    ): MatchTaskEntity? {
        if (libraryRootUriString == null) return null

        return matchTaskDao.getNextTaskByStatus(
            libraryRootUriString = libraryRootUriString,
            status = MatchTaskStatus.NeedReview,
            currentTaskId = currentTaskId
        )
    }

    private fun makeTagKey(
        namespace: String,
        name: String
    ): String {
        return "${namespace.lowercase()}:${name.lowercase()}"
    }
}