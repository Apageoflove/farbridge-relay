// 验证 Android 与服务器共享的 HMAC canonical 字节契约。
package com.zj.phonemirror.network

import org.junit.Assert.assertEquals
import org.junit.Test

class HmacSignerTest {
    @Test fun `canonical 和签名匹配固定向量`() {
        val canonical = HmacSigner.canonical("POST", "/api/v1/sync/events", "1700000000", "nonce-1", "{\"a\":1}".toByteArray())
        assertEquals("POST\n/api/v1/sync/events\n1700000000\nnonce-1\n015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862", canonical)
        assertEquals("2219819549214ae98720cb3ff41ab41bee9023fb167911d094192c654f1dc568", HmacSigner.sign("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".toByteArray(), canonical))
    }
}
