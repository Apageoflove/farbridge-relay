// 创建拒绝生产明文 HTTP、无正文日志且逐请求 HMAC 签名的 Retrofit 客户端。
package com.zj.phonemirror.network

import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.settings.ServerUrlValidator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 把 URL 校验和签名密钥读取放在网络建立之前。 */
object ApiClient {
    /** 仅对已验证根地址创建客户端；永不安装 body logging interceptor。 */
    fun create(baseUrl: String, deviceId: String, secretStore: SecretStore, allowDebugLoopback: Boolean): ApiService {
        require(ServerUrlValidator.isAllowed(baseUrl, allowDebugLoopback)) { "server_url_rejected" }
        val normalized = baseUrl.trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(SigningInterceptor(deviceId, secretStore))
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(ApiMoshiFactory.create()))
            .build()
            .create(ApiService::class.java)
    }
}

/** 按实际发送的原始 JSON 字节构造签名，避免序列化前后不一致。 */
private class SigningInterceptor(private val deviceId: String, private val secretStore: SecretStore) : Interceptor {
    /** 添加设备、时间、nonce 与 HMAC 头；不记录任何正文或 secret。 */
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val bodyBytes = Buffer().use { buffer ->
            request.body?.writeTo(buffer)
            buffer.readByteArray()
        }
        val secret = secretStore.get() ?: throw IOException("device_secret_unavailable")
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val canonical = HmacSigner.canonical(request.method, request.url.encodedPath, timestamp, nonce, bodyBytes)
        val signed = try {
            request.newBuilder()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", HmacSigner.sign(secret, canonical))
                .build()
        } finally {
            secret.fill(0)
        }
        return chain.proceed(signed)
    }
}
