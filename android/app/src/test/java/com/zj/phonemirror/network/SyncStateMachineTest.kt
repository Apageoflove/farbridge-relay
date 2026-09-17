// 验证 partial ACK 与模糊网络失败不会丢失 Outbox 事件。
package com.zj.phonemirror.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStateMachineTest {
    @Test fun `只确认服务器明确返回的事件`() {
        val result = SyncStateMachine.afterResponse(listOf("a", "b", "c"), setOf("a", "c"))
        assertEquals(setOf("a", "c"), result.acked)
        assertEquals(setOf("b"), result.retry)
    }

    @Test fun `提交后超时使整批回到重试状态`() {
        val result = SyncStateMachine.afterAmbiguousFailure(listOf("a", "b"))
        assertEquals(emptySet<String>(), result.acked)
        assertEquals(setOf("a", "b"), result.retry)
    }

    @Test fun `指数退避有上限`() {
        assertEquals(15_000L, SyncStateMachine.backoffMillis(0))
        assertEquals(30_000L, SyncStateMachine.backoffMillis(1))
        assertEquals(3_600_000L, SyncStateMachine.backoffMillis(20))
    }

    @Test fun `进程中断后的发送租约十分钟后可恢复`() {
        assertEquals(400_000L, SyncStateMachine.abandonedLeaseBefore(1_000_000L))
    }
}
