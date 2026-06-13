package com.ice.hitomimanager.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ice.hitomimanager.data.local.AppDatabase
import com.ice.hitomimanager.data.local.entity.BookEntity
import com.ice.hitomimanager.data.local.entity.BookTagEntity
import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.toBookItem
import com.ice.hitomimanager.domain.scanner.DocumentTreeScanner
import com.ice.hitomimanager.domain.scanner.CoverCache
import com.ice.hitomimanager.domain.reader.ComicArchiveReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.model.MatchTaskFilter
import com.ice.hitomimanager.data.model.MatchTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.ice.hitomimanager.domain.scanner.ScanProgress
import kotlinx.coroutines.flow.combine
import java.io.File

class LibraryRepository(
    private val context: Context
) {
    private var db = AppDatabase.get(context)
    private var bookDao = db.bookDao()
    private var tagDao = db.tagDao()
    private val scanner = DocumentTreeScanner(context)
    private val coverCache = CoverCache(context)

    private var matchTaskDao = db.matchTaskDao()

    private fun reconnectDatabase() {
        db = AppDatabase.get(context)
        bookDao = db.bookDao()
        tagDao = db.tagDao()
        matchTaskDao = db.matchTaskDao()
    }

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

    fun observeMatchTaskFilterCounts(
        libraryRootUriString: String
    ): Flow<Map<MatchTaskFilter, Int>> {
        return combine(
            matchTaskDao.observeStatusCounts(libraryRootUriString),
            bookDao.observeUnqueuedUnmatchedBookCount(libraryRootUriString)
        ) { statusCounts, unqueuedCount ->
            buildMatchTaskFilterCounts(statusCounts, unqueuedCount)
        }
    }

    suspend fun getMatchTaskFilterCounts(
        libraryRootUriString: String
    ): Map<MatchTaskFilter, Int> {
        return buildMatchTaskFilterCounts(
            statusCounts = matchTaskDao.getStatusCounts(libraryRootUriString),
            unqueuedCount = bookDao.countUnqueuedUnmatchedBooks(libraryRootUriString)
        )
    }

    private fun buildMatchTaskFilterCounts(
        statusCounts: List<com.ice.hitomimanager.data.local.dao.MatchTaskStatusCount>,
        unqueuedCount: Int
    ): Map<MatchTaskFilter, Int> {
        val byStatus = statusCounts.associate { it.status to it.count }
        val running = listOf(
            MatchTaskStatus.Pending,
            MatchTaskStatus.Running
        ).sumOf { byStatus[it] ?: 0 }

        return mapOf(
            MatchTaskFilter.All to byStatus.values.sum(),
            MatchTaskFilter.Running to running,
            MatchTaskFilter.Success to (byStatus[MatchTaskStatus.AutoMatched] ?: 0),
            MatchTaskFilter.NeedReview to (byStatus[MatchTaskStatus.NeedReview] ?: 0),
            MatchTaskFilter.Failed to (byStatus[MatchTaskStatus.Failed] ?: 0),
            MatchTaskFilter.Skipped to (byStatus[MatchTaskStatus.Skipped] ?: 0),
            MatchTaskFilter.Unqueued to unqueuedCount
        )
    }

    suspend fun getUnmatchedBooks(
        libraryRootUriString: String
    ): List<BookItem> {
        return bookDao.getUnmatchedBooks(libraryRootUriString)
            .map { it.toBookItem() }
    }

    suspend fun repairMissingCover(
        book: BookItem
    ) {
        withContext(Dispatchers.IO) {
            val currentPath = book.coverFilePath
            if (!currentPath.isNullOrBlank() && File(currentPath).exists()) {
                return@withContext
            }

            val documentFile = DocumentFile.fromSingleUri(
                context,
                Uri.parse(book.uriString)
            ) ?: return@withContext

            val coverFile = ComicArchiveReader.extractCoverToPersistentCache(
                context = context,
                archiveUri = documentFile.uri
            ) ?: return@withContext

            coverCache.saveCover(
                file = documentFile,
                coverPath = coverFile.absolutePath
            )

            val now = System.currentTimeMillis()
            bookDao.updateCoverFilePath(
                uriString = book.uriString,
                coverFilePath = coverFile.absolutePath,
                updatedAt = now
            )
            matchTaskDao.updateCoverFilePathForBook(
                bookUriString = book.uriString,
                coverFilePath = coverFile.absolutePath,
                updatedAt = now
            )
        }
    }

    suspend fun clearDatabase() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    suspend fun exportDatabaseTo(uri: Uri) {
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
                while (it.moveToNext()) {
                    // Drain the cursor so SQLite completes the checkpoint.
                }
            }

            val databaseFile = context.getDatabasePath("hitomi_manager.db")
            require(databaseFile.exists()) {
                "数据库文件不存在"
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                databaseFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: error("无法打开导出文件")
        }
    }

    suspend fun importDatabaseFrom(uri: Uri) {
        withContext(Dispatchers.IO) {
            val databaseFile = context.getDatabasePath("hitomi_manager.db")
            val tempFile = File(databaseFile.parentFile, "hitomi_manager_import_tmp.db")

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("无法打开导入文件")

            validateDatabaseFile(tempFile)

            AppDatabase.closeInstance()

            try {
                databaseFile.parentFile?.mkdirs()
                if (databaseFile.exists()) {
                    databaseFile.delete()
                }
                deleteDatabaseSidecarFiles(databaseFile)

                tempFile.inputStream().use { input ->
                    databaseFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                tempFile.delete()
                deleteDatabaseSidecarFiles(databaseFile)
                reconnectDatabase()
            } catch (e: Exception) {
                reconnectDatabase()
                throw e
            }
        }
    }

    private fun validateDatabaseFile(file: File) {
        require(file.exists() && file.length() > 0L) {
            "导入文件为空"
        }

        val sqlite = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        sqlite.use { database ->
            database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "导入文件不是有效的 SQLite 数据库"
                }
            }
        }
    }

    private fun deleteDatabaseSidecarFiles(databaseFile: File) {
        listOf(
            File("${databaseFile.absolutePath}-wal"),
            File("${databaseFile.absolutePath}-shm")
        ).forEach { file ->
            if (file.exists()) {
                file.delete()
            }
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

    suspend fun deleteMatchTask(
        taskId: Long
    ) {
        matchTaskDao.deleteCandidatesForTask(taskId)
        matchTaskDao.deleteTask(taskId)
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

    suspend fun getBookByUriString(
        uriString: String
    ): BookItem? {
        return bookDao.findByUri(uriString)?.toBookItem()
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
        currentUpdatedAt: Long,
        libraryRootUriString: String?
    ): MatchTaskEntity? {
        if (libraryRootUriString == null) return null

        return matchTaskDao.getNextTaskByStatusAfterCursor(
            libraryRootUriString = libraryRootUriString,
            status = MatchTaskStatus.NeedReview,
            currentTaskId = currentTaskId,
            currentUpdatedAt = currentUpdatedAt
        ) ?: matchTaskDao.getFirstTaskByStatus(
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
