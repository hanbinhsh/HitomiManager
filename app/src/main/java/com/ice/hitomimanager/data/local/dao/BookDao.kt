package com.ice.hitomimanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.paging.PagingSource
import com.ice.hitomimanager.data.local.entity.BookEntity
import com.ice.hitomimanager.data.local.entity.LibraryFolderEntity
import com.ice.hitomimanager.data.local.entity.LibrarySourceEntity
import com.ice.hitomimanager.data.model.TagCountItem
import kotlinx.coroutines.flow.Flow

private const val ACTIVE_BOOK_FILTER = """
AND EXISTS (
    SELECT 1 FROM library_source AS active_source
    WHERE active_source.id = b.sourceId
      AND (
          active_source.lastCompletedScanAt IS NULL
          OR b.lastSeenAt >= active_source.lastCompletedScanAt
      )
)
"""

@Dao
interface BookDao {

    @Query("SELECT * FROM library_source ORDER BY name COLLATE NOCASE ASC")
    fun observeSources(): Flow<List<LibrarySourceEntity>>

    @Query("SELECT * FROM library_source ORDER BY name COLLATE NOCASE ASC")
    suspend fun getSources(): List<LibrarySourceEntity>

    @Query("SELECT * FROM library_source WHERE id = :sourceId LIMIT 1")
    suspend fun getSource(sourceId: String): LibrarySourceEntity?

    @Query("SELECT * FROM library_source WHERE rootUriString = :rootUriString LIMIT 1")
    suspend fun getSourceByRoot(rootUriString: String): LibrarySourceEntity?

    @Upsert
    suspend fun upsertSource(source: LibrarySourceEntity)

    @Query("UPDATE library_source SET lastCompletedScanAt = :scanBoundary, updatedAt = :updatedAt")
    suspend fun markAllSourcesAwaitingRescan(scanBoundary: Long, updatedAt: Long)

