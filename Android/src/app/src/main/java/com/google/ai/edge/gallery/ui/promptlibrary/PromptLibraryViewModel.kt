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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class PromptLibraryUiState(
    val savedPrompts: List<Prompt> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val repository: LocalPromptRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptLibraryUiState())
    val uiState: StateFlow<PromptLibraryUiState> = _uiState.asStateFlow()

    init { refreshPrompts() }

    fun refreshPrompts() {
        viewModelScope.launch(Dispatchers.IO) {
            val prompts = repository.getAllPrompts()
            if (prompts.isEmpty()) {
                loadDefaults()
            } else {
                updateLegacyPrompts(prompts)
                val updatedList = repository.getAllPrompts()
                _uiState.value = _uiState.value.copy(savedPrompts = updatedList)
            }
        }
    }

    private fun updateLegacyPrompts(currentPrompts: List<Prompt>) {
        val legacyTitles = listOf("Python Docstring Generator", "SQL Query Optimizer", "Technical Blog Outline")
        currentPrompts.forEach { prompt ->
            if (legacyTitles.contains(prompt.title) && prompt.text.endsWith("...")) {
                when (prompt.title) {
                    "Python Docstring Generator" -> repository.updatePrompt(prompt.id, prompt.title, "Generate professional PEP 8 compliant docstrings for Python functions. Please include sections for Arguments, Returns, and Exceptions based on the code provided below.", prompt.category, prompt.isFavorite)
                    "SQL Query Optimizer" -> repository.updatePrompt(prompt.id, prompt.title, "Act as an expert Database Administrator. Review the following SQL query for potential performance bottlenecks and suggest optimizations such as indexing or query refactoring.", prompt.category, prompt.isFavorite)
                    "Technical Blog Outline" -> repository.updatePrompt(prompt.id, prompt.title, "Create a comprehensive outline for a 1500-word technical blog post. The outline should include an introduction, key technical concepts, implementation steps, and a concluding summary.", prompt.category, prompt.isFavorite)
                }
            }
        }
    }

    private fun loadDefaults() {
        repository.addPrompt(
            "Python Docstring Generator",
            "Generate professional PEP 8 compliant docstrings for Python functions. Please include sections for Arguments, Returns, and Exceptions based on the code provided below.",
            "ENGINEERING"
        )
        repository.addPrompt(
            "SQL Query Optimizer",
            "Act as an expert Database Administrator. Review the following SQL query for potential performance bottlenecks and suggest optimizations such as indexing or query refactoring.",
            "DATA SCIENCE"
        )
        repository.addPrompt(
            "Technical Blog Outline",
            "Create a comprehensive outline for a 1500-word technical blog post. The outline should include an introduction, key technical concepts, implementation steps, and a concluding summary.",
            "WRITING"
        )
        _uiState.value = _uiState.value.copy(savedPrompts = repository.getAllPrompts())
    }

    fun saveOrUpdatePrompt(existingId: Long?, title: String, text: String, category: String) {
        val isTitleTaken = _uiState.value.savedPrompts.any {
            it.title.trim().equals(title.trim(), ignoreCase = true) && it.id != existingId
        }
        if (isTitleTaken) {
            _uiState.update { it.copy(errorMessage = "A prompt with this title already exists. Please choose a unique title.") }
            return
        }
        val isDuplicate = _uiState.value.savedPrompts.any {
            it.title.trim().equals(title.trim(), ignoreCase = true) &&
                    it.text.trim().equals(text.trim(), ignoreCase = true) &&
                    it.id != existingId
        }

        if (isDuplicate) {
            _uiState.update { it.copy(errorMessage = "A prompt with this exact title and content already exists.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (existingId != null && existingId != 0L) {
                val isFav =
                    _uiState.value.savedPrompts.find { it.id == existingId }?.isFavorite ?: false
                repository.updatePrompt(existingId, title, text, category, isFav)
            } else {
                repository.addPrompt(title, text, category)
            }
            _uiState.update { it.copy(errorMessage = null) }
            refreshPrompts()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
