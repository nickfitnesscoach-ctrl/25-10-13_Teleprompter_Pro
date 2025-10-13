package com.teleprompter.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for teleprompter script
 */
@Entity(tableName = "scripts")
data class Script(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Optional customization per script
    val fontSize: Int? = null,
    val scrollSpeed: Int? = null,
    val textColor: Int? = null,
    val backgroundColor: Int? = null
)
