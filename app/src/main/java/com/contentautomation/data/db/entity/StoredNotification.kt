package com.contentautomation.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stored_notifications",
    indices = [Index(value = ["created_at"])]
)
data class StoredNotification(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val message: String,
    val time: String,
    @androidx.room.ColumnInfo(name = "action_label") val actionLabel: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long
)
