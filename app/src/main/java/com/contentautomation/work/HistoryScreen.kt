
package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.data.db.entity.TikTokPost
import com.contentautomation.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val posts by vm.history.collectAsStateWithLifecycle()

    if (posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Chưa có bài đăng nào")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = posts,
            key = { post -> post.id }
        ) { post ->
            HistoryRow(p = post)
        }
    }
}

@Composable
private fun HistoryRow(p: TikTokPost) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                "Publish ID: ${p.publishId ?: "-"}",
                style = MaterialTheme.typography.titleSmall
            )

            Text("Trạng thái: ${p.status}")
            Text("Quyền riêng tư: ${p.privacyLevel ?: "-"}")

            Text(
                "Thời gian: ${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(Date(p.createdAt))
                }"
            )

            p.errorMessage?.let {
                Text(
                    "Lỗi: $it",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
