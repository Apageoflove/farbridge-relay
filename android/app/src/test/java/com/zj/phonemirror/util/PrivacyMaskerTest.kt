// 验证诊断输出不会泄露完整号码、正文或验证码。
package com.zj.phonemirror.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivacyMaskerTest {
    @Test fun `手机号码仅保留前三后四位`() {
        assertEquals("138****8000", PrivacyMasker.maskAddress("13800138000"))
    }

    @Test fun `短服务号码至少遮蔽中间字符`() {
        assertEquals("1***6", PrivacyMasker.maskAddress("10086"))
    }

    @Test fun `短信摘要不包含正文和验证码`() {
        val value = PrivacyMasker.smsDiagnostic("10086", "您的验证码是 382617")
        assertFalse(value.contains("您的验证码是"))
        assertFalse(value.contains("382617"))
        assertEquals("来自 1***6 · 含验证码 · 13字", value)
    }
}
