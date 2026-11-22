package com.contentautomation.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.contentautomation.data.db.entity.AiChatMessage

@Dao
interface AiChatDao {
    @Insert
    suspend fun insert(message: AiChatMessage)

    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC")
    suspend fun getAll(): List<AiChatMessage>

    @Query("DELETE FROM ai_chat_messages")
    suspend fun deleteAll()
}
