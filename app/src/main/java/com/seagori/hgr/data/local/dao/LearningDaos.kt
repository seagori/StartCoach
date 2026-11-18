package com.seagori.hgr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.seagori.hgr.data.local.entity.LearningPathEntity
import com.seagori.hgr.data.local.entity.LearningStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningPathDao {
    @Query("SELECT * FROM learning_paths WHERE goalId = :goalId LIMIT 1")
    suspend fun getByGoal(goalId: Long): LearningPathEntity?

    @Query("SELECT * FROM learning_paths WHERE id = :id")
    suspend fun getById(id: Long): LearningPathEntity?

    @Query(
        """
        SELECT p.* FROM learning_paths p
        INNER JOIN goals g ON g.id = p.goalId
        WHERE g.status = 'ACTIVE'
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeForActiveGoals(): Flow<List<LearningPathEntity>>

    @Query(
        """
        SELECT p.* FROM learning_paths p
        INNER JOIN goals g ON g.id = p.goalId
        WHERE g.status != 'ARCHIVED'
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeForVisibleGoals(): Flow<List<LearningPathEntity>>

    @Query("SELECT * FROM learning_paths ORDER BY id ASC")
    suspend fun listAll(): List<LearningPathEntity>

    @Insert
    suspend fun insert(path: LearningPathEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<LearningPathEntity>)

    @Update
    suspend fun update(path: LearningPathEntity)

    @Delete
    suspend fun delete(path: LearningPathEntity)

    @Query("DELETE FROM learning_paths WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)
}

@Dao
interface LearningStepDao {
    @Query("SELECT * FROM learning_steps WHERE pathId = :pathId ORDER BY sortOrder ASC")
    suspend fun listForPath(pathId: Long): List<LearningStepEntity>

    @Query("SELECT * FROM learning_steps WHERE id = :id")
    suspend fun getById(id: Long): LearningStepEntity?

    @Query(
        """
        SELECT s.* FROM learning_steps s
        INNER JOIN learning_paths p ON p.id = s.pathId
        INNER JOIN goals g ON g.id = p.goalId
        WHERE g.status = 'ACTIVE'
        ORDER BY s.pathId ASC, s.sortOrder ASC
        """
    )
    fun observeForActiveGoals(): Flow<List<LearningStepEntity>>

    @Query(
        """
        SELECT s.* FROM learning_steps s
        INNER JOIN learning_paths p ON p.id = s.pathId
        INNER JOIN goals g ON g.id = p.goalId
        WHERE g.status != 'ARCHIVED'
        ORDER BY s.pathId ASC, s.sortOrder ASC
        """
    )
    fun observeForVisibleGoals(): Flow<List<LearningStepEntity>>

    @Query("SELECT * FROM learning_steps ORDER BY id ASC")
    suspend fun listAll(): List<LearningStepEntity>

    @Insert
    suspend fun insertAll(items: List<LearningStepEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<LearningStepEntity>)

    @Update
    suspend fun update(step: LearningStepEntity)

    @Query("DELETE FROM learning_steps WHERE pathId = :pathId")
    suspend fun deleteForPath(pathId: Long)

    @Query("DELETE FROM learning_steps WHERE id = :id")
    suspend fun deleteById(id: Long)
}
