// 将 SecretStore 的可变字节副本安全转换为密码框文本，并立即清除原数组。
package com.zj.phonemirror.ui

/** 集中约束已保存设备密钥的短暂展示生命周期。 */
object SecretPresentationPolicy {
    /** 解码供密码框显示后无论成功与否都原位清零可变字节。 */
    fun decodeAndClear(secret: ByteArray?): String {
        if (secret == null) return ""
        return try {
            secret.toString(Charsets.UTF_8)
        } finally {
            secret.fill(0)
        }
    }

    /** 将文本短暂编码给 Keystore 写入，写入完成或抛错后都立即清零字节。 */
    inline fun useEncoded(value: String, action: (ByteArray) -> Unit) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            action(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /** 计算 UTF-8 长度时也不遗留用于校验的临时字节。 */
    fun encodedSize(value: String): Int {
        var size = 0
        useEncoded(value) { size = it.size }
        return size
    }
}