    @Query("DELETE FROM library_source WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: String)

    @Query("UPDATE library_folder SET sourceName = :name WHERE sourceId = :sourceId")
    suspend fun updateFolderSourceName(sourceId: String, name: String)

    @Query(
        """
        SELECT folder.* FROM library_folder AS folder
        WHERE folder.sourceId = :sourceId
          AND COALESCE(folder.parentPath, '') = :parentPath
          AND EXISTS (
              SELECT 1 FROM library_source AS active_source
              WHERE active_source.id = folder.sourceId
                AND (
                    active_source.lastCompletedScanAt IS NULL
                    OR folder.updatedAt >= active_source.lastCompletedScanAt
                )
          )
          AND EXISTS (
              SELECT 1 FROM book AS active_book
              INNER JOIN library_source AS book_source ON book_source.id = active_book.sourceId
              WHERE active_book.sourceId = folder.sourceId
                AND (
                    active_book.parentPath = folder.path
                    OR active_book.relativePath LIKE folder.path || '/%'
                )
                AND (
                    book_source.lastCompletedScanAt IS NULL
                    OR active_book.lastSeenAt >= book_source.lastCompletedScanAt
                )
          )
        ORDER BY folder.name COLLATE NOCASE ASC
        """
    )
    fun observeChildFolders(sourceId: String, parentPath: String): Flow<List<LibraryFolderEntity>>

    @Query(
        """
        SELECT folder.* FROM library_folder AS folder
        WHERE (:sourceCount = 0 OR folder.sourceId IN (:sourceIds))
          AND EXISTS (
              SELECT 1 FROM library_source AS active_source
              WHERE active_source.id = folder.sourceId
                AND (
                    active_source.lastCompletedScanAt IS NULL
                    OR folder.updatedAt >= active_source.lastCompletedScanAt
                )
          )
          AND EXISTS (
              SELECT 1 FROM book AS active_book
              INNER JOIN library_source AS book_source ON book_source.id = active_book.sourceId
              WHERE active_book.sourceId = folder.sourceId
                AND (
                    active_book.parentPath = folder.path
                    OR active_book.relativePath LIKE folder.path || '/%'
                )
                AND (
                    book_source.lastCompletedScanAt IS NULL
                    OR active_book.lastSeenAt >= book_source.lastCompletedScanAt
                )
          )
        ORDER BY folder.sourceName COLLATE NOCASE ASC, folder.path COLLATE NOCASE ASC
        """
    )
    fun observeFoldersForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<LibraryFolderEntity>>

    @Query("DELETE FROM library_folder WHERE sourceId = :sourceId")
    suspend fun deleteFoldersForSource(sourceId: String)

    @Upsert
    suspend fun upsertFolders(folders: List<LibraryFolderEntity>)

    @Query("DELETE FROM library_folder")
    suspend fun clearFolders(): Int

    @Query(
        """
        DELETE FROM library_folder
        WHERE :sourceCount > 0
          AND sourceId NOT IN (:sourceIds)
        """
    )
    suspend fun deleteFoldersOutsideSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Int

    @Query(
        """
        SELECT b.* FROM book b
        WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
        """ + ACTIVE_BOOK_FILTER + """
          AND (
            :query = ''
            OR LOWER(b.displayName) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(b.title, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(b.japaneseTitle, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(b.sourceGalleryId, '')) LIKE '%' || LOWER(:query) || '%'
          )
          AND (
            :tagCount = 0
            OR b.uriString IN (
              SELECT effective_tags.bookUriString
              FROM (
                SELECT bt.bookUriString AS bookUriString, LOWER(bt.tagKey) AS tagKey
                FROM book_tag bt
                UNION ALL
                SELECT book.uriString, 'language:' || LOWER(book.language)
                FROM book
                WHERE book.language IS NOT NULL AND book.language != ''
                UNION ALL
                SELECT book.uriString, 'type:' || LOWER(book.type)
                FROM book
                WHERE book.type IS NOT NULL AND book.type != ''
                UNION ALL
                SELECT bt.bookUriString, 'gender:' || SUBSTR(LOWER(bt.tagKey), INSTR(bt.tagKey, ':') + 1)
                FROM book_tag bt
                WHERE LOWER(bt.tagKey) LIKE 'male:%' OR LOWER(bt.tagKey) LIKE 'female:%'
              ) AS effective_tags
              WHERE effective_tags.tagKey IN (:tagKeys)
              GROUP BY effective_tags.bookUriString
              HAVING COUNT(DISTINCT effective_tags.tagKey) = :tagCount
            )
          )
        ORDER BY
          CASE WHEN :sortMode = 'NameAsc' THEN b.displayName END COLLATE NOCASE ASC,
          CASE WHEN :sortMode = 'NameDesc' THEN b.displayName END COLLATE NOCASE DESC,
          CASE WHEN :sortMode = 'FileTimeDesc' THEN b.lastModified END DESC,
          CASE WHEN :sortMode = 'FileTimeAsc' THEN b.lastModified END ASC,
          CASE WHEN :sortMode IN ('PageCountAsc', 'PageCountDesc') AND b.pageCount IS NULL THEN 1 ELSE 0 END ASC,
          CASE WHEN :sortMode = 'PageCountDesc' THEN b.pageCount END DESC,
          CASE WHEN :sortMode = 'PageCountAsc' THEN b.pageCount END ASC,
          b.displayName COLLATE NOCASE ASC,
          b.uriString ASC
        """
    )
    fun pagingBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        tagKeys: List<String>,
        tagCount: Int,
        query: String,
        sortMode: String
    ): PagingSource<Int, BookEntity>

