package com.contentautomation.update

import android.content.Context
import android.net.Uri
import android.os.Build
import com.cundong.utils.PatchUtils
import com.google.gson.JsonParser
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

        val json = readLatestReleaseAsset("update.json")
        val manifest = parseUpdateManifest(json)
        if (manifest.versionCode <= installedVersionCode()) return@withContext null

        val abi = Build.SUPPORTED_ABIS.firstOrNull { manifest.assets.containsKey(it) }
            ?: throw IOException("Không có bản cập nhật phù hợp với kiến trúc điện thoại")
        val asset = manifest.assets[abi]
            ?: throw IOException("Thiếu tệp cập nhật cho kiến trúc $abi")
        AvailableUpdate(manifest, abi, asset)
    }


    /**
     * Parse the release manifest explicitly. This avoids Gson reflection trying to
     * instantiate obfuscated Kotlin/collection implementation classes in minified builds.
     */
    private fun parseUpdateManifest(json: String): UpdateManifest {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (error: Exception) {
            throw IOException("Thông tin cập nhật không hợp lệ", error)
        }
        fun requiredLong(name: String): Long =
            root.get(name)?.takeUnless { it.isJsonNull }?.asLong
                ?: throw IOException("Thiếu trường $name trong update.json")
        fun requiredString(name: String): String =
            root.get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                ?: throw IOException("Thiếu trường $name trong update.json")

        val assetObject = root.getAsJsonObject("assets")
            ?: throw IOException("Manifest không có danh sách APK")
        val parsedAssets = linkedMapOf<String, ReleaseAsset>()
        for ((abi, value) in assetObject.entrySet()) {
            if (!value.isJsonObject) continue
            val item = value.asJsonObject
            fun optionalString(name: String): String? =
                item.get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            fun optionalLong(name: String): Long? =
                item.get(name)?.takeUnless { it.isJsonNull }?.asLong
            val apkUrl = optionalString("apkUrl") ?: continue
            val apkSha256 = optionalString("apkSha256") ?: continue
            parsedAssets[abi] = ReleaseAsset(
                apkUrl = apkUrl,
                apkSha256 = apkSha256,
                patchUrl = optionalString("patchUrl"),
                patchSha256 = optionalString("patchSha256"),
                baseVersionCode = optionalLong("baseVersionCode"),
                baseApkSha256 = optionalString("baseApkSha256")
            )
        }
        if (parsedAssets.isEmpty()) throw IOException("Manifest không có APK hợp lệ")
        return UpdateManifest(
            versionCode = requiredLong("versionCode"),
            versionName = requiredString("versionName"),
            releaseNotes = root.get("releaseNotes")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            assets = parsedAssets
        )
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

        val release = JsonParser.parseString(releaseJson).asJsonObject
        val assets = release.getAsJsonArray("assets")
            ?: throw IOException("Bản phát hành không có danh sách tệp")
        val asset = assets.firstOrNull {
            it.asJsonObject.get("name")?.asString == fileName
        }?.asJsonObject ?: throw IOException("Không tìm thấy tệp $fileName trong bản phát hành")
        val assetId = asset.get("id")?.asLong
            ?: throw IOException("GitHub không trả mã tệp $fileName")
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
