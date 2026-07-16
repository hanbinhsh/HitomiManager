package com.ice.hitomimanager

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ice.hitomimanager.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationPreservesExistingBookMetadata() {
        helper.createDatabase(DB_NAME, 7).apply {
            execSQL(
                """
                INSERT INTO library_source(
                    id, name, type, rootUriString, createdAt, updatedAt, lastCompletedScanAt
                ) VALUES('content://library', '旧目录', 'LocalSaf', 'content://library', 1, 2, 2)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO book(
                    displayName, uriString, fileSize, lastModified, coverFilePath,
                    source, sourceGalleryId, title, japaneseTitle, language, type,
                    pageCount, matchStatus, createdAt, updatedAt, libraryRootUriString,
                    sourceId, relativePath, parentPath, lastSeenAt
                ) VALUES(
                    'old.cbz', 'content://library/old.cbz', 1234, 99, '/covers/old.webp',
                    'hitomi', '123', 'Old title', NULL, 'japanese', 'doujinshi',
                    24, 'manual_matched', 1, 2, 'content://library',
                    'content://library', 'old.cbz', NULL, 2
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            8,
            true,
            AppDatabase.MIGRATION_7_8
        ).use { database ->
            database.query(
                "SELECT coverFilePath, sourceGalleryId, title, pageCount, localPageCount, remoteEtag FROM book"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("/covers/old.webp", cursor.getString(0))
                assertEquals("123", cursor.getString(1))
                assertEquals("Old title", cursor.getString(2))
                assertEquals(24, cursor.getInt(3))
                assertNull(cursor.getString(4))
                assertNull(cursor.getString(5))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-7-8"
    }
}
