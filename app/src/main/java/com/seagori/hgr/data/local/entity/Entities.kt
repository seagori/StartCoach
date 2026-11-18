package com.seagori.hgr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val domain: String,
    val status: String,
    val longTermTarget: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val phase: String,
    val difficultyLevel: Int,
    val selectedSuggestionId: Long?
)

@Entity(
    tableName = "suggestions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("goalId")]
)
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val goalId: Long,
    val text: String,
    val difficulty: Int,
    val kind: String,
    val selected: Boolean,
    val source: String,
    val createdAt: Long
)

@Entity(
    tableName = "executions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId"), Index("sessionId"), Index("createdAt")]
)
data class ExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val goalId: Long,
    val suggestionId: Long,
    val actionText: String,
    val outcome: String,
    val attempted: Boolean,
    val count: Int?,
    val durationSec: Int?,
    val feltDifficulty: Int?,
    val isEstimated: Boolean,
    val createdAt: Long,
    val stepId: Long? = null,
    val archived: Boolean = false,
    val updatedAt: Long = createdAt
)

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["goalId"], unique = true)]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val preference: String?,
    val difficultyNote: String?,
    val lastProgress: String?,
    val summary: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "materials",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val markdown: String,
    val markdownHash: String,
    val importedAt: Long
)

@Entity(
    tableName = "material_chunks",
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("materialId"), Index(value = ["materialId", "sortOrder"])]
)
data class MaterialChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val headingPath: String,
    val body: String,
    val sortOrder: Int
)

@Entity(
    tableName = "curriculum_progress",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["goalId"], unique = true), Index("materialId")]
)
data class CurriculumProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val materialId: Long,
    val chunkIndex: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "learning_paths",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["goalId"], unique = true)]
)
data class LearningPathEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val sourceMaterialId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "learning_steps",
    foreignKeys = [
        ForeignKey(
            entity = LearningPathEntity::class,
            parentColumns = ["id"],
            childColumns = ["pathId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pathId"), Index(value = ["pathId", "sortOrder"])]
)
data class LearningStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pathId: Long,
    val sortOrder: Int,
    val title: String,
    val instruction: String,
    val criteria: String,
    val minutes: Int,
    val difficulty: Int,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sourceHeading: String? = null
)
