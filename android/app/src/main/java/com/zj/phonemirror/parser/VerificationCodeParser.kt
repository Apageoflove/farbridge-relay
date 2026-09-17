// 从具有验证码语境的短信中保守提取 4 至 8 位 OTP。
package com.zj.phonemirror.parser

/** 提供低误报的验证码解析。 */
object VerificationCodeParser {
    private val context = Regex("(?i)(验证码|校验码|动态码|otp|verification\\s*code|security\\s*code|passcode)")
    private val code = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

    /** 仅在正文存在明确 OTP 语境时返回最邻近的数字片段。 */
    fun parse(body: String): String? {
        val marker = context.find(body) ?: return null
        return code.findAll(body)
            .minByOrNull { kotlin.math.abs(it.range.first - marker.range.last) }
            ?.groupValues?.get(1)
    }
}
