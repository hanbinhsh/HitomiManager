package com.ice.hitomimanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import kotlinx.coroutines.flow.Flow

private const val ACTIVE_TASK_BOOK_FILTER = """
AND EXISTS (
    SELECT 1
    FROM book AS active_book
    INNER JOIN library_source AS active_source ON active_source.id = active_book.sourceId
    WHERE active_book.uriString = match_task.bookUriString
      AND (
          active_source.lastCompletedScanAt IS NULL
          OR active_book.lastSeenAt >= active_source.lastCompletedScanAt
      )
)
"""

data class MatchTaskStatusCount(
    val status: String,
    val count: Int
)

@Dao
interface MatchTaskDao {

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
    ORDER BY updatedAt DESC, id DESC
    """
    )
    fun observeTasksForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<MatchTaskEntity>>

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
      AND status IN (:statuses)
    ORDER BY updatedAt DESC, id DESC
    """
    )
    fun observeTasksByStatusesForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        statuses: List<String>
    ): Flow<List<MatchTaskEntity>>

    @Query(
        """
    SELECT status, COUNT(*) AS count
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
    GROUP BY status
    """
    )
    fun observeStatusCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): Flow<List<MatchTaskStatusCount>>

    @Query(
        """
    SELECT status, COUNT(*) AS count
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
    GROUP BY status
    """
    )
    suspend fun getStatusCountsForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int
    ): List<MatchTaskStatusCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: MatchTaskEntity): Long

    @Update
    suspend fun updateTask(task: MatchTaskEntity)

    @Query("SELECT * FROM match_task WHERE id = :taskId LIMIT 1")
    suspend fun getTask(taskId: Long): MatchTaskEntity?

    @Query("DELETE FROM match_task WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Query("DELETE FROM match_candidate WHERE taskId = :taskId")
    suspend fun deleteCandidatesForTask(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<MatchCandidateEntity>)

    @Query("SELECT * FROM match_candidate WHERE taskId = :taskId ORDER BY id ASC")
    fun observeCandidatesForTask(taskId: Long): Flow<List<MatchCandidateEntity>>

    @Query("DELETE FROM match_task")
    suspend fun clearTasks()

    @Query("DELETE FROM match_candidate")
    suspend fun clearCandidates()

    @Query("SELECT * FROM match_task WHERE id = :taskId LIMIT 1")
    fun observeTask(taskId: Long): Flow<MatchTaskEntity?>

    @Query("UPDATE match_candidate SET selected = 0 WHERE taskId = :taskId")
    suspend fun clearSelectedCandidate(taskId: Long)

    @Query(
        """
    UPDATE match_candidate
    SET selected = 1
    WHERE taskId = :taskId AND galleryId = :galleryId
    """
    )
    suspend fun markSelectedCandidate(
        taskId: Long,
        galleryId: String
    )

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
      AND status IN (:statuses)
    ORDER BY updatedAt DESC, id DESC
    """
    )
    suspend fun getTasksByStatusesForSourceIds(
        sourceIds: List<String>,
        sourceCount: Int,
        statuses: List<String>
    ): List<MatchTaskEntity>

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
      AND status = :status
      AND (
          updatedAt < :currentUpdatedAt
          OR (updatedAt = :currentUpdatedAt AND id < :currentTaskId)
      )
    ORDER BY updatedAt DESC, id DESC
    LIMIT 1
    """
    )
    suspend fun getNextTaskByStatusAfterCursor(
        sourceIds: List<String>,
        sourceCount: Int,
        status: String,
        currentTaskId: Long,
        currentUpdatedAt: Long
    ): MatchTaskEntity?

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE (:sourceCount = 0 OR libraryRootUriString IN (:sourceIds))
    """ + ACTIVE_TASK_BOOK_FILTER + """
      AND status = :status
      AND id != :currentTaskId
    ORDER BY updatedAt DESC, id DESC
    LIMIT 1
    """
    )
    suspend fun getFirstTaskByStatus(
        sourceIds: List<String>,
        sourceCount: Int,
        status: String,
        currentTaskId: Long
    ): MatchTaskEntity?

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE bookUriString = :bookUriString
    ORDER BY updatedAt DESC, id DESC
    """
    )
    suspend fun getTasksByBookUri(
        bookUriString: String
    ): List<MatchTaskEntity>

    @Query(
        """
    UPDATE match_task
    SET 
        status = :newStatus,
        errorMessage = :errorMessage,
        updatedAt = :updatedAt
    WHERE status IN (:oldStatuses)
    """
    )
    suspend fun markTasksByStatuses(
        oldStatuses: List<String>,
        newStatus: String,
        errorMessage: String,
        updatedAt: Long
    )

    @Query(
        """
    UPDATE match_task
    SET 
        bookUriString = :newUriString,
        libraryRootUriString = :libraryRootUriString,
        displayName = :displayName,
        coverFilePath = COALESCE(:coverFilePath, coverFilePath),
        updatedAt = :updatedAt
    WHERE bookUriString = :oldUriString
    """
    )
    suspend fun migrateBookUri(
        oldUriString: String,
        newUriString: String,
        libraryRootUriString: String,
        displayName: String,
        coverFilePath: String?,
        updatedAt: Long
    )

    @Query(
        """
    UPDATE match_task
    SET coverFilePath = :coverFilePath,
        updatedAt = :updatedAt
    WHERE bookUriString = :bookUriString
    """
    )
    suspend fun updateCoverFilePathForBook(
        bookUriString: String,
        coverFilePath: String,
        updatedAt: Long
    )

    @Query("DELETE FROM match_candidate WHERE taskId IN (SELECT id FROM match_task WHERE bookUriString IN (:bookUriStrings))")
    suspend fun deleteCandidatesForBooks(bookUriStrings: List<String>)

    @Query("DELETE FROM match_task WHERE bookUriString IN (:bookUriStrings)")
    suspend fun deleteTasksForBooks(bookUriStrings: List<String>)
}
