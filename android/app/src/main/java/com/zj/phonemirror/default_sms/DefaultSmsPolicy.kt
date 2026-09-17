// 决定普通镜像、默认短信和角色丢失降级状态。
package com.zj.phonemirror.default_sms

/** 用户可见的 SMS 工作模式。 */
enum class SmsMode { MIRROR, DEFAULT_SMS, DEGRADED }

/** 默认短信角色必须由设备支持、用户显式启用且当前实际持有。 */
object DefaultSmsPolicy {
    /** 角色丢失时返回 DEGRADED，绝不假装 OTP 仍然即时。 */
    fun resolve(eligible: Boolean, userEnabled: Boolean, roleHeld: Boolean): SmsMode = when {
        userEnabled && (!eligible || !roleHeld) -> SmsMode.DEGRADED
        userEnabled && roleHeld -> SmsMode.DEFAULT_SMS
        else -> SmsMode.MIRROR
    }
}
