package com.contentautomation.update

import android.content.Context
import android.os.Build
import com.cundong.utils.PatchUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class ReleaseAsset(
    val apkUrl: String,
    val apkSha256: String,
    val patchUrl: String? = null,
    val patchSha256: String? = null,
    val baseVersionCode: Long? = null,
    val baseApkSha256: String? = null
)

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String = "",
    val assets: Map<String, ReleaseAsset>
)

data class AvailableUpdate(
    val manifest: UpdateManifest,
    val abi: String,
    val asset: ReleaseAsset
)

class AppUpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val manifestUrl =
        "https://github.com/chinhly212/Poco/releases/latest/download/update.json"

    suspend fun checkForUpdate(): AvailableUpdate? = withContext(Dispatchers.IO) {
        clearTemporaryUpdateFiles()

        val request = Request.Builder().url(manifestUrl).get().build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Không thể kiểm tra cập nhật (HTTP ${response.code})")
            }
            response.body?.string() ?: throw IOException("Máy chủ cập nhật không trả dữ liệu")
        }

        val manifest = Gson().fromJson(json, UpdateManifest::class.java)
            ?: throw IOException("Thông tin cập nhật không hợp lệ")
        if (manifest.versionCode <= installedVersionCode()) return@withContext null

        val abi = Build.SUPPORTED_ABIS.firstOrNull { manifest.assets.containsKey(it) }
            ?: throw IOException("Không có bản cập nhật phù hợp với kiến trúc điện thoại")
        val asset = manifest.assets[abi]
            ?: throw IOException("Thiếu tệp cập nhật cho kiến trúc $abi")
        AvailableUpdate(manifest, abi, asset)
    }

    suspend fun downloadAndPrepare(update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val target = update.manifest.versionCode
        val asset = update.asset
        val installedApk = File(context.applicationInfo.sourceDir)

        // Chỉ dùng bản vá khi phiên bản VÀ SHA-256 của APK gốc trùng khớp tuyệt đối.
        if (
            !asset.patchUrl.isNullOrBlank() &&
            !asset.patchSha256.isNullOrBlank() &&
            !asset.baseApkSha256.isNullOrBlank() &&
            asset.baseVersionCode == installedVersionCode() &&
            installedApk.isFile &&
            sha256(installedApk).equals(asset.baseApkSha256, ignoreCase = true)
        ) {
            val patchFile = File(context.cacheDir, "poco-update-$target.patch")
            val rebuiltApk = File(context.cacheDir, "poco-update-$target.apk")
            try {
                download(asset.patchUrl, patchFile)
                if (!sha256(patchFile).equals(asset.patchSha256, ignoreCase = true)) {
                    throw IOException("Bản vá tải về không đúng SHA-256")
                }
                rebuiltApk.delete()
                val result = PatchUtils.patch(
                    installedApk.absolutePath,
                    rebuiltApk.absolutePath,
                    patchFile.absolutePath
                )
                if (result != PatchUtils.SUCCESS) {
                    throw IOException("Không thể ghép bản vá (mã lỗi $result)")
                }
                verifyTargetApk(rebuiltApk, update)
                patchFile.delete()
                return@withContext rebuiltApk
            } catch (_: Exception) {
                patchFile.delete()
                rebuiltApk.delete()
                // Nếu bản vá không dùng được, chuyển sang tải APK đầy đủ bên dưới.
            } catch (_: LinkageError) {
                patchFile.delete()
                rebuiltApk.delete()
            }
        }

        val fullApk = File(context.cacheDir, "poco-update-full-$target.apk")
        fullApk.delete()
        download(asset.apkUrl, fullApk)
        verifyTargetApk(fullApk, update)
        fullApk
    }

    private fun clearTemporaryUpdateFiles() {
        context.cacheDir.listFiles()
            ?.filter {
                it.name.startsWith("poco-update-") &&
                    (it.extension == "apk" || it.extension == "patch")
            }
            ?.forEach { it.delete() }
    }

    private fun verifyTargetApk(file: File, update: AvailableUpdate) {
        if (!file.isFile || !sha256(file).equals(update.asset.apkSha256, ignoreCase = true)) {
            file.delete()
            throw IOException("APK cập nhật không vượt qua kiểm tra SHA-256")
        }

        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("Tệp tải về không phải APK hợp lệ")
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        if (packageInfo.packageName != context.packageName || version != update.manifest.versionCode) {
            file.delete()
            throw IOException("APK không đúng ứng dụng hoặc sai phiên bản")
        }
    }

    private fun installedVersionCode(): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun download(url: String, destination: File) {
        destination.parentFile?.mkdirs()
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Tải cập nhật thất bại (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Tệp cập nhật trống")
                body.byteStream().use { input ->
                    destination.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
