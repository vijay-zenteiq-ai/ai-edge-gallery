/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

private const val DATABASE_NAME = "prompts.db"
private const val DATABASE_VERSION = 2

private const val TABLE_PROMPTS = "prompts"
private const val COLUMN_ID = "id"
private const val COLUMN_TITLE = "title"
private const val COLUMN_TEXT = "text"
private const val COLUMN_CATEGORY = "category"
private const val COLUMN_IS_FAVORITE = "is_favorite"
private const val COLUMN_LAST_UPDATED = "last_updated"

@Singleton
class PromptDatabaseHelper @Inject constructor(
    @ApplicationContext context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_PROMPTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_TEXT TEXT NOT NULL,
                $COLUMN_CATEGORY TEXT,
                $COLUMN_IS_FAVORITE INTEGER DEFAULT 0,
                $COLUMN_LAST_UPDATED INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROMPTS")
        onCreate(db)
    }

    fun addPrompt(title: String, text: String, category: String): Long {
        val values = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_TEXT, text)
            put(COLUMN_CATEGORY, category)
            put(COLUMN_IS_FAVORITE, 0)
            put(COLUMN_LAST_UPDATED, System.currentTimeMillis())
        }
        // Connection pool managed by Helper; no need to close manually.
        return writableDatabase.insert(TABLE_PROMPTS, null, values)
    }

    fun updatePrompt(id: Long, title: String, text: String, category: String, isFavorite: Boolean): Int {
        val values = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_TEXT, text)
            put(COLUMN_CATEGORY, category)
            put(COLUMN_IS_FAVORITE, if (isFavorite) 1 else 0)
            put(COLUMN_LAST_UPDATED, System.currentTimeMillis())
        }
        return writableDatabase.update(TABLE_PROMPTS, values, "$COLUMN_ID=?", arrayOf(id.toString()))
    }

    fun deletePrompt(id: Long): Int {
        return writableDatabase.delete(TABLE_PROMPTS, "$COLUMN_ID=?", arrayOf(id.toString()))
    }

    fun getAllPrompts(): List<Prompt> {
        val promptList = mutableListOf<Prompt>()
        val selectQuery = "SELECT * FROM $TABLE_PROMPTS ORDER BY $COLUMN_LAST_UPDATED DESC"

        // Use 'readableDatabase' for reads
        readableDatabase.rawQuery(selectQuery, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val prompt = Prompt(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                        text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT)),
                        category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)),
                        isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_FAVORITE)) == 1,
                        lastUpdated = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_UPDATED))
                    )
                    promptList.add(prompt)
                } while (cursor.moveToNext())
            }
        }
        return promptList
    }
}

