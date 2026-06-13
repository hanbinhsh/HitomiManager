package com.ice.hitomimanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ice.hitomimanager.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

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
    LIMIT 1
    """
    )
    suspend fun findReusableMovedBook(
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
        displayName = :displayName,
        fileSize = :fileSize,
        lastModified = :lastModified,
        coverFilePath = COALESCE(:coverFilePath, coverFilePath),
        updatedAt = :updatedAt
    WHERE uriString = :oldUriString
    """
    )
    suspend fun migrateBookUri(
        oldUriString: String,
        newUriString: String,
        libraryRootUriString: String,
        displayName: String,
        fileSize: Long,
        lastModified: Long,
        coverFilePath: String?,
        updatedAt: Long
    )
}
