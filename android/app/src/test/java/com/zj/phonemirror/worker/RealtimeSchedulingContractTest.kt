// 检查实时探测和上传任务保持唯一、加急且可降级执行。
package com.zj.phonemirror.worker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用源码合同防止前台直读与变化后加急上传在重构时回退。 */
class RealtimeSchedulingContractTest {
    private val scheduler = File("src/main/java/com/zj/phonemirror/worker/WorkerScheduler.kt").readText()
    private val relay = File("src/main/java/com/zj/phonemirror/service/RelayForegroundService.kt").readText()
    private val probe = File("src/main/java/com/zj/phonemirror/service/ForegroundRelayProbe.kt").readText()

    @Test fun `前台服务直接读取并只在真实变化后上传`() {
        assertTrue(relay.contains("SmsProviderReader"))
        assertTrue(relay.contains("CallLogProviderReader"))
        assertTrue(relay.contains("MirrorRepository"))
        assertTrue(relay.contains("ForegroundRelayProbe"))
        assertTrue(relay.contains("OutboxSyncRunner"))
        assertTrue(relay.contains("directSync"))
        assertTrue(relay.contains("WorkerScheduler.enqueueSync"))
        assertTrue(relay.contains("RealtimeSmsProbeLoop"))
        assertTrue(probe.contains("!smsOutcome.hasChanges() && !callOutcome.hasChanges()"))
        assertTrue(!scheduler.contains("fun enqueueSmsProbe"))
    }

    @Test fun `上传任务使用加急降级`() {
        val syncBlock = scheduler.substringAfter("fun enqueueSync").substringBefore("fun enqueueHeartbeat")
        assertTrue(syncBlock.contains("setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)"))
    }

    @Test fun `前台服务启动单循环并在销毁时取消`() {
        assertTrue(relay.contains("providerProbeLoop.start()"))
        assertTrue(relay.contains("providerProbeLoop.stop()"))
        assertTrue(relay.contains("serviceScope.cancel()"))
        assertTrue(relay.contains("override fun onDestroy"))
    }
}
