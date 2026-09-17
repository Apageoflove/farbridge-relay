// 验证前台短信探测的固定周期和单循环生命周期策略。
package com.zj.phonemirror.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定验证码场景使用的三秒探测周期。 */
class RealtimePollingPolicyTest {
    @Test fun `短信探测固定为三秒`() {
        assertEquals(3_000L, RealtimePollingPolicy.smsProbeIntervalMillis)
    }
}
