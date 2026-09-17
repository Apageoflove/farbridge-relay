// 验证已保存设备密钥可回填到密码框，并及时清除读取出的可变字节副本。
package com.zj.phonemirror.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设备密钥展示边界的纯 Kotlin 回归测试。 */
class SecretPresentationPolicyTest {
    /** 解码供密码框显示后必须原位清零 SecretStore 返回的 ByteArray。 */
    @Test
    fun decodeSavedSecretClearsMutableBytes() {
        val bytes = "saved-device-secret".toByteArray(Charsets.UTF_8)
        assertEquals("saved-device-secret", SecretPresentationPolicy.decodeAndClear(bytes))
        assertTrue(bytes.all { it == 0.toByte() })
    }

    /** 尚未保存密钥时密码框保持空值。 */
    @Test
    fun missingSecretProducesEmptyInput() {
        assertEquals("", SecretPresentationPolicy.decodeAndClear(null))
    }

    /** 写入 Keystore 后必须清零由密码字符串创建的临时 UTF-8 字节。 */
    @Test
    fun useEncodedSecretClearsBytesAfterStore() {
        lateinit var captured: ByteArray
        SecretPresentationPolicy.useEncoded("saved-device-secret") { bytes ->
            assertEquals("saved-device-secret", bytes.toString(Charsets.UTF_8))
            captured = bytes
        }
        assertTrue(captured.all { it == 0.toByte() })
    }
}
