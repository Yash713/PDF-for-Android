package com.pdfmaster.app.di

import com.pdfmaster.app.BuildConfig
import com.pdfmaster.app.data.remote.CloudConvertApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val CLOUDCONVERT_BASE_URL = "https://api.cloudconvert.com/"
private const val CLOUDCONVERT_HOST = "api.cloudconvert.com"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Only attaches the API key when talking to CloudConvert itself - the
     * presigned upload/download URLs CloudConvert hands back point at a
     * different host and would reject (or ignore) a stray Authorization header.
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val request = if (original.url.host == CLOUDCONVERT_HOST && BuildConfig.CLOUDCONVERT_API_KEY.isNotBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.CLOUDCONVERT_API_KEY}")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(CLOUDCONVERT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideCloudConvertApi(retrofit: Retrofit): CloudConvertApi =
        retrofit.create(CloudConvertApi::class.java)
}
