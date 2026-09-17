// 定义前台中继的短信主动探测周期。
package com.zj.phonemirror.service

/** 将 OEM 无关的实时探测参数集中为可单测纯策略。 */
object RealtimePollingPolicy {
    const val smsProbeIntervalMillis = 3_000L
}
