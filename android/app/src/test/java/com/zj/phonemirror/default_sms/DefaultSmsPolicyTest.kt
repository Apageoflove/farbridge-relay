// 验证默认短信角色必须显式授权且角色丢失时降级。
package com.zj.phonemirror.default_sms

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSmsPolicyTest {
    @Test fun `未显式请求时保持普通镜像模式`() {
        assertEquals(SmsMode.MIRROR, DefaultSmsPolicy.resolve(true, false, false))
    }

    @Test fun `用户启用且持有角色时进入默认短信模式`() {
        assertEquals(SmsMode.DEFAULT_SMS, DefaultSmsPolicy.resolve(true, true, true))
    }

    @Test fun `用户启用但角色丢失时状态降级`() {
        assertEquals(SmsMode.DEGRADED, DefaultSmsPolicy.resolve(true, true, false))
    }
}
