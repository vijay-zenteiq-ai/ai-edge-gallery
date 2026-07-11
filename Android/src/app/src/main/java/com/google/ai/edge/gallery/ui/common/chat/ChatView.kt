/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.common.copyBitmapToClipboard
import com.google.ai.edge.gallery.ui.common.saveBitmapToMediaStore
import com.google.ai.edge.gallery.ui.common.shareBitmap
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AGChatView"

@Composable
fun ChatView(
  task: Task,
  viewModel: ChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSendMessage: (Model, List<ChatMessage>) -> Unit,
  onRunAgainClicked: (Model, ChatMessage) -> Unit,
  onBenchmarkClicked: (Model, ChatMessage, Int, Int) -> Unit,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  skillCount: Int = 0,
  mcpCount: Int = 0,
  onResetSessionClicked:
    (model: Model, initialMessages: List<ChatMessage>, clearHistory: Boolean, onDone: () -> Unit) -> Unit =
    { _, _, _, onDone -> onDone() },
  onStreamImageMessage: (Model, ChatMessageImage) -> Unit = { _, _ -> },
  onStopButtonClicked: (Model) -> Unit = {},
  onSkillClicked: () -> Unit = {},
  onMcpClicked: () -> Unit = {},
  onPromptLibraryClicked: () -> Unit = {},
  showStopButtonInInputWhenInProgress: Boolean = false,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  emptyStateComposable: @Composable (Model) -> Unit = {},
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  promptToInput: String? = null,
) {
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel

  var selectedImageIndex by remember { mutableIntStateOf(-1) }
  var allImageViewerImages by remember { mutableStateOf<List<Bitmap>>(listOf()) }
  var showImageViewer by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val allHistorySessions by viewModel.historySessions.collectAsState()
  val historySessions = remember(allHistorySessions, task.id) { allHistorySessions.filter { it.taskId == task.id } }

  val context = LocalContext.current
  val currentMessages = uiState.messagesByModel[selectedModel.name] ?: emptyList()

  LaunchedEffect(uiState.inProgress) {
    if (!uiState.inProgress && currentMessages.isNotEmpty()) {
      viewModel.saveSession(viewModel.currentSessionId, currentMessages, selectedModel.name, task.id, context)
    }
  }

  val scope = rememberCoroutineScope()
  var navigatingUp by remember { mutableStateOf(false) }

  val handleNavigateUp = {
    navigatingUp = true
    navigateUp()
    scope.launch(Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(context, task, model)
      }
    }
  }

  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp && curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      modelManagerViewModel.initializeModel(context, task, selectedModel)
    }
  }

  LaunchedEffect(sendMessageTrigger) {
    sendMessageTrigger?.let { trigger -> onSendMessage(trigger.model, trigger.messages) }
  }

  BackHandler {
    val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
    val isModelInitializing = modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
    if (drawerState.isOpen) {
      scope.launch { drawerState.close() }
    } else if (!isModelInitializing && !uiState.inProgress) {
      handleNavigateUp()
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          ModalDrawerSheet {
            // ... (keep your ChatHistorySideSheetContent logic here)
          }
        }
      },
      gesturesEnabled = drawerState.isOpen,
    ) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
          modifier = modifier,
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            ModelPageAppBar(
              task = task,
              model = selectedModel,
              modelManagerViewModel = modelManagerViewModel,
              inProgress = uiState.inProgress,
              modelPreparing = uiState.preparing,
              shouldShowHistoryButton = true,
              onConfigChanged = { old, new ->
                // ... (keep existing config logic)
              },
              onBackClicked = { handleNavigateUp() },
              onModelSelected = { prev, cur -> /* ... */ },
              allowEditingSystemPrompt = allowEditingSystemPrompt,
              curSystemPrompt = curSystemPrompt,
              onSystemPromptChanged = onSystemPromptChanged,
              onHistoryClicked = { scope.launch { drawerState.open() } },
            )
          },
        ) { innerPadding ->
          Box {
            composableBelowMessageList(selectedModel)
            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
              AnimatedContent(targetState = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) { targetState ->
                when (targetState) {
                  true -> ChatPanel(
                    modelManagerViewModel = modelManagerViewModel,
                    task = task,
                    selectedModel = selectedModel,
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                    skillCount = skillCount,
                    mcpCount = mcpCount,
                    navigateUp = navigateUp,
                    onSendMessage = onSendMessage,
                    onRunAgainClicked = onRunAgainClicked,
                    onBenchmarkClicked = onBenchmarkClicked,
                    onStreamImageMessage = onStreamImageMessage,
                    onStreamEnd = { /* ... */ },
                    onStopButtonClicked = { onStopButtonClicked(selectedModel) },
                    onImageSelected = { bitmaps, index -> /* ... */ },
                    onSkillClicked = onSkillClicked,
                    onMcpClicked = onMcpClicked,
                    onPromptLibraryClicked = onPromptLibraryClicked, // Passed through successfully
                    promptToInput = promptToInput,
                    modifier = Modifier.weight(1f),
                    showStopButtonInInputWhenInProgress = showStopButtonInInputWhenInProgress,
                    showImagePicker = showImagePicker,
                    showAudioPicker = showAudioPicker,
                    emptyStateComposable = emptyStateComposable,
                  )
                  false -> ModelDownloadStatusInfoPanel(selectedModel, task, modelManagerViewModel)
                }
              }
            }
            // ... (keep your Image Viewer Dialog code)
          }
        }
      }
    }
  }
}