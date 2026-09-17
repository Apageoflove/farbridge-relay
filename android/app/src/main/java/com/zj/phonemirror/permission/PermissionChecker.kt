// 实际检查 SMS、CallLog、PhoneState 与可选 Contacts 权限。
package com.zj.phonemirror.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** 不缓存权限结果，避免角色/权限撤销后继续误报正常。 */
class PermissionChecker(private val context: Context) {
    /** 每次读取 PackageManager 当前授权状态。 */
    fun checkAll(): List<PermissionState> = listOf(
        state(Manifest.permission.RECEIVE_SMS, true),
        state(Manifest.permission.READ_SMS, true),
        state(Manifest.permission.READ_CALL_LOG, true),
        state(Manifest.permission.READ_PHONE_STATE, true),
        state(Manifest.permission.READ_CONTACTS, false),
    )

    /** 关键权限任何一项缺失即进入 DEGRADED。 */
    fun requiredGranted(): Boolean = checkAll().filter { it.required }.all { it.granted }

    /** 查询单项权限。 */
    private fun state(permission: String, required: Boolean) = PermissionState(
        permission,
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        required,
    )
}
