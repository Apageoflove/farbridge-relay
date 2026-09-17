// 定义首页权限自检的无敏感信息结果。
package com.zj.phonemirror.permission

/** 单项 Android runtime permission 实际授权状态。 */
data class PermissionState(val permission: String, val granted: Boolean, val required: Boolean)
