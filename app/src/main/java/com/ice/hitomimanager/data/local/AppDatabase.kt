package com.ice.hitomimanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ice.hitomimanager.data.local.dao.BookDao
import com.ice.hitomimanager.data.local.dao.MatchTaskDao
import com.ice.hitomimanager.data.local.dao.TagDao
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.BookEntity
import com.ice.hitomimanager.data.local.entity.BookTagEntity
import com.ice.hitomimanager.data.local.entity.TagEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        TagEntity::class,
        BookTagEntity::class,
        MatchTaskEntity::class,
        MatchCandidateEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun matchTaskDao(): MatchTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book ADD COLUMN libraryRootUriString TEXT")
                db.execSQL("ALTER TABLE match_task ADD COLUMN libraryRootUriString TEXT")
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hitomi_manager.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
