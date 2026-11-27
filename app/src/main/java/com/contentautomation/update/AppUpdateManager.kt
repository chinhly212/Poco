package com.contentautomation.update

import android.content.Context
import android.net.Uri
import android.os.Build
import com.cundong.utils.PatchUtils
import org.json.JSONObject
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
    private val githubApi = "https://api.github.com/repos/chinhly212/Poco"

    suspend fun checkForUpdate(): AvailableUpdate? = withContext(Dispatchers.IO) {
        clearTemporaryUpdateFiles()

        // Parse JSON explicitly: Gson reflection was failing in minified Release builds
        // when R8 rewrote generic DTO metadata (reported as abstract class P3.c).
        val json = JSONObject(readLatestReleaseAsset("update.json"))
        val assetsJson = json.optJSONObject("assets")
            ?: throw IOException("Thông tin cập nhật thiếu danh sách APK")
        val assets = linkedMapOf<String, ReleaseAsset>()
        val assetKeys = assetsJson.keys()
        while (assetKeys.hasNext()) {
            val abiKey = assetKeys.next()
            val item = assetsJson.optJSONObject(abiKey) ?: continue
            val apkUrl = item.optString("apkUrl").takeIf { it.isNotBlank() }
                ?: throw IOException("Thiếu đường dẫn APK cho $abiKey")
            val apkSha256 = item.optString("apkSha256").takeIf { it.isNotBlank() }
                ?: throw IOException("Thiếu mã kiểm tra APK cho $abiKey")
            assets[abiKey] = ReleaseAsset(
                apkUrl = apkUrl,
                apkSha256 = apkSha256,
                patchUrl = item.optString("patchUrl").takeIf { it.isNotBlank() && it != "null" },
                patchSha256 = item.optString("patchSha256").takeIf { it.isNotBlank() && it != "null" },
                baseVersionCode = if (item.has("baseVersionCode") && !item.isNull("baseVersionCode")) item.optLong("baseVersionCode") else null,
                baseApkSha256 = item.optString("baseApkSha256").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        val manifest = UpdateManifest(
            versionCode = json.optLong("versionCode", -1L),
            versionName = json.optString("versionName"),
            releaseNotes = json.optString("releaseNotes"),
            assets = assets
        )
        if (manifest.versionCode < 0L || manifest.versionName.isBlank() || manifest.assets.isEmpty()) {
            throw IOException("Thông tin cập nhật không đầy đủ hoặc không hợp lệ")
        }
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

    /**
     * GitHub browser_download_url có thể trả HTTP 403 qua redirect CDN.
     * Tra cứu asset ID từ Releases API rồi tải bằng endpoint chính thức của GitHub.
     */
    private fun readLatestReleaseAsset(fileName: String): String {
        val assetUrl = latestReleaseAssetApiUrl(fileName)
        val request = Request.Builder()
            .url(assetUrl)
            .header("Accept", "application/octet-stream")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Không thể tải $fileName (HTTP ${response.code})")
            }
            response.body?.string() ?: throw IOException("Tệp $fileName không có dữ liệu")
        }
    }

    private fun latestReleaseAssetApiUrl(fileName: String): String {
        val request = Request.Builder()
            .url("$githubApi/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        val releaseJson = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Không thể lấy thông tin bản phát hành (HTTP ${response.code})")
            }
            response.body?.string() ?: throw IOException("GitHub không trả thông tin bản phát hành")
        }

        val assets = JSONObject(releaseJson).optJSONArray("assets")
            ?: throw IOException("Bản phát hành không có danh sách tệp")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == fileName }
            ?: throw IOException("Không tìm thấy tệp $fileName trong bản phát hành")
        val assetId = asset.optLong("id", -1L)
        if (assetId <= 0L) throw IOException("GitHub không trả mã tệp $fileName")
        return "$githubApi/releases/assets/$assetId"
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
        val fileName = Uri.parse(url).lastPathSegment
            ?: throw IOException("Đường dẫn tệp cập nhật không hợp lệ")
        try {
            val assetUrl = latestReleaseAssetApiUrl(fileName)
            val request = Request.Builder()
                .url(assetUrl)
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()
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
