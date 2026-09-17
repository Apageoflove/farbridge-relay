// 验证系统 Provider 的原始字段映射与未知类型回退。
package com.zj.phonemirror.provider

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderMappingTest {
    @Test fun `拒接和屏蔽类型保留语义`() {
        assertEquals("REJECTED", ProviderMappings.callType(CallLog.Calls.REJECTED_TYPE))
        assertEquals("BLOCKED", ProviderMappings.callType(CallLog.Calls.BLOCKED_TYPE))
    }

    @Test fun `未知通话类型安全回退`() {
        assertEquals("UNKNOWN", ProviderMappings.callType(9999))
    }
}
