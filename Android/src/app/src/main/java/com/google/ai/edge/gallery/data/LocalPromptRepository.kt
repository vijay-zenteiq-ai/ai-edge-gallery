package com.google.ai.edge.gallery.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPromptRepository @Inject constructor(
    private val dbHelper: PromptDatabaseHelper
) {

    /**
     * Adds a new prompt to the database.
     */
    fun addPrompt(title: String, text: String, category: String): Long {
        return dbHelper.addPrompt(title, text, category)
    }

    /**
     * Updates an existing prompt with new details.
     */
    fun updatePrompt(id: Long, title: String, text: String, category: String, isFavorite: Boolean): Int {
        return dbHelper.updatePrompt(id, title, text, category, isFavorite)
    }

    /**
     * Toggles the favorite status for a specific prompt.
     * We fetch the current state, flip it, and save it.
     */
    fun toggleFavorite(prompt: Prompt): Int {
        return updatePrompt(
            id = prompt.id,
            title = prompt.title,
            text = prompt.text,
            category = prompt.category,
            isFavorite = !prompt.isFavorite // Flip the boolean
        )
    }

    /**
     * Deletes a prompt by ID.
     */
    fun deletePrompt(id: Long): Int {
        return dbHelper.deletePrompt(id)
    }

    /**
     * Retrieves all prompts ordered by last updated.
     */
    fun getAllPrompts(): List<Prompt> {
        return dbHelper.getAllPrompts()
    }

    /**
     * Retrieves only prompts marked as favorites.
     */
    fun getFavoritePrompts(): List<Prompt> {
        return getAllPrompts().filter { it.isFavorite }
    }
}
