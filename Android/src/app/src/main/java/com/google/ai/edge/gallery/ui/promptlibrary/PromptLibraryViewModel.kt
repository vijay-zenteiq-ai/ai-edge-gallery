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

package com.google.ai.edge.gallery.ui.promptlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.LocalPromptRepository
import com.google.ai.edge.gallery.data.Prompt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class PromptLibraryUiState(
    val savedPrompts: List<Prompt> = emptyList()
)

@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val repository: LocalPromptRepository // Injecting your SQLite Repository wrapper here
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptLibraryUiState())
    val uiState: StateFlow<PromptLibraryUiState> = _uiState.asStateFlow()

    init {
        refreshPrompts()
    }

    // Pulls data from SQLite on a background thread and applies formatting updates
    fun refreshPrompts() {
        viewModelScope.launch(Dispatchers.IO) {
            val prompts = repository.getAllPrompts()

            // If database is completely brand new, load your mock items safely
            if (prompts.isEmpty()) {
                loadDefaults()
            } else {
                _uiState.value = PromptLibraryUiState(savedPrompts = prompts)
            }
        }
    }

    private fun loadDefaults() {
        repository.addPrompt("Python Docstring Generator", "Generate professional PEP 8 compliant docstrings for Python functions including args, returns, and...", "ENGINEERING")
        repository.addPrompt("SQL Query Optimizer", "Review this complex SQL JOIN query and suggest indexing or restructuring for better performance i...", "DATA SCIENCE")
        repository.addPrompt("Technical Blog Outline", "Create a detailed outline for a 1500-word blog post about the benefits of micro-frontends in...", "WRITING")

        // Refresh the database state snapshot to render screen contents
        val updatedPrompts = repository.getAllPrompts()
        _uiState.value = PromptLibraryUiState(savedPrompts = updatedPrompts)
    }

    // Handles both new additions and direct modifications cleanly based on numeric ID status
    fun saveOrUpdatePrompt(existingId: Long?, title: String, text: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (existingId != null && existingId != 0L) {
                // To keep database calls clean, find the item to pull its current favorite flag
                val currentList = _uiState.value.savedPrompts
                val existingPrompt = currentList.find { it.id == existingId }
                val isFav = existingPrompt?.isFavorite ?: false

                repository.updatePrompt(
                    id = existingId,
                    title = title,
                    text = text,
                    category = category,
                    isFavorite = isFav
                )
            } else {
                repository.addPrompt(title, text, category)
            }
            refreshPrompts()
        }
    }

    fun toggleFavorite(prompt: Prompt) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(prompt)
            refreshPrompts()
        }
    }

    fun deletePrompt(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePrompt(id)
            refreshPrompts()
        }
    }

    // Helper method to convert database timestamp numbers to UI text fields dynamically
    fun formatTimestamp(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            hours < 1 -> "Just now"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            else -> "${days}d ago"
        }
    }
}