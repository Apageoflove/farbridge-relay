// 验证手动同步的结束策略和用户可理解状态，防止按钮假成功或无限重试。
package com.zj.phonemirror.worker

import org.junit.Assert.assertEquals
import org.junit.Test

/** 手动同步策略的纯 Kotlin 回归测试。 */
class ManualSyncPolicyTest {
    /** 手动任务失败应结束并反馈，后台任务仍保持可恢复重试。 */
    @Test
    fun manualFailureFinishesWhileBackgroundFailureRetries() {
        assertEquals(
            ManualSyncPolicy.FailureDisposition.FINISH,
            ManualSyncPolicy.failureDisposition(isManual = true),
        )
        assertEquals(
            ManualSyncPolicy.FailureDisposition.RETRY,
            ManualSyncPolicy.failureDisposition(isManual = false),
        )
    }

    /** 认证和网络失败只能提示笼统的中文原因，不能把异常正文暴露到界面。 */
    @Test
    fun failureMessagesAreActionableAndSanitized() {
        assertEquals(
            "服务器拒绝认证，请检查设备 ID 和设备密钥",
            ManualSyncPolicy.statusMessage(
                listOf(ManualSyncPolicy.Stage(ManualSyncPolicy.StageState.FAILED, "http_401")),
            ),
        )
        assertEquals(
            "网络或证书连接失败，请检查网络和服务器地址",
            ManualSyncPolicy.statusMessage(
                listOf(ManualSyncPolicy.Stage(ManualSyncPolicy.StageState.FAILED, "network_or_tls_failure")),
            ),
        )
    }

    /** 只有本轮全部阶段成功才允许向用户报告完成。 */
    @Test
    fun completionRequiresEveryStageToSucceed() {
        assertEquals(
            "正在同步，请保持网络畅通…",
            ManualSyncPolicy.statusMessage(
                listOf(
                    ManualSyncPolicy.Stage(ManualSyncPolicy.StageState.SUCCEEDED),
                    ManualSyncPolicy.Stage(ManualSyncPolicy.StageState.RUNNING),
                ),
            ),
        )
        assertEquals(
            "服务器连接成功，同步已完成",
            ManualSyncPolicy.statusMessage(
                List(4) { ManualSyncPolicy.Stage(ManualSyncPolicy.StageState.SUCCEEDED) },
            ),
        )
    }
}
