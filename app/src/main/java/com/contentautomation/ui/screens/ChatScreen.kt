package com.contentautomation.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.contentautomation.data.remote.PocoChatConversation
import com.contentautomation.data.remote.PocoChatMessage
import com.contentautomation.data.remote.PocoChatProfile
import com.contentautomation.data.remote.PocoChatRepository
import com.contentautomation.data.remote.SupabaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChatWhite = Color(0xFFFFFFFF)
private val ChatSurface = Color(0xFFF4F4F5)
private val ChatBorder = Color(0xFFE4E4E7)
private val ChatInk = Color(0xFF18181B)
private val ChatMuted = Color(0xFF71717A)

@Composable
fun ChatScreen(auth: SupabaseAuth, onDetailChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember(context, auth) { PocoChatRepository(context.applicationContext, auth) }
    val scope = rememberCoroutineScope()
    val preferences = remember(context) {
        context.getSharedPreferences("muchi_chat_nicknames", Context.MODE_PRIVATE)
    }
    val appearancePrefs = remember(context) {
        context.getSharedPreferences("muchi_chat_appearance", Context.MODE_PRIVATE)
    }
    var ownBubbleColor by remember {
        mutableStateOf(Color(appearancePrefs.getInt("own_bubble_color", 0xFF18181B.toInt())))
    }
    var chatBackgroundUri by remember {
        mutableStateOf(appearancePrefs.getString("chat_background_uri", null))
    }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            chatBackgroundUri = uri.toString()
            appearancePrefs.edit().putString("chat_background_uri", uri.toString()).apply()
        }
    }
    var myId by remember { mutableStateOf<String?>(null) }
    var myAccountId by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf<List<PocoChatConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<PocoChatConversation?>(null) }
    var messages by remember { mutableStateOf<List<PocoChatMessage>>(emptyList()) }
    var peerProfile by remember { mutableStateOf<PocoChatProfile?>(null) }
    var nicknames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf("") }
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
        nicknames = preferences.all.mapNotNull { (key, value) ->
            (value as? String)?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
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

    LaunchedEffect(selected?.id) {
        onDetailChanged(selected != null)
        peerProfile = null
        val active = selected
        val uid = myId
        if (active != null && uid != null && !active.isGroup) {
            runCatching { repo.conversationPeer(active.id, uid) }
                .onSuccess { peerProfile = it }
                .onFailure { error = it.message }
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

    fun displayName(profile: PocoChatProfile?): String {
        if (profile == null) return selected?.title?.takeIf { it.isNotBlank() } ?: "Cuộc trò chuyện"
        return nicknames[profile.userId]
            ?: profile.displayName.takeIf { it.isNotBlank() }
            ?: profile.accountId.takeIf { it.isNotBlank() }
            ?: profile.username.takeIf { it.isNotBlank() }
            ?: profile.userId
    }

    Scaffold(containerColor = ChatWhite) { padding ->
        Column(
            Modifier.fillMaxSize().background(ChatWhite)
                .padding(padding)
                .padding(horizontal = if (selected == null) 16.dp else 0.dp)
        ) {
            if (selected == null) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tin nhắn", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChatInk)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(ChatSurface)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("MuChi ID", myAccountId.orEmpty()))
                            }
                            .padding(start = 9.dp, end = 5.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ID: ${myAccountId ?: "…"}", fontSize = 11.sp, color = ChatInk, fontWeight = FontWeight.Medium)
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Sao chép ID", modifier = Modifier.padding(start = 5.dp).size(15.dp), tint = ChatMuted)
                    }
                    IconButton(onClick = {
                        groupMode = !groupMode
                        selectedMembers.clear()
                        groupTitle = ""
                    }) { Icon(Icons.Outlined.GroupAdd, contentDescription = "Thêm bạn hoặc tạo nhóm", tint = ChatInk) }
                }

                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(ChatSurface).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = ChatMuted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ChatInk),
                        decorationBox = { inner ->
                            Box {
                                if (search.isEmpty()) Text("Tìm ID, tên hoặc username...", color = ChatMuted, fontSize = 13.sp)
                                inner()
                            }
                        }
                    )
                }

                if (groupMode) {
                    Spacer(Modifier.height(10.dp))
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
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ChatInk)
                    }
                } else if (results.isNotEmpty()) {
                    Text(if (groupMode) "Chọn thành viên" else "Kết quả tìm kiếm", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 5.dp), color = ChatInk)
                    LazyColumn(Modifier.weight(1f)) {
                        items(results, key = { it.userId }) { profile ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (!groupMode) launchAction {
                                        val id = repo.createDirect(profile.userId)
                                        refreshConversations()
                                        selected = conversations.firstOrNull { it.id == id }
                                            ?: PocoChatConversation(id, profile.displayName.ifBlank { profile.accountId }, false, "")
                                        search = ""
                                    }
                                }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChatAvatar(profile.displayName.ifBlank { profile.accountId }, 44, profile.avatarUrl)
                                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                    Text(profile.displayName.ifBlank { profile.accountId.ifBlank { profile.userId } }, fontWeight = FontWeight.SemiBold, color = ChatInk, fontSize = 14.sp)
                                    Text("${profile.accountId}  ·  @${profile.username}", fontSize = 12.sp, color = ChatMuted)
                                }
                                if (groupMode) {
                                    Checkbox(
                                        checked = profile.userId in selectedMembers,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedMembers.add(profile.userId)
                                            else selectedMembers.remove(profile.userId)
                                        }
                                    )
                                } else Text("Nhắn tin", color = ChatInk, fontSize = 12.sp)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ChatBorder))
                        }
                    }
                } else if (search.length >= 2) {
                    Text("Không tìm thấy người dùng.", color = ChatMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                } else if (search.isBlank()) {
                    Text("Cuộc trò chuyện", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ChatMuted, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                    if (conversations.isEmpty() && !loading) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Chưa có tin nhắn. Tìm ID MuChi, tên hoặc username để bắt đầu.", color = ChatMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(conversations, key = { it.id }) { conversation ->
                                val title = conversation.title.ifBlank { "Cuộc trò chuyện" }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selected = conversation
                                        launchAction { messages = repo.messages(conversation.id) }
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChatAvatar(title, 44, conversation.avatarUrl)
                                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                        Text(title, fontWeight = FontWeight.SemiBold, color = ChatInk, fontSize = 14.sp, maxLines = 1)
                                        Text(conversation.lastMessage.ifBlank { "Mở cuộc trò chuyện" }, color = ChatMuted, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(ChatBorder))
                            }
                        }
                    }
                }
            } else {
                val active = selected!!
                val peerName = if (active.isGroup) active.title else displayName(peerProfile)
                Row(
                    Modifier.fillMaxWidth().height(58.dp).background(ChatWhite)
                        .border(width = 0.dp, color = ChatWhite).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selected = null; messages = emptyList(); peerProfile = null }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Quay lại", tint = ChatInk)
                    }
                    ChatAvatar(peerName, 36, peerProfile?.avatarUrl)
                    Text(
                        peerName,
                        modifier = Modifier.padding(start = 9.dp).weight(1f),
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ChatInk, maxLines = 1
                    )
                    if (!active.isGroup && peerProfile != null) {
                        IconButton(onClick = {
                            nicknameDraft = nicknames[peerProfile!!.userId].orEmpty()
                            showNicknameDialog = true
                        }) { Icon(Icons.Outlined.Edit, contentDescription = "Sửa biệt danh", tint = ChatMuted) }
                    }
                    IconButton(onClick = { showAppearanceDialog = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Tùy chỉnh phòng chat", tint = ChatInk)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ChatBorder))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (!chatBackgroundUri.isNullOrBlank()) {
                        AsyncImage(
                            model = chatBackgroundUri,
                            contentDescription = "Ảnh nền phòng chat",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
                    } else {
                        Box(Modifier.fillMaxSize().background(ChatWhite))
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
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
                                    .widthInChatBubble()
                                    .clip(
                                        if (mine) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                                        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                                    )
                                    .background(if (mine) ownBubbleColor else ChatSurface.copy(alpha = 0.96f))
                                    .padding(horizontal = 13.dp, vertical = 9.dp),
                                color = if (mine) Color.White else ChatInk,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                }
                Row(
                    Modifier.fillMaxWidth().background(ChatWhite).imePadding().padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(44.dp).clip(CircleShape).background(ChatSurface).padding(start = 15.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = ChatInk),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text("Nhắn tin...", color = ChatMuted, fontSize = 14.sp)
                                inner()
                            }
                        }
                    )
                    IconButton(
                        enabled = draft.isNotBlank() && !busy,
                        onClick = {
                            val current = selected ?: return@IconButton
                            val text = draft
                            launchAction {
                                repo.sendMessage(current.id, myId ?: error("Chưa xác định tài khoản"), text)
                                draft = ""
                                refreshMessages()
                                refreshConversations()
                            }
                        },
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(ChatInk)
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = "Gửi", tint = ChatWhite, modifier = Modifier.size(17.dp))
                    }
                }
            }
            error?.let {
                Text(it, color = Color(0xFFB3261E), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (busy) Text("Đang xử lý…", color = ChatMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
        }
    }

    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Tùy chỉnh phòng Chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Màu bong bóng tin nhắn của bạn", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(
                            "Đen" to Color(0xFF18181B),
                            "Xanh navy" to Color(0xFF1E3A8A),
                            "Xanh rêu" to Color(0xFF365A40),
                            "Tím than" to Color(0xFF43305D)
                        ).forEach { (label, color) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(color)
                                        .border(if (ownBubbleColor == color) 3.dp else 1.dp, if (ownBubbleColor == color) Color(0xFF9CA3AF) else ChatBorder, CircleShape)
                                        .clickable {
                                            ownBubbleColor = color
                                            appearancePrefs.edit().putInt("own_bubble_color", color.toArgb()).apply()
                                        }
                                )
                                Text(label, fontSize = 9.sp, color = ChatMuted)
                            }
                        }
                    }
                    TextButton(onClick = { backgroundPicker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Text(" Chọn ảnh nền từ điện thoại")
                    }
                    if (!chatBackgroundUri.isNullOrBlank()) {
                        TextButton(onClick = {
                            chatBackgroundUri = null
                            appearancePrefs.edit().remove("chat_background_uri").apply()
                        }) { Text("Xóa ảnh nền") }
                    }
                    Text("Ảnh nền được lưu trên thiết bị này; lớp phủ tối nhẹ giúp nội dung dễ đọc.", fontSize = 11.sp, color = ChatMuted)
                }
            },
            confirmButton = { TextButton(onClick = { showAppearanceDialog = false }) { Text("Xong") } }
        )
    }

    if (showNicknameDialog && peerProfile != null) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Đặt biệt danh") },
            text = {
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it },
                    singleLine = true,
                    label = { Text("Biệt danh") },
                    placeholder = { Text(peerProfile?.displayName ?: "Tên hiển thị") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = peerProfile!!.userId
                    val clean = nicknameDraft.trim()
                    if (clean.isBlank()) preferences.edit().remove(id).apply()
                    else preferences.edit().putString(id, clean).apply()
                    nicknames = nicknames.toMutableMap().apply {
                        if (clean.isBlank()) remove(id) else put(id, clean)
                    }
                    showNicknameDialog = false
                }) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun ChatAvatar(name: String, size: Int, avatarUrl: String? = null) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(ChatSurface),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Ảnh đại diện $name",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(initial, color = ChatInk, fontWeight = FontWeight.SemiBold, fontSize = (size * 0.38f).sp)
        }
    }
}

private fun Modifier.widthInChatBubble(): Modifier =
    this.then(Modifier.fillMaxWidth(0.82f))
