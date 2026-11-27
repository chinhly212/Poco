# Keep generic signatures and runtime annotations used by Gson, Retrofit and Kotlin.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# WorkManager persists worker class names and recreates workers after process death/reboot.
-keep class com.contentautomation.work.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserve the JNI entry point: its Java class and method names must match the native symbol.
-keep class com.cundong.utils.PatchUtils {
    public static native int patch(java.lang.String, java.lang.String, java.lang.String);
}

# Gson reflects over these DTO fields; retain their field names for JSON compatibility.
-keepclassmembers class com.contentautomation.data.network.ProcessVideoRequest {
    <fields>;
}
-keepclassmembers class com.contentautomation.data.network.ApiResponse {
    <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Enum names are persisted in the local Room database and must remain stable across updates.
-keepclassmembers enum com.contentautomation.data.db.entity.** {
    *;
}

# Retrofit creates API implementations dynamically and reads method/parameter annotations.
-keep interface com.contentautomation.data.network.AutomationApi {
    @retrofit2.http.* <methods>;
}
