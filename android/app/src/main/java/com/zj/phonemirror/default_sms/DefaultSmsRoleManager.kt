// 检查并申请默认短信角色；未启用时保持普通镜像模式。
package com.zj.phonemirror.default_sms
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import com.zj.phonemirror.settings.SettingsStore
/** 角色只在用户显式授权后生效，丢失立即降级上报。 */
class DefaultSmsRoleManager(private val context: Context) {
    /** 当前设备是否支持 RoleManager 的 SMS 角色。 */
    fun eligible(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    /** 是否实际持有系统默认短信角色。 */
    fun roleHeld(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
    /** 结合用户开关解析当前工作模式。 */
    fun mode(): SmsMode = DefaultSmsPolicy.resolve(eligible(), SettingsStore(context).defaultSmsEnabled, roleHeld())
    /** 发起可逆的系统角色申请对话框。 */
    fun requestRole(activity: Activity): Boolean {
        if (!eligible()) return false
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching {
            activity.startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            true
        }.getOrDefault(false)
    }
    /** 旧设备回退：打开系统默认应用设置页。 */
    fun openDefaultAppSettings(activity: Activity) {
        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }
}
