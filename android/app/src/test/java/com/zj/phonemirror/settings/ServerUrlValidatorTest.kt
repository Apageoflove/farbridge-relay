// 验证生产服务器地址拒绝明文 HTTP 与非根路径配置。
package com.zj.phonemirror.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlValidatorTest {
    @Test fun `生产环境只接受无凭据 HTTPS 根地址`() {
        assertTrue(ServerUrlValidator.isAllowed("https://phone.example.com", false))
        assertFalse(ServerUrlValidator.isAllowed("http://phone.example.com", false))
        assertFalse(ServerUrlValidator.isAllowed("https://user:pass@phone.example.com", false))
        assertFalse(ServerUrlValidator.isAllowed("https://phone.example.com/api", false))
    }

    @Test fun `调试环境仅额外允许 loopback HTTP`() {
        assertTrue(ServerUrlValidator.isAllowed("http://127.0.0.1:8084", true))
        assertFalse(ServerUrlValidator.isAllowed("http://192.168.1.2:8084", true))
    }
}
