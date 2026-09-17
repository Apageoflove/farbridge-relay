// 验证可靠常驻模式的 CPU 唤醒锁只在有效配置生命周期内持有。
package com.zj.phonemirror.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** 防止重复 acquire、漏 release 或未配置时错误耗电。 */
class RelayWakeLockControllerTest {
    @Test fun `有效配置幂等持有且销毁释放`() {
        var acquired = 0
        var released = 0
        val controller = RelayWakeLockController(
            acquireAction = { acquired++ },
            releaseAction = { released++ },
        )

        controller.start(configured = true)
        controller.start(configured = true)
        controller.stop()
        controller.stop()

        assertEquals(1, acquired)
        assertEquals(1, released)
    }

    @Test fun `未配置不持有唤醒锁`() {
        var acquired = 0
        var released = 0
        val controller = RelayWakeLockController({ acquired++ }, { released++ })

        controller.start(configured = false)
        controller.stop()

        assertEquals(0, acquired)
        assertEquals(0, released)
    }
}
