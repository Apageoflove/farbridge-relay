// 验证设备心跳上报始终满足服务端的电量范围约束。
package com.zj.phonemirror.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 覆盖系统电池接口的异常值与正常边界。 */
class DeviceStatusUtilsTest {
    /** 系统暂不可用时常返回 -1，必须降为可接受的零值。 */
    @Test fun `未知电量归一化为零`() {
        assertEquals(0, DeviceStatusUtils.normalizeBatteryPercent(-1))
    }

    /** 防止厂商 ROM 返回越界值破坏心跳请求。 */
    @Test fun `电量始终限制在零到一百`() {
        assertEquals(0, DeviceStatusUtils.normalizeBatteryPercent(-20))
        assertEquals(73, DeviceStatusUtils.normalizeBatteryPercent(73))
        assertEquals(100, DeviceStatusUtils.normalizeBatteryPercent(140))
    }
}
