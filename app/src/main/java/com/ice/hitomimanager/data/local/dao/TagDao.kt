package com.ice.hitomimanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ice.hitomimanager.data.local.entity.BookTagEntity
import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.TagCountItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Upsert
    suspend fun upsertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTags(bookTags: List<BookTagEntity>)

    @Query("DELETE FROM book_tag WHERE bookUriString = :bookUriString")
    suspend fun deleteTagsForBook(bookUriString: String)

    @Query("DELETE FROM book_tag WHERE bookUriString IN (:bookUriStrings)")
    suspend fun deleteTagsForBooks(bookUriStrings: List<String>)

    @Query(
        """
        SELECT tag.*
        FROM tag
        INNER JOIN book_tag ON tag.key = book_tag.tagKey
        WHERE book_tag.bookUriString = :bookUriString
        ORDER BY tag.namespace COLLATE NOCASE ASC, tag.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForBook(bookUriString: String): Flow<List<TagEntity>>

    @Query(
        """
    SELECT 
        tag.`key` AS tagKey,
        tag.namespace AS namespace,
        tag.name AS name,
        tag.translatedName AS translatedName,
        COUNT(book_tag.bookUriString) AS bookCount
    FROM tag
    INNER JOIN book_tag ON tag.`key` = book_tag.tagKey
    INNER JOIN book ON book.uriString = book_tag.bookUriString
    WHERE book.libraryRootUriString = :libraryRootUriString
    GROUP BY tag.`key`, tag.namespace, tag.name, tag.translatedName
    """
    )
    fun observeTagCounts(
        libraryRootUriString: String
    ): Flow<List<TagCountItem>>

    @Query(
        """
    SELECT
        tag.`key` AS tagKey,
        tag.namespace AS namespace,
        tag.name AS name,
        tag.translatedName AS translatedName,
        COUNT(book_tag.bookUriString) AS bookCount
    FROM tag
    INNER JOIN book_tag ON tag.`key` = book_tag.tagKey
    INNER JOIN book ON book.uriString = book_tag.bookUriString
    WHERE (:sourceCount = 0 OR book.sourceId IN (:sourceIds))
    GROUP BY tag.`key`, tag.namespace, tag.name, tag.translatedName
    """
    )
    fun observeTagCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<TagCountItem>>

    @Query(
        """
    SELECT
        'gender:' || LOWER(tag.name) AS tagKey,
        'tag' AS namespace,
        MIN(tag.name) AS name,
        MAX(tag.translatedName) AS translatedName,
        COUNT(DISTINCT book_tag.bookUriString) AS bookCount
    FROM tag
    INNER JOIN book_tag ON tag.`key` = book_tag.tagKey
    INNER JOIN book ON book.uriString = book_tag.bookUriString
    WHERE (:sourceCount = 0 OR book.sourceId IN (:sourceIds))
      AND tag.namespace IN ('male', 'female')
    GROUP BY LOWER(tag.name)
    """
    )
    fun observeGenderMergedTagCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<TagCountItem>>

    @Query(
        """
    UPDATE book_tag
    SET bookUriString = :newUriString
    WHERE bookUriString = :oldUriString
    """
    )
    suspend fun migrateBookUri(
        oldUriString: String,
        newUriString: String
    )
}
