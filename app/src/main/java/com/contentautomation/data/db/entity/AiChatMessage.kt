package com.contentautomation.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ai_chat_messages",
    indices = [Index(value = ["createdAt"])]
)
data class AiChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val message: String,
    val fromUser: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
