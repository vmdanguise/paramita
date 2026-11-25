package ar.param.advisor.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ar.param.advisor.data.local.dao.MeritAndCalmDao
import ar.param.advisor.data.local.dao.MindfulAppTargetDao
import ar.param.advisor.data.local.dao.PracticeDao
import ar.param.advisor.data.local.dao.PracticeSessionDao
import ar.param.advisor.data.local.entity.MantraConfigEntity
import ar.param.advisor.data.local.entity.MeritAndCalmEventEntity
import ar.param.advisor.data.local.entity.MindfulAppTargetEntity
import ar.param.advisor.data.local.entity.PracticeEntity
import ar.param.advisor.data.local.entity.PracticeJewelFocusEntity
import ar.param.advisor.data.local.entity.PracticeSegmentEntity
import ar.param.advisor.data.local.entity.PracticeSessionEntity
import ar.param.advisor.data.local.entity.PracticeTagEntity

@Database(
    entities = [
        PracticeEntity::class,
        PracticeSegmentEntity::class,
        MantraConfigEntity::class,
        PracticeTagEntity::class,
        PracticeJewelFocusEntity::class,
        PracticeSessionEntity::class,
        MeritAndCalmEventEntity::class,
        MindfulAppTargetEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2 suma `practices.image_uri` (nullable): migración automática, sin perder el historial.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ManiDatabase : RoomDatabase() {
    abstract fun practiceDao(): PracticeDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun meritAndCalmDao(): MeritAndCalmDao
    abstract fun mindfulAppTargetDao(): MindfulAppTargetDao

    companion object {
        const val DATABASE_NAME = "mani.db"

        /** La migración destructiva solo se permite en builds debuggables (CON-004, ADR-0003). */
        fun build(context: Context, isDebuggable: Boolean): ManiDatabase {
            val builder = Room.databaseBuilder(context, ManiDatabase::class.java, DATABASE_NAME)
            if (isDebuggable) builder.fallbackToDestructiveMigration(dropAllTables = true)
            return builder.build()
        }
    }
}
