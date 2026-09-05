package com.pdfmaster.app.data.remote

import com.pdfmaster.app.data.remote.dto.CreateJobRequest
import com.pdfmaster.app.data.remote.dto.JobEnvelope
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

interface CloudConvertApi {

    @POST("v2/jobs")
    suspend fun createJob(@Body request: CreateJobRequest): JobEnvelope

    @GET("v2/jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobEnvelope

    /**
     * CloudConvert's import/upload task hands back a presigned form POST URL that
     * is NOT on api.cloudconvert.com - AuthInterceptor must not attach the API
     * key to this call (see NetworkModule).
     */
    @Multipart
    @POST
    suspend fun uploadFile(
        @Url url: String,
        @PartMap parameters: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}
