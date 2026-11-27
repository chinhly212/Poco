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
import androidx.compose.material3.Slider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    var cropZoom by remember { mutableFloatStateOf(1f) }
    var showCropEditor by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedImage = uri
            cropZoom = 1f
            showCropEditor = true
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
                                modifier = Modifier.size(76.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
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
            TextButton(enabled = !saving && !showCropEditor, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val avatarUrl = selectedImage?.let { uri ->
                            val bytes = withContext(Dispatchers.IO) { compressAvatar(context, uri, cropZoom) }
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

    if (showCropEditor && selectedImage != null) {
        AlertDialog(
            onDismissRequest = { showCropEditor = false; selectedImage = null },
            title = { Text("Căn chỉnh ảnh đại diện") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = "Xem trước ảnh đại diện hình tròn",
                        modifier = Modifier.size(220.dp).clip(CircleShape).graphicsLayer { scaleX = cropZoom; scaleY = cropZoom },
                        contentScale = ContentScale.Crop
                    )
                    Text("Phóng to hoặc thu nhỏ ảnh trong khung tròn", fontSize = 12.sp, color = Color(0xFF71717A))
                    Slider(value = cropZoom, onValueChange = { cropZoom = it }, valueRange = 1f..3f)
                    Text("Điều chỉnh tỷ lệ ảnh", fontSize = 12.sp, color = Color(0xFF71717A))
                }
            },
            confirmButton = {
                TextButton(onClick = { showCropEditor = false }) { Text("Dùng ảnh này") }
            },
            dismissButton = {
                TextButton(onClick = { showCropEditor = false; selectedImage = null }) { Text("Chọn lại") }
            }
        )
    }

}

private fun compressAvatar(context: android.content.Context, uri: Uri, zoom: Float): ByteArray {
    val resolver = context.contentResolver
    val source = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.createSource(resolver, uri)
    } else null
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(source!!) { decoder, _, _ ->
            decoder.setMutableRequired(true)
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(resolver, uri)
    }
    val side = (minOf(bitmap.width, bitmap.height) / zoom.coerceIn(1f, 3f)).toInt().coerceAtLeast(1)
    val left = ((bitmap.width - side) / 2).coerceAtLeast(0)
    val top = ((bitmap.height - side) / 2).coerceAtLeast(0)
    val square = Bitmap.createBitmap(bitmap, left, top, side, side)
    val resized = Bitmap.createScaledBitmap(square, 512, 512, true)
    if (square !== bitmap) square.recycle()
    var quality = 82
    var bytes: ByteArray
    do {
        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
        bytes = output.toByteArray()
        quality -= 8
    } while (bytes.size > 900 * 1024 && quality >= 50)
    resized.recycle()
    bitmap.recycle()
    require(bytes.size <= 1024 * 1024) { "Ảnh quá lớn. Hãy chọn ảnh khác." }
    return bytes
}
