// 定义短信广播后的 Provider 落盘补读延迟与去重策略。
package com.zj.phonemirror.worker

import androidx.work.ExistingWorkPolicy

/** 避免一加系统先发广播、后写入 SMS Provider 时读取到旧快照。 */
object SmsProviderCommitPolicy {
    /** 兼顾系统短信库提交时间与验证码到达实时性的短延迟。 */
    const val delayMillis = 2_500L

    /** 同一时段多段短信或重复广播只保留最后一次补读。 */
    val existingWorkPolicy = ExistingWorkPolicy.REPLACE
}
