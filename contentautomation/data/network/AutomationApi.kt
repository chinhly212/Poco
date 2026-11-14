package com.contentautomation.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class ProcessVideoRequest(val videoUrl: String, val platform: String)
data class ApiResponse(val success: Boolean, val message: String)

interface AutomationApi {
    // App gửi link video lên Server, Server sẽ tự tải, xử lý FFmpeg, dịch và đăng
    @POST("/api/v1/process-video")
    suspend fun processVideo(@Body request: ProcessVideoRequest): Response<ApiResponse>

    companion object {
        // ĐÂY LÀ NƠI BẠN SẼ ĐIỀN LINK SERVER SAU NÀY
        private const val BASE_URL = "https://api.yourdomain.com/"
        
        fun create(): AutomationApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AutomationApi::class.java)
        }
    }
}
