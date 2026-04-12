package dev.ligustah.lsnav

import dev.ligustah.lsnav.api.generated.apis.NavigationApi
import dev.ligustah.lsnav.api.generated.apis.ScootersApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class ApiClientProvider(private val baseUrl: String, private val token: String?) {
    
    private val okHttpClient: OkHttpClient by lazy {
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${token.trim()}")
            }
            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private fun formatBaseUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/api/v1")) {
            trimmed
        } else if (trimmed.endsWith("/api/v1/")) {
            trimmed.removeSuffix("/")
        } else {
            "${trimmed.removeSuffix("/")}/api/v1"
        }
    }

    fun getNavigationApi(): NavigationApi {
        return NavigationApi(basePath = formatBaseUrl(baseUrl), client = okHttpClient)
    }

    fun getScootersApi(): ScootersApi {
        return ScootersApi(basePath = formatBaseUrl(baseUrl), client = okHttpClient)
    }
}
