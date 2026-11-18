package com.seagori.hgr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.seagori.hgr.data.local.entity.CurriculumProgressEntity
import com.seagori.hgr.data.local.entity.MaterialChunkEntity
import com.seagori.hgr.data.local.entity.MaterialEntity

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE goalId = :goalId ORDER BY importedAt DESC")
    suspend fun listByGoal(goalId: Long): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getById(id: Long): MaterialEntity?

    @Insert
    suspend fun insert(item: MaterialEntity): Long

    @Update
    suspend fun update(item: MaterialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<MaterialEntity>)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM materials ORDER BY id ASC")
    suspend fun listAll(): List<MaterialEntity>
}

@Dao
interface MaterialChunkDao {
    @Query("SELECT * FROM material_chunks WHERE materialId = :materialId ORDER BY sortOrder ASC")
    suspend fun listForMaterial(materialId: Long): List<MaterialChunkEntity>

    @Query("SELECT * FROM material_chunks WHERE id = :id")
    suspend fun getById(id: Long): MaterialChunkEntity?

    @Query("DELETE FROM material_chunks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun update(item: MaterialChunkEntity)

    @Insert
    suspend fun insertAll(items: List<MaterialChunkEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<MaterialChunkEntity>)

    @Query("SELECT * FROM material_chunks ORDER BY id ASC")
    suspend fun listAll(): List<MaterialChunkEntity>
}

@Dao
interface CurriculumProgressDao {
    @Query("SELECT * FROM curriculum_progress WHERE goalId = :goalId LIMIT 1")
    suspend fun getByGoal(goalId: Long): CurriculumProgressEntity?

    @Query("SELECT * FROM curriculum_progress WHERE id = :id")
    suspend fun getById(id: Long): CurriculumProgressEntity?

    @Query("DELETE FROM curriculum_progress WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)

    @Query("DELETE FROM curriculum_progress WHERE materialId = :materialId")
    suspend fun deleteByMaterial(materialId: Long)

    @Query("DELETE FROM curriculum_progress WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CurriculumProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<CurriculumProgressEntity>)

    @Query("SELECT * FROM curriculum_progress ORDER BY id ASC")
    suspend fun listAll(): List<CurriculumProgressEntity>
}