    @Query(
        """
        SELECT COUNT(*) FROM book b
        WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
        """ + ACTIVE_BOOK_FILTER + """
          AND (
            :query = ''
            OR LOWER(b.displayName) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(b.title, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(b.japaneseTitle, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(b.sourceGalleryId, '')) LIKE '%' || LOWER(:query) || '%'
          )
          AND (
            :tagCount = 0
            OR b.uriString IN (
              SELECT effective_tags.bookUriString
              FROM (
                SELECT bt.bookUriString AS bookUriString, LOWER(bt.tagKey) AS tagKey
                FROM book_tag bt
                UNION ALL
                SELECT book.uriString, 'language:' || LOWER(book.language)
                FROM book
                WHERE book.language IS NOT NULL AND book.language != ''
                UNION ALL
                SELECT book.uriString, 'type:' || LOWER(book.type)
                FROM book
                WHERE book.type IS NOT NULL AND book.type != ''
                UNION ALL
                SELECT bt.bookUriString, 'gender:' || SUBSTR(LOWER(bt.tagKey), INSTR(bt.tagKey, ':') + 1)
                FROM book_tag bt
                WHERE LOWER(bt.tagKey) LIKE 'male:%' OR LOWER(bt.tagKey) LIKE 'female:%'
              ) AS effective_tags
              WHERE effective_tags.tagKey IN (:tagKeys)
              GROUP BY effective_tags.bookUriString
              HAVING COUNT(DISTINCT effective_tags.tagKey) = :tagCount
            )
          )
        """
    )
    fun observeBookCountByFiltersForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        tagKeys: List<String>,
        tagCount: Int,
        query: String
    ): Flow<Int>

    @Query(
        """
        SELECT b.* FROM book AS b
        WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
        """ + ACTIVE_BOOK_FILTER + """
          AND COALESCE(b.parentPath, '') = ''
        ORDER BY b.displayName COLLATE NOCASE ASC
        """
    )
    fun observeRootBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT
        'language:' || lower(b.language) AS tagKey,
        'language' AS namespace,
        b.language AS name,
        NULL AS translatedName,
        COUNT(*) AS bookCount
    FROM book AS b
    WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
    """ + ACTIVE_BOOK_FILTER + """
      AND b.language IS NOT NULL
      AND b.language != ''
    GROUP BY lower(b.language), b.language
    """
    )
    fun observeLanguageFacetCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<TagCountItem>>

    @Query(
        """
    SELECT
        'type:' || lower(b.type) AS tagKey,
        'type' AS namespace,
        b.type AS name,
        NULL AS translatedName,
        COUNT(*) AS bookCount
    FROM book AS b
    WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
    """ + ACTIVE_BOOK_FILTER + """
      AND b.type IS NOT NULL
      AND b.type != ''
    GROUP BY lower(b.type), b.type
    """
    )
    fun observeTypeFacetCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<TagCountItem>>

    @Query("SELECT * FROM book WHERE uriString = :uriString LIMIT 1")
    suspend fun findByUri(uriString: String): BookEntity?

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query(
        """
    UPDATE book
    SET 
        source = 'hitomi',
        sourceGalleryId = :galleryId,
        title = :title,
        japaneseTitle = :japaneseTitle,
        language = :language,
        type = :type,
        pageCount = :pageCount,
        matchStatus = :matchStatus,
        updatedAt = :updatedAt
    WHERE uriString = :uriString
    """
    )
    suspend fun updateHitomiMeta(
        uriString: String,
        galleryId: String,
        title: String?,
        japaneseTitle: String?,
        language: String?,
        type: String?,
        pageCount: Int?,
        matchStatus: String,
        updatedAt: Long
    )

    @Query(
        """
    UPDATE book
    SET coverFilePath = :coverFilePath,
        updatedAt = :updatedAt
    WHERE uriString = :uriString
    """
    )
    suspend fun updateCoverFilePath(
        uriString: String,
        coverFilePath: String,
        updatedAt: Long
    )

    @Query(
        """
    SELECT b.*
    FROM book AS b
    WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
    """ + ACTIVE_BOOK_FILTER + """
      AND (b.sourceGalleryId IS NULL OR b.sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = b.uriString
      )
    ORDER BY b.displayName COLLATE NOCASE ASC
    """
    )
    suspend fun getUnmatchedBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): List<BookEntity>

    @Query(
        """
    SELECT b.*
    FROM book AS b
    WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
    """ + ACTIVE_BOOK_FILTER + """
      AND (b.sourceGalleryId IS NULL OR b.sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = b.uriString
      )
    ORDER BY b.displayName COLLATE NOCASE ASC
    """
    )
    fun observeUnqueuedUnmatchedBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT COUNT(*)
    FROM book AS b
    WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
    """ + ACTIVE_BOOK_FILTER + """
      AND (b.sourceGalleryId IS NULL OR b.sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = b.uriString
      )
    """
    )
    fun observeUnqueuedUnmatchedBookCountForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<Int>

    @Query(
        """
    SELECT COUNT(*)
    FROM book AS b
    WHERE (:sourceCount = 0 OR b.sourceId IN (:sourceIds))
    """ + ACTIVE_BOOK_FILTER + """
      AND (b.sourceGalleryId IS NULL OR b.sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = b.uriString
      )
    """
    )
    suspend fun countUnqueuedUnmatchedBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Int

    @Query(
        """
    SELECT b.*
    FROM book AS b
    WHERE b.sourceId = :sourceId
    """ + ACTIVE_BOOK_FILTER + """
      AND COALESCE(b.parentPath, '') = :parentPath
    ORDER BY b.displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksInFolder(
        sourceId: String,
        parentPath: String
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT uriString
    FROM book
    WHERE sourceId = :sourceId
      AND :lastCompletedScanAt IS NOT NULL
      AND (lastSeenAt IS NULL OR lastSeenAt < :lastCompletedScanAt)
    """
    )
    suspend fun getBookUrisMissingFromLastScan(
        sourceId: String,
        lastCompletedScanAt: Long?
    ): List<String>

    @Query(
        """
    SELECT uriString
    FROM book
    WHERE :sourceCount > 0
      AND sourceId NOT IN (:sourceIds)
    """
    )
    suspend fun getBookUrisOutsideSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): List<String>

    @Query("SELECT uriString FROM book")
    suspend fun getAllBookUris(): List<String>

    @Query(
        """
        SELECT b.uriString FROM book b
        INNER JOIN library_source s ON s.id = b.sourceId
        WHERE s.type = 'LocalSaf'
        """
    )
    suspend fun getLocalBookUris(): List<String>

    @Query("SELECT * FROM book WHERE sourceId = :sourceId")
    suspend fun getBooksForSource(sourceId: String): List<BookEntity>

    @Query(
        """
        UPDATE book
        SET coverFilePath = COALESCE(:coverFilePath, coverFilePath),
            localPageCount = COALESCE(:localPageCount, localPageCount),
            remoteEtag = COALESCE(:remoteEtag, remoteEtag),
            updatedAt = :updatedAt
        WHERE uriString = :uriString
        """
    )
    suspend fun updateArchiveIndex(
        uriString: String,
        coverFilePath: String?,
        localPageCount: Int?,
        remoteEtag: String?,
        updatedAt: Long
    )

    @Query("UPDATE book SET lastSeenAt = NULL WHERE uriString = :uriString")
    suspend fun markBookUnavailable(uriString: String)

    @Query("UPDATE book SET lastSeenAt = NULL WHERE uriString IN (:uriStrings)")
    suspend fun markBooksUnavailable(uriStrings: List<String>)

    @Query("DELETE FROM book WHERE uriString IN (:uriStrings)")
    suspend fun deleteByUris(uriStrings: List<String>)

    @Query(
        """
    SELECT *
    FROM book
    WHERE displayName = :displayName
      AND fileSize = :fileSize
      AND uriString != :uriString
    ORDER BY 
      CASE 
        WHEN sourceGalleryId IS NULL OR sourceGalleryId = '' THEN 1 
        ELSE 0 
      END,
      updatedAt DESC
    """
    )
    suspend fun findReusableMovedBooks(
        displayName: String,
        fileSize: Long,
        uriString: String
    ): List<BookEntity>

    @Query(
        """
    UPDATE book
    SET 
        uriString = :newUriString,
        libraryRootUriString = :libraryRootUriString,
        sourceId = :sourceId,
        relativePath = :relativePath,
        parentPath = :parentPath,
        displayName = :displayName,
        fileSize = :fileSize,
        lastModified = :lastModified,
        coverFilePath = COALESCE(:coverFilePath, coverFilePath),
        lastSeenAt = :lastSeenAt,
        updatedAt = :updatedAt
    WHERE uriString = :oldUriString
    """
    )
    suspend fun migrateBookUri(
        oldUriString: String,
        newUriString: String,
        libraryRootUriString: String,
        sourceId: String,
        relativePath: String?,
        parentPath: String?,
        displayName: String,
        fileSize: Long,
        lastModified: Long,
        coverFilePath: String?,
        lastSeenAt: Long,
        updatedAt: Long
    )
}
