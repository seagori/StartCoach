package com.seagori.hgr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.local.entity.MemoryEntity
import com.seagori.hgr.data.local.entity.SessionEntity
import com.seagori.hgr.data.local.entity.SuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE status != 'ARCHIVED' ORDER BY updatedAt DESC")
    fun observeVisible(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE status = 'ARCHIVED' ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE title = :title AND status != 'ARCHIVED' LIMIT 1")
    suspend fun getByTitle(title: String): GoalEntity?

    @Query("SELECT * FROM goals ORDER BY id ASC")
    suspend fun listAll(): List<GoalEntity>

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpen(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY id ASC")
    suspend fun listAll(): List<SessionEntity>
}

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun listForSession(sessionId: Long): List<SuggestionEntity>

    @Query("SELECT * FROM suggestions WHERE id = :id")
    suspend fun getById(id: Long): SuggestionEntity?

    @Query("DELETE FROM suggestions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<SuggestionEntity>)

    @Insert
    suspend fun insertAll(items: List<SuggestionEntity>): List<Long>

    @Update
    suspend fun update(item: SuggestionEntity)

    @Query("SELECT * FROM suggestions ORDER BY id ASC")
    suspend fun listAll(): List<SuggestionEntity>
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM executions WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeVisible(): Flow<List<ExecutionEntity>>

    @Query("SELECT * FROM executions WHERE archived = 1 ORDER BY createdAt DESC")
    fun observeArchived(): Flow<List<ExecutionEntity>>

    @Query(
        """
        SELECT e.goalId AS goalId, COUNT(*) AS count
        FROM executions e
        INNER JOIN goals g ON g.id = e.goalId
        WHERE e.attempted = 1 AND e.archived = 0 AND g.status = 'ACTIVE'
        GROUP BY e.goalId
        """
    )
    fun observeAttemptCounts(): Flow<List<GoalAttemptCount>>

    @Query("SELECT * FROM executions WHERE goalId = :goalId AND archived = 0 ORDER BY createdAt DESC")
    fun observeByGoal(goalId: Long): Flow<List<ExecutionEntity>>

    @Query("UPDATE executions SET archived = :archived, updatedAt = :updatedAt WHERE goalId = :goalId")
    suspend fun setArchivedForGoal(goalId: Long, archived: Boolean, updatedAt: Long)

    @Query("SELECT * FROM executions WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForSession(sessionId: Long): ExecutionEntity?

    @Query("SELECT * FROM executions WHERE id = :id")
    suspend fun getById(id: Long): ExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExecutionEntity>)

    @Insert
    suspend fun insert(item: ExecutionEntity): Long

    @Update
    suspend fun update(item: ExecutionEntity)

    @Delete
    suspend fun delete(item: ExecutionEntity)

    @Query("SELECT * FROM executions ORDER BY id ASC")
    suspend fun listAll(): List<ExecutionEntity>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE goalId = :goalId LIMIT 1")
    suspend fun getByGoal(goalId: Long): MemoryEntity?

    @Query("DELETE FROM memories WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<MemoryEntity>)

    @Query("SELECT * FROM memories ORDER BY id ASC")
    suspend fun listAll(): List<MemoryEntity>
}
