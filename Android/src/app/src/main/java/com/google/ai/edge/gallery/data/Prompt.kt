package com.google.ai.edge.gallery.data

data class Prompt(
    val id: Long = 0,
    val title: String,
    val text: String,
    val category: String,
    val isFavorite: Boolean = false,
    val lastUpdated: Long // Unix timestamp for "Last updated 2 days ago"
)