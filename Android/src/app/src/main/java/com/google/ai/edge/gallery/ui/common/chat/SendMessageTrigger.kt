package com.google.ai.edge.gallery.ui.common.chat

import com.google.ai.edge.gallery.data.Model

data class SendMessageTrigger(
    val model: Model,
    val messages: List<ChatMessage>
)