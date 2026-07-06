package com.ice.hitomimanager.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ice.hitomimanager.data.local.AppDatabase
import com.ice.hitomimanager.data.local.entity.BookEntity
import com.ice.hitomimanager.data.local.entity.BookTagEntity
import com.ice.hitomimanager.data.local.entity.LibraryFolderEntity
import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.model.LibraryFolderNode
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.LibrarySourceType
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.toBookItem
import com.ice.hitomimanager.data.local.entity.toEntity
import com.ice.hitomimanager.data.local.entity.toLibrarySource
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

    fun observeSources(): Flow<List<LibrarySource>> {
        return bookDao.observeSources().map { list ->
            list.map { it.toLibrarySource() }
        }
    }

    suspend fun getSources(): List<LibrarySource> {
        return bookDao.getSources().map { it.toLibrarySource() }
    }

    suspend fun ensureLegacyLocalSource(rootUriString: String?) {
        if (rootUriString.isNullOrBlank()) return
        if (bookDao.getSourceByRoot(rootUriString) != null) return
        val now = System.currentTimeMillis()
        bookDao.upsertSource(
            LibrarySource(
                id = rootUriString,
                name = localDisplayName(Uri.parse(rootUriString)),
                type = LibrarySourceType.LocalSaf,
                rootUriString = rootUriString,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
    }

    suspend fun addOrUpdateLocalSource(uri: Uri): LibrarySource {
        val uriString = uri.toString()
        val now = System.currentTimeMillis()
        val old = bookDao.getSourceByRoot(uriString)?.toLibrarySource()
        val source = LibrarySource(
            id = old?.id ?: uriString,
            name = old?.name ?: localDisplayName(uri),
            type = LibrarySourceType.LocalSaf,
            rootUriString = uriString,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            lastCompletedScanAt = old?.lastCompletedScanAt
        )
        bookDao.upsertSource(source.toEntity())
        return source
    }

    suspend fun renameSource(sourceId: String, name: String): LibrarySource {
        val old = bookDao.getSource(sourceId)?.toLibrarySource() ?: error("目录来源不存在")
        val fixedName = name.trim()
        require(fixedName.isNotBlank()) { "目录名称不能为空" }
        val source = old.copy(
            name = fixedName,
            updatedAt = System.currentTimeMillis()
        )
        bookDao.upsertSource(source.toEntity())
        bookDao.updateFolderSourceName(sourceId, fixedName)
        return source
    }

    suspend fun deleteSource(sourceId: String) {
        db.withTransaction {
            bookDao.deleteFoldersForSource(sourceId)
            bookDao.deleteSource(sourceId)
        }
    }

    fun observeBooksForSourceIds(sourceIds: List<String>): Flow<List<BookItem>> {
        return bookDao.observeBooksForSourceIds(sourceIds, sourceIds.size)
            .map { list -> list.map { it.toBookItem() } }
    }

    fun observeChildFolders(sourceId: String, parentPath: String): Flow<List<LibraryFolderNode>> {
        return bookDao.observeChildFolders(sourceId, parentPath)
            .map { list -> list.map { it.toNode() } }
    }

    fun observeFoldersForSourceIds(sourceIds: List<String>): Flow<List<LibraryFolderNode>> {
        return bookDao.observeFoldersForSourceIds(sourceIds, sourceIds.size)
            .map { list -> list.map { it.toNode() } }
    }

    fun observeBooksInFolder(sourceId: String, parentPath: String): Flow<List<BookItem>> {
        return bookDao.observeBooksInFolder(sourceId, parentPath)
            .map { list -> list.map { it.toBookItem() } }
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

    fun observeMatchTasksByStatusesForSourceIds(
        sourceIds: List<String>,
        statuses: List<String>
    ): Flow<List<MatchTaskEntity>> {
        return if (statuses.isEmpty()) {
            matchTaskDao.observeTasksForSourceIds(sourceIds, sourceIds.size)
        } else {
            matchTaskDao.observeTasksByStatusesForSourceIds(
                sourceIds = sourceIds,
                sourceCount = sourceIds.size,
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

    fun observeMatchTaskFilterCountsForSourceIds(
        sourceIds: List<String>
    ): Flow<Map<MatchTaskFilter, Int>> {
        return combine(
            matchTaskDao.observeStatusCountsForSourceIds(sourceIds, sourceIds.size),
            bookDao.observeUnqueuedUnmatchedBookCountForSourceIds(sourceIds, sourceIds.size)
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

    suspend fun getMatchTaskFilterCountsForSourceIds(
        sourceIds: List<String>
    ): Map<MatchTaskFilter, Int> {
        return buildMatchTaskFilterCounts(
            statusCounts = matchTaskDao.getStatusCountsForSourceIds(sourceIds, sourceIds.size),
            unqueuedCount = bookDao.countUnqueuedUnmatchedBooksForSourceIds(sourceIds, sourceIds.size)
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

    suspend fun getUnmatchedBooksForSourceIds(
        sourceIds: List<String>
    ): List<BookItem> {
        return bookDao.getUnmatchedBooksForSourceIds(sourceIds, sourceIds.size)
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
        return combine(
            tagDao.observeTagCounts(libraryRootUriString),
            bookDao.observeLanguageFacetCounts(libraryRootUriString),
            bookDao.observeTypeFacetCounts(libraryRootUriString)
        ) { tagCounts, languageCounts, typeCounts ->
            val normalTags = tagCounts.filterNot {
                it.namespace == "language" || it.namespace == "type"
            }

            normalTags + languageCounts + typeCounts
        }
    }

    fun observeTagCountsForSourceIds(
        sourceIds: List<String>,
        mergeGenderTags: Boolean = false
    ): Flow<List<TagCountItem>> {
        if (!mergeGenderTags) {
            return combine(
                tagDao.observeTagCountsForSourceIds(sourceIds, sourceIds.size),
                bookDao.observeLanguageFacetCountsForSourceIds(sourceIds, sourceIds.size),
                bookDao.observeTypeFacetCountsForSourceIds(sourceIds, sourceIds.size)
            ) { tagCounts, languageCounts, typeCounts ->
                val normalTags = tagCounts.filterNot {
                    it.namespace == "language" || it.namespace == "type"
                }

                normalTags + languageCounts + typeCounts
            }
        }

        // 合并性别：用按名称去重的性别合并计数替换 male/female 逐键计数
        return combine(
            tagDao.observeTagCountsForSourceIds(sourceIds, sourceIds.size),
            bookDao.observeLanguageFacetCountsForSourceIds(sourceIds, sourceIds.size),
            bookDao.observeTypeFacetCountsForSourceIds(sourceIds, sourceIds.size),
            tagDao.observeGenderMergedTagCountsForSourceIds(sourceIds, sourceIds.size)
        ) { tagCounts, languageCounts, typeCounts, genderMerged ->
            val nonGenderTags = tagCounts.filterNot {
                it.namespace == "language" ||
                        it.namespace == "type" ||
                        it.namespace == "male" ||
                        it.namespace == "female"
            }

            nonGenderTags + genderMerged + languageCounts + typeCounts
        }
    }

    fun observeBooksByAllTags(
        libraryRootUriString: String,
        tagKeys: List<String>
    ): Flow<List<BookItem>> {
        if (tagKeys.isEmpty()) {
            return observeBooks(libraryRootUriString)
        }

        val facetKeys = tagKeys.filter { isFacetTagKey(it) }
        val normalTagKeys = tagKeys.filterNot { isFacetTagKey(it) }

        val baseFlow = if (normalTagKeys.isEmpty()) {
            observeBooks(libraryRootUriString)
        } else {
            bookDao.observeBooksByAllTags(
                libraryRootUriString = libraryRootUriString,
                tagKeys = normalTagKeys,
                tagCount = normalTagKeys.size
            ).map { list ->
                list.map { it.toBookItem() }
            }
        }

        return baseFlow.map { books ->
            books.filter { book ->
                facetKeys.all { key ->
                    bookMatchesFacetKey(book, key)
                }
            }
        }
    }

    fun observeBooksByAllTagsForSourceIds(
        sourceIds: List<String>,
        tagKeys: List<String>
    ): Flow<List<BookItem>> {
        if (tagKeys.isEmpty()) {
            return observeBooksForSourceIds(sourceIds)
        }

        val genderKeys = tagKeys.filter { isGenderMergedKey(it) }
        val facetKeys = tagKeys.filter { isFacetTagKey(it) }
        val normalTagKeys = tagKeys.filterNot {
            isFacetTagKey(it) || isGenderMergedKey(it)
        }

        val baseFlow = if (normalTagKeys.isEmpty()) {
            observeBooksForSourceIds(sourceIds)
        } else {
            bookDao.observeBooksByAllTagsForSourceIds(
                sourceIds = sourceIds,
                sourceCount = sourceIds.size,
                tagKeys = normalTagKeys,
                tagCount = normalTagKeys.size
            ).map { list ->
                list.map { it.toBookItem() }
            }
        }

        if (genderKeys.isEmpty()) {
            return baseFlow.map { books ->
                books.filter { book ->
                    facetKeys.all { key ->
                        bookMatchesFacetKey(book, key)
                    }
                }
            }
        }

        // 合并性别筛选：每个合并键匹配 male/female 任一（组内 OR），多个键之间 AND
        val genderMemberFlows = genderKeys.map { key ->
            val name = key.removePrefix("gender:")
            val members = listOf(
                makeTagKey("male", name),
                makeTagKey("female", name)
            )
            bookDao.observeBookUrisByAnyTagForSourceIds(
                sourceIds = sourceIds,
                sourceCount = sourceIds.size,
                tagKeys = members
            ).map { it.toSet() }
        }

        val genderSetsFlow = combine(genderMemberFlows) { it.toList() }

        return combine(baseFlow, genderSetsFlow) { books, genderSets ->
            books.filter { book ->
                facetKeys.all { key -> bookMatchesFacetKey(book, key) } &&
                        genderSets.all { uris -> book.uriString in uris }
            }
        }
    }

    private fun isFacetTagKey(key: String): Boolean {
        return key.startsWith("language:") || key.startsWith("type:")
    }

    private fun isGenderMergedKey(key: String): Boolean {
        return key.startsWith("gender:")
    }

    private fun bookMatchesFacetKey(
        book: BookItem,
        key: String
    ): Boolean {
        val normalizedKey = key.lowercase()

        return when {
            normalizedKey.startsWith("language:") -> {
                makeTagKey(
                    namespace = "language",
                    name = book.language.orEmpty()
                ) == normalizedKey
            }

            normalizedKey.startsWith("type:") -> {
                makeTagKey(
                    namespace = "type",
                    name = book.type.orEmpty()
                ) == normalizedKey
            }

            else -> true
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

    fun observeBooksBySearchForSourceIds(
        sourceIds: List<String>,
        query: String
    ): Flow<List<BookItem>> {
        val cleaned = query.trim()

        if (cleaned.isBlank()) {
            return observeBooksForSourceIds(sourceIds)
        }

        return bookDao.observeBooksBySearchForSourceIds(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
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
        val source = addOrUpdateLocalSource(treeUri)
        scanSource(source.id, onProgress)
    }

    suspend fun scanSource(
        sourceId: String,
        onProgress: (ScanProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val source = bookDao.getSource(sourceId)?.toLibrarySource() ?: error("目录来源不存在")
        val scanStartedAt = System.currentTimeMillis()
        val rootUriString = source.rootUriString
        val scannedLibrary = scanner.scan(
            treeUri = Uri.parse(rootUriString),
            onProgress = onProgress
        )
        val now = System.currentTimeMillis()

        // 分块批量事务：每块共用一个事务，显著减少逐本提交开销
        scannedLibrary.books.chunked(SCAN_TX_CHUNK).forEach { chunk ->
            db.withTransaction {
                chunk.forEach { scanned ->
                    val oldBySameUri = bookDao.findByUri(scanned.uriString)

                    if (oldBySameUri != null) {
                    val coverFilePath = scanned.coverFilePath ?: oldBySameUri.coverFilePath

                    val entity = oldBySameUri.copy(
                        libraryRootUriString = rootUriString,
                        sourceId = source.id,
                        relativePath = scanned.relativePath,
                        parentPath = scanned.parentPath,
                        displayName = scanned.displayName,
                        fileSize = scanned.fileSize,
                        lastModified = scanned.lastModified,
                        coverFilePath = coverFilePath,
                        lastSeenAt = scanStartedAt,
                        updatedAt = now
                    )

                    bookDao.upsert(entity)
                    return@forEach
                }

                val movedOld = bookDao.findReusableMovedBook(
                    sourceId = source.id,
                    displayName = scanned.displayName,
                    fileSize = scanned.fileSize,
                    uriString = scanned.uriString
                )

                if (movedOld != null) {
                    bookDao.migrateBookUri(
                        oldUriString = movedOld.uriString,
                        newUriString = scanned.uriString,
                        libraryRootUriString = rootUriString,
                        sourceId = source.id,
                        relativePath = scanned.relativePath,
                        parentPath = scanned.parentPath,
                        displayName = scanned.displayName,
                        fileSize = scanned.fileSize,
                        lastModified = scanned.lastModified,
                        coverFilePath = scanned.coverFilePath,
                        lastSeenAt = scanStartedAt,
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

                    return@forEach
                }

                val newEntity = BookEntity(
                    displayName = scanned.displayName,
                    uriString = scanned.uriString,
                    libraryRootUriString = rootUriString,
                    sourceId = source.id,
                    relativePath = scanned.relativePath,
                    parentPath = scanned.parentPath,
                    fileSize = scanned.fileSize,
                    lastModified = scanned.lastModified,
                    coverFilePath = scanned.coverFilePath,
                    createdAt = now,
                    updatedAt = now,
                    lastSeenAt = scanStartedAt
                )

                    bookDao.upsert(newEntity)
                }
            }
        }

        db.withTransaction {
            bookDao.deleteFoldersForSource(source.id)
            val folders = scannedLibrary.folders.map { folder ->
                LibraryFolderEntity(
                    sourceId = source.id,
                    sourceName = source.name,
                    path = folder.path,
                    parentPath = folder.parentPath,
                    name = folder.name,
                    updatedAt = now
                )
            }
            if (folders.isNotEmpty()) {
                bookDao.upsertFolders(folders)
            }
            bookDao.upsertSource(
                source.copy(
                    updatedAt = now,
                    lastCompletedScanAt = scanStartedAt
                ).toEntity()
            )
        }

        // 不要 deleteMissing。扫描默认只增量更新，缺失记录由用户主动清理。
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

    fun observeUnqueuedUnmatchedBooksForSourceIds(
        sourceIds: List<String>
    ): Flow<List<BookItem>> {
        return bookDao.observeUnqueuedUnmatchedBooksForSourceIds(sourceIds, sourceIds.size)
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
            meta.language?.takeIf { it.isNotBlank() }?.let { add("language" to it) }
            meta.type?.takeIf { it.isNotBlank() }?.let { add("type" to it) }
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

    suspend fun getMatchTasksByStatusesForSourceIds(
        sourceIds: List<String>,
        statuses: List<String>
    ): List<MatchTaskEntity> {
        return matchTaskDao.getTasksByStatusesForSourceIds(
            sourceIds = sourceIds,
            sourceCount = sourceIds.size,
            statuses = statuses
        )
    }

    suspend fun cleanupMissingFromConfiguredSources(
        sourceIds: List<String>
    ): Int = withContext(Dispatchers.IO) {
        val configuredSources = bookDao.getSources().map { it.toLibrarySource() }
        val configuredIds = configuredSources.map { it.id }
        val targetSources = if (sourceIds.isEmpty()) {
            configuredSources
        } else {
            configuredSources.filter { it.id in sourceIds }
        }

        var deletedCount = 0

        for (source in targetSources) {
            val missingUris = bookDao.getBookUrisMissingFromLastScan(
                sourceId = source.id,
                lastCompletedScanAt = source.lastCompletedScanAt
            )
            deletedCount += deleteBookRecords(missingUris)
        }

        if (sourceIds.isEmpty() && configuredIds.isNotEmpty()) {
            val orphanUris = bookDao.getBookUrisOutsideSourceIds(
                sourceIds = configuredIds,
                sourceCount = configuredIds.size
            )
            deletedCount += deleteBookRecords(orphanUris)
        }

        deletedCount
    }

    private suspend fun deleteBookRecords(
        uriStrings: List<String>
    ): Int {
        if (uriStrings.isEmpty()) return 0

        var deleted = 0
        uriStrings.distinct().chunked(300).forEach { chunk ->
            db.withTransaction {
                matchTaskDao.deleteCandidatesForBooks(chunk)
                matchTaskDao.deleteTasksForBooks(chunk)
                tagDao.deleteTagsForBooks(chunk)
                bookDao.deleteByUris(chunk)
            }
            deleted += chunk.size
        }

        return deleted
    }

    private fun LibraryFolderEntity.toNode(): LibraryFolderNode {
        return LibraryFolderNode(
            sourceId = sourceId,
            sourceName = sourceName,
            path = path,
            parentPath = parentPath,
            name = name
        )
    }

    private fun localDisplayName(uri: Uri): String {
        return DocumentFile.fromTreeUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?: "本地目录"
    }

    private fun makeTagKey(
        namespace: String,
        name: String
    ): String {
        return "${namespace.lowercase()}:${name.lowercase()}"
    }

    private companion object {
        // 扫描入库时每个事务处理的书本数量
        private const val SCAN_TX_CHUNK = 200
    }
}
