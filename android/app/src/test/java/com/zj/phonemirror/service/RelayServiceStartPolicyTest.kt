// 验证常驻中继只由用户可见入口在配置完整时启动。
package com.zj.phonemirror.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定后台广播不得直接启动前台服务的 Android 合规边界。 */
class RelayServiceStartPolicyTest {
    @Test fun `可见应用且配置完整才启动中继服务`() {
        assertTrue(RelayServiceStartPolicy.shouldStart(userVisible = true, configured = true))
        assertFalse(RelayServiceStartPolicy.shouldStart(userVisible = true, configured = false))
        assertFalse(RelayServiceStartPolicy.shouldStart(userVisible = false, configured = true))
    }
}
