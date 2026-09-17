// 验证前台短信探测严格串行、启动幂等且停止后不再运行。
package com.zj.phonemirror.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定三秒探测循环的生命周期，避免任务堆积或重复循环。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSmsProbeLoopTest {
    @Test fun `立即探测且每轮完成三秒后再执行下一轮`() = runTest {
        var calls = 0
        val loop = RealtimeSmsProbeLoop(this, 3_000L) { calls++ }

        loop.start()
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, calls)
        loop.stop()
    }

    @Test fun `重复启动不叠加且停止后不再探测`() = runTest {
        var calls = 0
        val loop = RealtimeSmsProbeLoop(this, 3_000L) { calls++ }

        loop.start()
        loop.start()
        runCurrent()
        assertEquals(1, calls)
        loop.stop()
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(1, calls)
    }
}
