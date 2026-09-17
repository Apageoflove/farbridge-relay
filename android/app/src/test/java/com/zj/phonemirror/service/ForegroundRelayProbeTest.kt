// 验证前台直读同时覆盖短信与通话，并只在有真实变化时调度一次上传。
package com.zj.phonemirror.service

import com.zj.phonemirror.repository.ReconcileOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定 OEM 观察器失效时，双 Provider 仍由前台循环直接收敛的行为。 */
class ForegroundRelayProbeTest {
    @Test fun `短信 Provider 短暂异常时仍继续读取通话`() = runTest {
        var callReads = 0
        var uploads = 0
        val probe = ForegroundRelayProbe(
            reconcileSms = { error("sms_provider_unavailable") },
            reconcileCalls = { callReads++; ReconcileOutcome.Applied(1) },
            directSync = { uploads++; DirectSyncOutcome.Completed },
            enqueueFallback = {},
        )

        probe.probe()

        assertEquals(1, callReads)
        assertEquals(1, uploads)
    }

    @Test fun `每轮同时收敛短信和通话`() = runTest {
        var smsReads = 0
        var callReads = 0
        var uploads = 0
        val probe = ForegroundRelayProbe(
            reconcileSms = { smsReads++; ReconcileOutcome.Applied(0) },
            reconcileCalls = { callReads++; ReconcileOutcome.Applied(0) },
            directSync = { uploads++; DirectSyncOutcome.Completed },
            enqueueFallback = {},
        )

        probe.probe()

        assertEquals(1, smsReads)
        assertEquals(1, callReads)
        assertEquals(0, uploads)
    }

    @Test fun `任一 Provider 有变化只调度一次上传`() = runTest {
        val outcomes = listOf(
            ReconcileOutcome.Applied(0) to ReconcileOutcome.Applied(0),
            ReconcileOutcome.Applied(1) to ReconcileOutcome.Applied(0),
            ReconcileOutcome.Applied(0) to ReconcileOutcome.Applied(2),
            ReconcileOutcome.Applied(1) to ReconcileOutcome.Applied(2),
            ReconcileOutcome.Skipped("sms_query_failed") to ReconcileOutcome.Skipped("call_query_failed"),
        )
        val expectedUploads = listOf(0, 1, 1, 1, 0)

        outcomes.forEachIndexed { index, (smsOutcome, callOutcome) ->
            var uploads = 0
            val probe = ForegroundRelayProbe(
                reconcileSms = { smsOutcome },
                reconcileCalls = { callOutcome },
                directSync = { uploads++; DirectSyncOutcome.Completed },
                enqueueFallback = {},
            )

            probe.probe()

            assertEquals("case $index", expectedUploads[index], uploads)
        }
    }
}
