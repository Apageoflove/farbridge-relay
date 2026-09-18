// 为诊断界面生成不含正文、OTP 和完整号码的摘要。
package com.zj.phonemirror.util

import com.zj.phonemirror.parser.VerificationCodeParser

/** 统一打码 UI 和日志里的敏感字段，避免调用方自行拼接。 */
object PrivacyMasker {
    /** 保留长号码前三后四位，短号码只保留首尾。 */
    fun maskAddress(value: String?): String {
        val raw = value.orEmpty()
        return when {
            raw.length >= 8 -> raw.take(3) + "*".repeat(raw.length - 7) + raw.takeLast(4)
            raw.length >= 3 -> raw.take(1) + "*".repeat(raw.length - 2) + raw.takeLast(1)
            raw.isNotEmpty() -> "*".repeat(raw.length)
            else -> "未知号码"
        }
    }

    /** 生成只含来源掩码、OTP 存在性和字符数的短信诊断行。 */
    fun smsDiagnostic(address: String?, body: String): String =
        "来自 ${maskAddress(address)} · ${if (VerificationCodeParser.parse(body) != null) "含验证码" else "普通短信"} · ${body.length}字"

    /** 生成不含联系人全名和完整号码的通话诊断行。 */
    fun callDiagnostic(number: String?, callType: String, duration: Long): String =
        "${maskAddress(number)} · $callType · ${duration}秒"
}
