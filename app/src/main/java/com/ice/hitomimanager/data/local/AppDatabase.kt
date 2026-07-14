package com.ice.hitomimanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ice.hitomimanager.data.local.dao.BookDao
import com.ice.hitomimanager.data.local.dao.MatchTaskDao
import com.ice.hitomimanager.data.local.dao.TagDao
import com.ice.hitomimanager.data.local.entity.LibraryFolderEntity
import com.ice.hitomimanager.data.local.entity.LibrarySourceEntity
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
        MatchCandidateEntity::class,
        LibrarySourceEntity::class,
        LibraryFolderEntity::class
    ],
    version = 7,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE book ADD COLUMN relativePath TEXT")
                db.execSQL("ALTER TABLE book ADD COLUMN parentPath TEXT")
                db.execSQL("ALTER TABLE book ADD COLUMN lastSeenAt INTEGER")
                db.execSQL("UPDATE book SET sourceId = COALESCE(libraryRootUriString, '') WHERE sourceId = ''")
                db.execSQL("UPDATE book SET lastSeenAt = updatedAt WHERE lastSeenAt IS NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_sourceId ON book(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_sourceId_parentPath ON book(sourceId, parentPath)")

                db.execSQL("CREATE TABLE IF NOT EXISTS library_source (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, rootUriString TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastCompletedScanAt INTEGER, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_source_type ON library_source(type)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_library_source_rootUriString ON library_source(rootUriString)")
                db.execSQL("INSERT OR IGNORE INTO library_source(id, name, type, rootUriString, createdAt, updatedAt, lastCompletedScanAt) SELECT libraryRootUriString, '本地目录', 'LocalSaf', libraryRootUriString, MIN(createdAt), MAX(updatedAt), NULL FROM book WHERE libraryRootUriString IS NOT NULL AND libraryRootUriString != '' GROUP BY libraryRootUriString")

                db.execSQL("CREATE TABLE IF NOT EXISTS library_folder (sourceId TEXT NOT NULL, sourceName TEXT NOT NULL, path TEXT NOT NULL, parentPath TEXT, name TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(sourceId, path))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_folder_sourceId ON library_folder(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_folder_sourceId_parentPath ON library_folder(sourceId, parentPath)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_task_libraryRootUriString ON match_task(libraryRootUriString)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_task_libraryRootUriString_status_updatedAt ON match_task(libraryRootUriString, status, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_tag_tagKey_bookUriString ON book_tag(tagKey, bookUriString)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_displayName_fileSize ON book(displayName, fileSize)")
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hitomi_manager.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
