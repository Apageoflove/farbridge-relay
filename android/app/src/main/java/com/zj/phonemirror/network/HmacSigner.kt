// 实现与 FastAPI 服务端完全一致的 HMAC-SHA256 canonical 契约。
package com.zj.phonemirror.network

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 对请求方法、路径、时间、nonce 和原始 JSON 字节签名。 */
object HmacSigner {
    /** 生成服务器约定的五行 canonical 字符串。 */
    fun canonical(method: String, path: String, timestamp: String, nonce: String, body: ByteArray): String {
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(body).toHex()
        return "${method.uppercase()}\n$path\n$timestamp\n$nonce\n$bodyHash"
    }

    /** 使用原始设备 secret 生成小写十六进制签名。 */
    fun sign(secret: ByteArray, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).toHex()
    }

    /** 将字节稳定编码为小写十六进制。 */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
