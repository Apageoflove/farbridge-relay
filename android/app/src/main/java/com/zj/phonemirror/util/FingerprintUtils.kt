// 生成 SMS/Call 内容变化检测用的稳定 SHA-256 指纹。
package com.zj.phonemirror.util

import java.security.MessageDigest

/** 指纹仅用于变更检测，不用于身份认证。 */
object FingerprintUtils {
    /** 使用长度前缀消除字段拼接歧义后计算 SHA-256。 */
    fun of(vararg fields: Any?): String {
        val canonical = fields.joinToString("|") { value ->
            val text = value?.toString().orEmpty()
            "${text.length}:$text"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
