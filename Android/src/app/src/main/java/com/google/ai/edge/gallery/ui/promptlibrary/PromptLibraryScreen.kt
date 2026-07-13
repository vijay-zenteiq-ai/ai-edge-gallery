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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.ai.edge.gallery.data.Prompt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PromptLibraryScreen(
    navigateUp: () -> Unit,
    onPromptSelected: ((String) -> Unit)? = null,
    viewModel: PromptLibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var promptInstructionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ENGINEERING") }

    var currentTab by remember { mutableIntStateOf(0) }

    var editingPromptId by remember { mutableStateOf<Long?>(null) }
    var editingOriginalTitle by remember { mutableStateOf("") }
    var activeViewItem by remember { mutableStateOf<Prompt?>(null) }
    var activeDeleteId by remember { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val handleBack: () -> Unit = {
        if (uiState.errorMessage != null) {
            viewModel.clearError()
        } else if (activeViewItem != null) {
            activeViewItem = null
        } else if (activeDeleteId != null) {
            activeDeleteId = null
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (currentTab != 0) {
            currentTab = 0
        } else if (editingPromptId != null || titleText.isNotEmpty() || promptInstructionText.isNotEmpty()) {
            titleText = ""
            promptInstructionText = ""
            editingPromptId = null
            focusManager.clearFocus()
            keyboardController?.hide()
        } else {
            navigateUp()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentTab == 1) "Favorite Prompts" else "Prompt Library",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Library") },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                    label = { Text("Favorites") }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Input Container Block
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search your prompts...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search bar icon") },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        )
                    )
                }
            }

            // Forms Layout Panel
            if (currentTab == 0) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (editingPromptId != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Editing Mode",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "You are currently updating '$editingOriginalTitle'.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                if (uiState.errorMessage != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Duplicate Entry",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = uiState.errorMessage!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.clearError() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Dismiss error",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = if (editingPromptId != null) "Update Prompt Details" else "Add New Prompt",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text("Title", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = titleText,
                                    onValueChange = { if (it.length <= 50) titleText = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    placeholder = { Text("e.g. Python Docstring Gen", color = Color.Gray) },
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = "${titleText.length} / 50",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text("Prompt Text", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = promptInstructionText,
                                    onValueChange = { if (it.length <= 1000) promptInstructionText = it },
                                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 4.dp),
                                    placeholder = { Text("Enter your prompt instructions here...", color = Color.Gray) },
                                    shape = RoundedCornerShape(8.dp),
                                    supportingText = {
                                        Text(
                                            text = "${promptInstructionText.length} / 1000",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (editingPromptId != null) {
                                        OutlinedButton(
                                            onClick = {
                                                titleText = ""
                                                promptInstructionText = ""
                                                editingPromptId = null
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(24.dp)
                                        ) {
                                            Text("Cancel")
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (titleText.isNotBlank() && promptInstructionText.isNotBlank()) {
                                                viewModel.saveOrUpdatePrompt(
                                                    existingId = editingPromptId,
                                                    title = titleText,
                                                    text = promptInstructionText,
                                                    category = selectedCategory
                                                )
                                                titleText = ""
                                                promptInstructionText = ""
                                                editingPromptId = null
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        },
                                        modifier = Modifier.weight(2f),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Icon(painterResource(id = android.R.drawable.ic_menu_save), contentDescription = "Save")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (editingPromptId != null) "Update Prompt" else "Save Prompt")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Headers Ribbon Segment
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentTab == 1) "FAVORITE PROMPTS" else "RECENTLY SAVED",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val filteredPrompts = uiState.savedPrompts.filter { prompt ->
                val matchesTab = (currentTab == 0) || (currentTab == 1 && prompt.isFavorite)
                val matchesSearch = prompt.title.contains(searchQuery, ignoreCase = true) ||
                        prompt.text.contains(searchQuery, ignoreCase = true)
                matchesTab && matchesSearch
            }

            if (filteredPrompts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentTab == 1) "No favorite prompts yet." else "No prompts found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(filteredPrompts, key = { it.id }) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        PromptItemCard(
                            promptItem = item,
                            timestampText = viewModel.formatTimestamp(item.lastUpdated),
                            onDelete = { activeDeleteId = item.id },
                            onView = { activeViewItem = item },
                            onFavoriteToggle = { viewModel.toggleFavorite(item) },
                            onEdit = {
                                currentTab = 0
                                editingPromptId = item.id
                                editingOriginalTitle = item.title
                                titleText = item.title
                                promptInstructionText = item.text
                                selectedCategory = item.category
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            onSelect = if (onPromptSelected != null) {
                                { onPromptSelected(item.text) }
                            } else null
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Full detail modal popup window handler
    activeViewItem?.let { prompt ->
        AlertDialog(
            onDismissRequest = { activeViewItem = null },
            title = { Text(text = prompt.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = prompt.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    Text(
                        text = prompt.text,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeViewItem = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Deletion prompt verification confirmation frame
    if (activeDeleteId != null) {
        AlertDialog(
            onDismissRequest = { activeDeleteId = null },
            title = { Text(text = "Confirm Deletion", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this prompt? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        activeDeleteId?.let { id ->
                            viewModel.deletePrompt(id)
                            if (editingPromptId == id) {
                                titleText = ""
                                promptInstructionText = ""
                                editingPromptId = null
                            }
                        }
                        activeDeleteId = null
                        focusManager.clearFocus()
                        Toast.makeText(context, "Prompt deleted successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDeleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PromptItemCard(
    promptItem: Prompt,
    timestampText: String,
    onDelete: () -> Unit,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSelect: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onSelect != null) Modifier.clickable { onSelect() } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = promptItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (promptItem.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite selection tracking status",
                        tint = if (promptItem.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = promptItem.category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = promptItem.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onView) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "Preview full item popup trigger",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modify edit layout selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Trigger warning validation modal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
