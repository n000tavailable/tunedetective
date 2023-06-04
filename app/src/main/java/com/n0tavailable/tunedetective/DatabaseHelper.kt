package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
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

        // Check if the query already exists in the database
        val queryExists = isQueryExists(db, query)
        if (queryExists) {
            // Query already exists, do not insert
            db.close()
            return
        }

        val values = ContentValues()
        values.put(COLUMN_QUERY, query)
        db.insert(TABLE_SEARCH_HISTORY, null, values)
        db.close()
    }

    private fun isQueryExists(db: SQLiteDatabase, query: String): Boolean {
        val selectQuery = "SELECT $COLUMN_QUERY FROM $TABLE_SEARCH_HISTORY WHERE $COLUMN_QUERY = ?"
        val cursor: Cursor? = db.rawQuery(selectQuery, arrayOf(query))
        val queryExists = cursor != null && cursor.count > 0
        cursor?.close()
        return queryExists
    }

    @SuppressLint("Range")
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