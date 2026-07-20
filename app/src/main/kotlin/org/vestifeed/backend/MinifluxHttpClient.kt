package org.vestifeed.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.vestifeed.BuildConfig
import org.vestifeed.http.tokenAuthInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

fun minifluxHttpClient(token: String): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .addInterceptor(tokenAuthInterceptor(token))
        .addInterceptor(errorInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)

    if (BuildConfig.DEBUG) {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        builder.addInterceptor(loggingInterceptor)
    }

    return builder.build()
}

private fun errorInterceptor(): Interceptor {
    return Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            val bodyString = response.body.string()
            val errorMessage = runCatching {
                val json = Gson().fromJson(bodyString, JsonObject::class.java)
                if (json != null && json.has("error_message")) {
                    json["error_message"].asString
                } else {
                    "Endpoint ${request.url} failed with response code ${response.code}"
                }
            }.getOrElse {
                "Endpoint ${request.url} failed with response code ${response.code}"
            }

            throw IOException(errorMessage)
        }

        response
    }
}
