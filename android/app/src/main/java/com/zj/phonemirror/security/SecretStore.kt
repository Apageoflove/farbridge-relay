// 使用 Android Keystore 包装保存设备 HMAC secret，禁止明文 SharedPreferences。
package com.zj.phonemirror.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 将设备 secret 以 AES-GCM 密文保存在应用私有存储。 */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_device", Context.MODE_PRIVATE)
    private val alias = "phone_mirror_device_wrapper_v1"

    /** 加密并原子覆盖设备 secret；不接受空 secret。 */
    fun put(secret: ByteArray) {
        require(secret.size >= 32) { "device_secret_too_short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(secret), Base64.NO_WRAP)
        check(preferences.edit().putString("wrapped", encoded).commit()) { "secret_store_write_failed" }
    }

    /** 解密读取设备 secret；损坏或密钥失效时返回 null 并要求重新配置。 */
    fun get(): ByteArray? = runCatching {
        val encoded = preferences.getString("wrapped", null) ?: return null
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        require(all.size > 12) { "secret_ciphertext_invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        cipher.doFinal(all.copyOfRange(12, all.size))
    }.getOrNull()

    /** 仅检查密钥是否存在，并立即清零为检查而解密出的临时字节。 */
    fun hasSecret(): Boolean {
        val secret = get() ?: return false
        secret.fill(0)
        return true
    }

    /** 用户撤销配置时同时删除密文。 */
    fun clear() { preferences.edit().remove("wrapped").apply() }

    /** 获取或首次创建仅限本应用使用的不可导出 AES 密钥。 */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
