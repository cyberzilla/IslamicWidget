package com.cyberzilla.islamicwidget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream

class QuoteDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "quotes.sqlite"
        private const val DB_VERSION = 1
    }

    init {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            copyDatabase(dbFile)
        }
    }

    private fun copyDatabase(dbFile: File) {
        context.assets.open("databases/$DB_NAME").use { input ->
            FileOutputStream(dbFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getRandomQuote(): Pair<String, String>? {
        val db = readableDatabase

        val settingsManager = SettingsManager(context)
        var lang = settingsManager.languageCode

        if (lang != "id" && lang != "en" && lang != "ar") {
            lang = "en"
        }

        val cursor = db.rawQuery(
            "SELECT quote, reference FROM quotes WHERE lang = ? ORDER BY RANDOM() LIMIT 1",
            arrayOf(lang)
        )
        var result: Pair<String, String>? = null

        if (cursor.moveToFirst()) {
            val quote = cursor.getString(0)
            val reference = cursor.getString(1)
            result = Pair(quote, reference)
        }
        cursor.close()
        // BUG FIX #5: Dihapus db.close() — SQLiteOpenHelper mengelola lifecycle
        // koneksi database secara internal. Memanggil db.close() secara manual
        // menyebabkan IllegalStateException jika fungsi ini dipanggil berulang
        // (misalnya saat ada beberapa widget yang di-update bersamaan).
        return result
    }
}
