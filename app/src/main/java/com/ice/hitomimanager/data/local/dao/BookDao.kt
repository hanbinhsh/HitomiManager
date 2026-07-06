package com.ice.hitomimanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ice.hitomimanager.data.local.entity.BookEntity
import com.ice.hitomimanager.data.local.entity.LibraryFolderEntity
import com.ice.hitomimanager.data.local.entity.LibrarySourceEntity
import com.ice.hitomimanager.data.model.TagCountItem
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM library_source WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: String)

    @Query("UPDATE library_folder SET sourceName = :name WHERE sourceId = :sourceId")
    suspend fun updateFolderSourceName(sourceId: String, name: String)

    @Query("SELECT * FROM library_folder WHERE sourceId = :sourceId AND COALESCE(parentPath, '') = :parentPath ORDER BY name COLLATE NOCASE ASC")
    fun observeChildFolders(sourceId: String, parentPath: String): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folder WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds)) ORDER BY sourceName COLLATE NOCASE ASC, path COLLATE NOCASE ASC")
    fun observeFoldersForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<LibraryFolderEntity>>

    @Query("DELETE FROM library_folder WHERE sourceId = :sourceId")
    suspend fun deleteFoldersForSource(sourceId: String)

    @Upsert
    suspend fun upsertFolders(folders: List<LibraryFolderEntity>)

    @Query("DELETE FROM library_folder")
    suspend fun clearFolders()

    @Query(
        """
    SELECT *
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooks(
        libraryRootUriString: String
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT *
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT
        'language:' || lower(language) AS tagKey,
        'language' AS namespace,
        language AS name,
        NULL AS translatedName,
        COUNT(*) AS bookCount
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND language IS NOT NULL
      AND language != ''
    GROUP BY lower(language), language
    """
    )
    fun observeLanguageFacetCounts(
        libraryRootUriString: String
    ): Flow<List<TagCountItem>>

    @Query(
        """
    SELECT
        'language:' || lower(language) AS tagKey,
        'language' AS namespace,
        language AS name,
        NULL AS translatedName,
        COUNT(*) AS bookCount
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND language IS NOT NULL
      AND language != ''
    GROUP BY lower(language), language
    """
    )
    fun observeLanguageFacetCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<TagCountItem>>

    @Query(
        """
    SELECT
        'type:' || lower(type) AS tagKey,
        'type' AS namespace,
        type AS name,
        NULL AS translatedName,
        COUNT(*) AS bookCount
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND type IS NOT NULL
      AND type != ''
    GROUP BY lower(type), type
    """
    )
    fun observeTypeFacetCounts(
        libraryRootUriString: String
    ): Flow<List<TagCountItem>>

    @Query(
        """
    SELECT
        'type:' || lower(type) AS tagKey,
        'type' AS namespace,
        type AS name,
        NULL AS translatedName,
        COUNT(*) AS bookCount
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND type IS NOT NULL
      AND type != ''
    GROUP BY lower(type), type
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

    @Query("DELETE FROM book")
    suspend fun clearAll()

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
    SELECT book.*
    FROM book
    INNER JOIN book_tag ON book.uriString = book_tag.bookUriString
    WHERE book_tag.tagKey = :tagKey
    ORDER BY book.displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksByTag(tagKey: String): Flow<List<BookEntity>>

    @Query(
        """
    SELECT book.*
    FROM book
    INNER JOIN book_tag ON book.uriString = book_tag.bookUriString
    WHERE book.libraryRootUriString = :libraryRootUriString
      AND book_tag.tagKey IN (:tagKeys)
    GROUP BY book.uriString
    HAVING COUNT(DISTINCT book_tag.tagKey) = :tagCount
    ORDER BY book.displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksByAllTags(
        libraryRootUriString: String,
        tagKeys: List<String>,
        tagCount: Int
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT book.*
    FROM book
    INNER JOIN book_tag ON book.uriString = book_tag.bookUriString
    WHERE (:sourceCount = 0 OR book.sourceId IN (:sourceIds))
      AND book_tag.tagKey IN (:tagKeys)
    GROUP BY book.uriString
    HAVING COUNT(DISTINCT book_tag.tagKey) = :tagCount
    ORDER BY book.displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksByAllTagsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        tagKeys: List<String>,
        tagCount: Int
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT DISTINCT book_tag.bookUriString
    FROM book_tag
    INNER JOIN book ON book.uriString = book_tag.bookUriString
    WHERE (:sourceCount = 0 OR book.sourceId IN (:sourceIds))
      AND book_tag.tagKey IN (:tagKeys)
    """
    )
    fun observeBookUrisByAnyTagForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        tagKeys: List<String>
    ): Flow<List<String>>

    @Query(
        """
    SELECT *
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND (
        displayName LIKE '%' || :query || '%' OR
        title LIKE '%' || :query || '%' OR
        japaneseTitle LIKE '%' || :query || '%' OR
        sourceGalleryId LIKE '%' || :query || '%'
      )
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksBySearch(
        libraryRootUriString: String,
        query: String
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT *
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND (
        displayName LIKE '%' || :query || '%' OR
        title LIKE '%' || :query || '%' OR
        japaneseTitle LIKE '%' || :query || '%' OR
        sourceGalleryId LIKE '%' || :query || '%'
      )
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksBySearchForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        query: String
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT *
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    suspend fun getUnmatchedBooks(
        libraryRootUriString: String
    ): List<BookEntity>

    @Query(
        """
    SELECT *
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    suspend fun getUnmatchedBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): List<BookEntity>

    @Query(
        """
    SELECT *
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeUnqueuedUnmatchedBooks(
        libraryRootUriString: String
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT *
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeUnqueuedUnmatchedBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<BookEntity>>

    @Query(
        """
    SELECT COUNT(*)
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    """
    )
    fun observeUnqueuedUnmatchedBookCount(
        libraryRootUriString: String
    ): Flow<Int>

    @Query(
        """
    SELECT COUNT(*)
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
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
    FROM book
    WHERE libraryRootUriString = :libraryRootUriString
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    """
    )
    suspend fun countUnqueuedUnmatchedBooks(
        libraryRootUriString: String
    ): Int

    @Query(
        """
    SELECT COUNT(*)
    FROM book
    WHERE (:sourceCount = 0 OR sourceId IN (:sourceIds))
      AND (sourceGalleryId IS NULL OR sourceGalleryId = '')
      AND NOT EXISTS (
          SELECT 1
          FROM match_task
          WHERE match_task.bookUriString = book.uriString
      )
    """
    )
    suspend fun countUnqueuedUnmatchedBooksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Int

    @Query(
        """
    SELECT *
    FROM book
    WHERE sourceId = :sourceId
      AND COALESCE(parentPath, '') = :parentPath
    ORDER BY displayName COLLATE NOCASE ASC
    """
    )
    fun observeBooksInFolder(
        sourceId: String,
        parentPath: String
    ): Flow<List<BookEntity>>

    @Query("SELECT * FROM book WHERE sourceId = :sourceId")
    suspend fun findAllBySource(sourceId: String): List<BookEntity>

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

    @Query("DELETE FROM book WHERE uriString IN (:uriStrings)")
    suspend fun deleteByUris(uriStrings: List<String>)

    @Query(
        """
    SELECT *
    FROM book
    WHERE sourceId = :sourceId
      AND displayName = :displayName
      AND fileSize = :fileSize
      AND uriString != :uriString
    ORDER BY 
      CASE 
        WHEN sourceGalleryId IS NULL OR sourceGalleryId = '' THEN 1 
        ELSE 0 
      END,
      updatedAt DESC
    LIMIT 1
    """
    )
    suspend fun findReusableMovedBook(
        sourceId: String,
        displayName: String,
        fileSize: Long,
        uriString: String
    ): BookEntity?

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
