package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.remote.PocoChatConversation
import com.contentautomation.data.remote.PocoChatMessage
import com.contentautomation.data.remote.PocoChatProfile
import com.contentautomation.data.remote.PocoChatRepository
import com.contentautomation.data.remote.SupabaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(auth: SupabaseAuth) {
    val context = LocalContext.current
    val repo = remember(context, auth) { PocoChatRepository(context.applicationContext, auth) }
    val scope = rememberCoroutineScope()
    var myId by remember { mutableStateOf<String?>(null) }
    var myAccountId by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf<List<PocoChatConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<PocoChatConversation?>(null) }
    var messages by remember { mutableStateOf<List<PocoChatMessage>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PocoChatProfile>>(emptyList()) }
    var groupMode by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refreshConversations() {
        val uid = myId ?: return
        conversations = repo.conversations(uid)
        if (selected != null) {
            selected = conversations.firstOrNull { it.id == selected?.id } ?: selected
        }
    }
    suspend fun refreshMessages() {
        val active = selected ?: return
        messages = repo.messages(active.id)
    }

    LaunchedEffect(Unit) {
        try {
            myId = repo.myUserId()
            myAccountId = repo.myAccountId()
            refreshConversations()
        } catch (e: Exception) {
            error = e.message ?: "Không thể kết nối Chat POCO."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(myId, selected?.id) {
        if (myId != null) {
            while (true) {
                runCatching {
                    refreshConversations()
                    if (selected != null) refreshMessages()
                }.onFailure { error = it.message ?: "Không thể đồng bộ Chat." }
                delay(3500)
            }
        }
    }

    LaunchedEffect(search, groupMode) {
        if (search.trim().length >= 2) {
            delay(350)
            runCatching { repo.searchUsers(search) }
                .onSuccess { results = it.filter { p -> p.userId != myId } }
                .onFailure { error = it.message }
        } else results = emptyList()
    }

    fun launchAction(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { action() } catch (e: Exception) {
                error = e.message ?: "Có lỗi khi thao tác với Chat."
            } finally { busy = false }
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (selected == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null)
                    Text("  MuChi Chat", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        groupMode = !groupMode
                        selectedMembers.clear()
                        groupTitle = ""
                    }) { Icon(Icons.Rounded.GroupAdd, contentDescription = "Tạo nhóm") }
                }
                Text("ID MuChi của bạn: ${myAccountId ?: "Đang tải..."}", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (groupMode) "Tìm bằng ID MuChi hoặc tên để thêm vào nhóm" else "Tìm bằng ID MuChi, tên hoặc username") },
                    singleLine = true
                )
                if (groupMode) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupTitle, onValueChange = { groupTitle = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Tên nhóm") }, singleLine = true
                    )
                    Button(
                        onClick = {
                            val members = selectedMembers.toList()
                            launchAction {
                                require(groupTitle.isNotBlank()) { "Hãy nhập tên nhóm." }
                                require(members.isNotEmpty()) { "Hãy chọn ít nhất một thành viên." }
                                val id = repo.createGroup(groupTitle, members)
                                groupMode = false
                                search = ""
                                selectedMembers.clear()
                                refreshConversations()
                                selected = conversations.firstOrNull { it.id == id }
                                    ?: PocoChatConversation(id, groupTitle, true, "")
                            }
                        },
                        enabled = !busy && groupTitle.isNotBlank() && selectedMembers.isNotEmpty()
                    ) { Text("Tạo nhóm (${selectedMembers.size})") }
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.padding(20.dp))
                } else if (results.isNotEmpty()) {
                    Text(if (groupMode) "Chọn thành viên" else "Kết quả tìm kiếm", fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(results, key = { it.userId }) { profile ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(profile.displayName.ifBlank { profile.username }, fontWeight = FontWeight.SemiBold)
                                    Text("${profile.accountId}  ·  @${profile.username}", fontSize = 12.sp, color = Color.Gray)
                                }
                                if (groupMode) {
                                    Checkbox(
                                        checked = profile.userId in selectedMembers,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedMembers.add(profile.userId)
                                            else selectedMembers.remove(profile.userId)
                                        }
                                    )
                                } else {
                                    TextButton(onClick = {
                                        launchAction {
                                            val id = repo.createDirect(profile.userId)
                                            refreshConversations()
                                            selected = conversations.firstOrNull { it.id == id }
                                                ?: PocoChatConversation(id, profile.displayName, false, "")
                                            search = ""
                                        }
                                    }) { Text("Nhắn tin") }
                                }
                            }
                        }
                    }
                } else if (search.length >= 2) {
                    Text("Không tìm thấy người dùng.", color = Color.Gray, fontSize = 13.sp)
                }
                if (search.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Cuộc trò chuyện", fontWeight = FontWeight.SemiBold)
                    if (conversations.isEmpty() && !loading) {
                        Text("Chưa có cuộc trò chuyện. Tìm ID MuChi, tên hoặc username ở trên để bắt đầu.", color = Color.Gray, fontSize = 13.sp)
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { conversation ->
                            TextButton(
                                onClick = {
                                    selected = conversation
                                    launchAction { messages = repo.messages(conversation.id) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        (if (conversation.isGroup) "👥 " else "") + conversation.title,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(conversation.lastMessage.ifBlank { "Mở cuộc trò chuyện" }, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = null; messages = emptyList() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                    Column {
                        Text(selected!!.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (selected!!.isGroup) "Nhóm MuChi" else "Trò chuyện riêng", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                    reverseLayout = false
                ) {
                    items(messages, key = { it.id }) { message ->
                        val mine = message.senderId == myId
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                        ) {
                            Text(
                                message.body,
                                modifier = Modifier
                                    .background(
                                        if (mine) Color(0xFFDCEBFF) else Color(0xFFECEFF3),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                color = Color(0xFF182230),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Tin nhắn") }
                    )
                    IconButton(
                        enabled = draft.isNotBlank() && !busy,
                        onClick = {
                            val active = selected ?: return@IconButton
                            val text = draft
                            launchAction {
                                repo.sendMessage(active.id, myId ?: error("Chưa xác định tài khoản"), text)
                                draft = ""
                                refreshMessages()
                                refreshConversations()
                            }
                        }
                    ) { Icon(Icons.Rounded.Send, contentDescription = "Gửi") }
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color(0xFFB3261E), fontSize = 12.sp)
            }
            if (busy) Text("Đang xử lý…", color = Color.Gray, fontSize = 11.sp)
        }
    }
}
