package com.seagori.hgr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.seagori.hgr.data.local.dao.CurriculumProgressDao
import com.seagori.hgr.data.local.dao.ExecutionDao
import com.seagori.hgr.data.local.dao.GoalDao
import com.seagori.hgr.data.local.dao.LearningPathDao
import com.seagori.hgr.data.local.dao.LearningStepDao
import com.seagori.hgr.data.local.dao.MaterialChunkDao
import com.seagori.hgr.data.local.dao.MaterialDao
import com.seagori.hgr.data.local.dao.MemoryDao
import com.seagori.hgr.data.local.dao.SessionDao
import com.seagori.hgr.data.local.dao.SuggestionDao
import com.seagori.hgr.data.local.entity.CurriculumProgressEntity
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.local.entity.LearningPathEntity
import com.seagori.hgr.data.local.entity.LearningStepEntity
import com.seagori.hgr.data.local.entity.MaterialChunkEntity
import com.seagori.hgr.data.local.entity.MaterialEntity
import com.seagori.hgr.data.local.entity.MemoryEntity
import com.seagori.hgr.data.local.entity.SessionEntity
import com.seagori.hgr.data.local.entity.SuggestionEntity

@Database(
    entities = [
        GoalEntity::class,
        SessionEntity::class,
        SuggestionEntity::class,
        ExecutionEntity::class,
        MemoryEntity::class,
        MaterialEntity::class,
        MaterialChunkEntity::class,
        CurriculumProgressEntity::class,
        LearningPathEntity::class,
        LearningStepEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun sessionDao(): SessionDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun executionDao(): ExecutionDao
    abstract fun memoryDao(): MemoryDao
    abstract fun materialDao(): MaterialDao
    abstract fun materialChunkDao(): MaterialChunkDao
    abstract fun curriculumProgressDao(): CurriculumProgressDao
    abstract fun learningPathDao(): LearningPathDao
    abstract fun learningStepDao(): LearningStepDao

    companion object {
        /** v3 데이터를 보존하면서 학습 경로 테이블과 실행-단계 연결을 추가한다. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `executions` ADD COLUMN `stepId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `learning_paths` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`sourceMaterialId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_learning_paths_goalId` " +
                        "ON `learning_paths` (`goalId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `learning_steps` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pathId` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`instruction` TEXT NOT NULL, " +
                        "`criteria` TEXT NOT NULL, " +
                        "`minutes` INTEGER NOT NULL, " +
                        "`difficulty` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER, " +
                        "`sourceHeading` TEXT, " +
                        "FOREIGN KEY(`pathId`) REFERENCES `learning_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_learning_steps_pathId` " +
                        "ON `learning_steps` (`pathId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_learning_steps_pathId_sortOrder` " +
                        "ON `learning_steps` (`pathId`, `sortOrder`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `executions` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `executions` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE `executions` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
            }
        }

        fun create(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, "startcoach.db")
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            if (com.seagori.hgr.BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration()
            } else {
                // 출시 전 1·2 스키마는 마이그레이션 없이 교체한다. 3 이상은 단계적으로 올린다.
                builder.fallbackToDestructiveMigrationFrom(1, 2)
            }
            return builder.build()
        }
    }
}
