package com.contentautomation.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.contentautomation.data.remote.PocoChatProfile
import com.contentautomation.data.remote.PocoChatRepository
import com.contentautomation.data.remote.SupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Composable
fun AccountProfileDialog(
    auth: SupabaseAuth,
    initialProfile: PocoChatProfile?,
    onDismiss: () -> Unit,
    onSaved: (PocoChatProfile) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context, auth) { PocoChatRepository(context.applicationContext, auth) }
    val scope = rememberCoroutineScope()
    var displayName by remember(initialProfile) { mutableStateOf(initialProfile?.displayName.orEmpty()) }
    var nickname by remember(initialProfile) { mutableStateOf(initialProfile?.nickname.orEmpty()) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedImage = uri
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Chỉnh sửa hồ sơ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (selectedImage != null || !initialProfile?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = selectedImage ?: initialProfile?.avatarUrl,
                                contentDescription = "Ảnh đại diện",
                                modifier = Modifier.size(76.dp).clip(CircleShape)
                            )
                        } else {
                            Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0xFFF4F4F5)), contentAlignment = Alignment.Center) {
                                Text(displayName.trim().firstOrNull()?.uppercase() ?: "?", fontSize = 26.sp)
                            }
                        }
                        TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !saving) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Text(" Đổi ảnh")
                        }
                    }
                }
                OutlinedTextField(
                    value = displayName, onValueChange = { displayName = it.take(60) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Tên hiển thị") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it.take(40) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Biệt danh") },
                    singleLine = true
                )
                Text("ID MuChi: ${initialProfile?.accountId ?: "Đang tải…"}", fontSize = 12.sp, color = Color(0xFF71717A))
                error?.let { Text(it, color = Color(0xFFB91C1C), fontSize = 12.sp) }
                if (saving) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(" Đang lưu và đồng bộ…", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val avatarUrl = selectedImage?.let { uri ->
                            val bytes = withContext(Dispatchers.IO) { compressAvatar(context, uri) }
                            repository.uploadAvatar(bytes)
                        } ?: initialProfile?.avatarUrl
                        repository.updateMyProfile(displayName, nickname, avatarUrl)
                        val updated = repository.myProfile()
                        onSaved(updated)
                    } catch (e: Exception) {
                        error = e.message ?: "Không thể lưu hồ sơ."
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Hủy") }
        }
    )
}

private fun compressAvatar(context: android.content.Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val source = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.createSource(resolver, uri)
    } else null
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(source!!) { decoder, _, _ ->
            decoder.isMutableRequired = true
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(resolver, uri)
    }
    val maxDimension = max(bitmap.width, bitmap.height)
    val scale = minOf(1f, 512f / maxDimension.toFloat())
    val resized = if (scale < 1f) Bitmap.createScaledBitmap(
        bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1), true
    ) else bitmap
    var quality = 82
    var bytes: ByteArray
    do {
        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
        bytes = output.toByteArray()
        quality -= 8
    } while (bytes.size > 900 * 1024 && quality >= 50)
    if (resized !== bitmap) resized.recycle()
    bitmap.recycle()
    require(bytes.size <= 1024 * 1024) { "Ảnh quá lớn. Hãy chọn ảnh khác." }
    return bytes
}
