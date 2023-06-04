package com.n0tavailable.tunedetective

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "search_history.db"
        private const val TABLE_SEARCH_HISTORY = "search_history"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_QUERY = "query"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_SEARCH_HISTORY ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_QUERY TEXT)"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SEARCH_HISTORY")
        onCreate(db)
    }

    fun addSearchQuery(query: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_QUERY, query)
        db.insert(TABLE_SEARCH_HISTORY, null, values)
        db.close()
    }

    fun getAllSearchQueries(): List<String> {
        val searchQueries = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SEARCH_HISTORY ORDER BY $COLUMN_ID DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val query = cursor.getString(cursor.getColumnIndex(COLUMN_QUERY))
                searchQueries.add(query)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return searchQueries
    }
}
