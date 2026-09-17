// 读取心跳所需的电池与网络状态，不接触短信正文或号码。
package com.zj.phonemirror.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/** 聚合 Android 系统健康状态供 HeartbeatWorker 使用。 */
object DeviceStatusUtils {
    /** 返回 0..100 电量，系统暂不可用时按零处理以满足服务端契约。 */
    fun batteryPercent(context: Context): Int = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        .let(::normalizeBatteryPercent)

    /** 把厂商 ROM 偶发的未知或越界电量限制在协议允许范围内。 */
    fun normalizeBatteryPercent(rawPercent: Int): Int = rawPercent.coerceIn(0, 100)

    /** 读取当前是否正在充电。 */
    fun charging(context: Context): Boolean = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging

    /** 映射当前主网络类型。 */
    fun networkType(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
    }
}
