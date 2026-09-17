// 验证验证码解析只接受具有 OTP 语境的独立数字片段。
package com.zj.phonemirror.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeParserTest {
    @Test fun `中文验证码提取六位数字`() {
        assertEquals("382617", VerificationCodeParser.parse("【服务】您的验证码是 382617，5分钟内有效"))
    }

    @Test fun `英文 OTP 提取八位数字`() {
        assertEquals("87654321", VerificationCodeParser.parse("Your OTP is 87654321. Do not share it."))
    }

    @Test fun `普通订单号不误判为验证码`() {
        assertNull(VerificationCodeParser.parse("订单 123456 已经发货"))
    }

    @Test fun `手机号不截取为验证码`() {
        assertNull(VerificationCodeParser.parse("请联系 13800138000"))
    }
}
