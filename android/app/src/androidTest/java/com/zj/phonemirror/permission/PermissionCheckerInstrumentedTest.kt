// 在真实 Android 权限模型上验证权限自检结果不把拒绝误报为已授权。
package com.zj.phonemirror.permission

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionCheckerInstrumentedTest {
    @Test fun `未授予的受限权限显示 denied`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val states = PermissionChecker(context).checkAll()
        assertEquals(false, states.first { it.permission == Manifest.permission.READ_SMS }.granted)
    }
}
