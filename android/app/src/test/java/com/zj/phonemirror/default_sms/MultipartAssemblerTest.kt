// 验证 multipart 短信按序合并且缺段时拒绝写入。
package com.zj.phonemirror.default_sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultipartAssemblerTest {
    @Test fun `完整分段按序组合`() {
        assertEquals("验证码 123456", MultipartAssembler.assemble(listOf(Part(1, 2, "123456"), Part(0, 2, "验证码 "))))
    }

    @Test fun `缺少分段返回失败`() {
        assertNull(MultipartAssembler.assemble(listOf(Part(0, 2, "验证码 "))))
    }
}
