package io.github.thatonecodingperson.thortools.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppOverrideEntity::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 6, to = 7),
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appOverrideDao(): AppOverrideDao

    companion object {
        private fun dropUnusedTable(from: Int) = object : Migration(from, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS hotkey")
            }
        }

        /** 0.12.0 drops a table nothing uses any more. */
        val DROP_UNUSED_TABLE = arrayOf(dropUnusedTable(4), dropUnusedTable(5))
    }
}
