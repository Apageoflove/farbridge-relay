// 定义 Provider 实时变化的唯一任务尾随策略。
package com.zj.phonemirror.worker

import androidx.work.ExistingWorkPolicy

/** 确保运行期间再次到达的短信或通话变化不会被 KEEP 静默丢弃。 */
object RealtimeTriggerPolicy {
    /** 可触发增量收敛的系统 Provider。 */
    enum class Provider { SMS, CALL_LOG }

    /** 两类 Provider 都追加尾随任务，失败链则由 WorkManager 自动替换。 */
    fun existingWorkPolicy(provider: Provider): ExistingWorkPolicy = when (provider) {
        Provider.SMS, Provider.CALL_LOG -> ExistingWorkPolicy.APPEND_OR_REPLACE
    }
}
