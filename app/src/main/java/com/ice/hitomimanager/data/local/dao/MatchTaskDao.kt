package com.ice.hitomimanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import kotlinx.coroutines.flow.Flow

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
    WHERE libraryRootUriString = :libraryRootUriString
    ORDER BY updatedAt DESC, id DESC
    """
    )
    fun observeTasks(
        libraryRootUriString: String
    ): Flow<List<MatchTaskEntity>>

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE libraryRootUriString = :libraryRootUriString
      AND status IN (:statuses)
    ORDER BY updatedAt DESC, id DESC
    """
    )
    fun observeTasksByStatuses(
        libraryRootUriString: String,
        statuses: List<String>
    ): Flow<List<MatchTaskEntity>>

    @Query(
        """
    SELECT status, COUNT(*) AS count
    FROM match_task
    WHERE libraryRootUriString = :libraryRootUriString
    GROUP BY status
    """
    )
    fun observeStatusCounts(
        libraryRootUriString: String
    ): Flow<List<MatchTaskStatusCount>>

    @Query(
        """
    SELECT status, COUNT(*) AS count
    FROM match_task
    WHERE libraryRootUriString = :libraryRootUriString
    GROUP BY status
    """
    )
    suspend fun getStatusCounts(
        libraryRootUriString: String
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
    WHERE libraryRootUriString = :libraryRootUriString
      AND status IN (:statuses)
    ORDER BY updatedAt DESC, id DESC
    """
    )
    suspend fun getTasksByStatuses(
        libraryRootUriString: String,
        statuses: List<String>
    ): List<MatchTaskEntity>

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE libraryRootUriString = :libraryRootUriString
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
        libraryRootUriString: String,
        status: String,
        currentTaskId: Long,
        currentUpdatedAt: Long
    ): MatchTaskEntity?

    @Query(
        """
    SELECT *
    FROM match_task
    WHERE libraryRootUriString = :libraryRootUriString
      AND status = :status
      AND id != :currentTaskId
    ORDER BY updatedAt DESC, id DESC
    LIMIT 1
    """
    )
    suspend fun getFirstTaskByStatus(
        libraryRootUriString: String,
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
}
