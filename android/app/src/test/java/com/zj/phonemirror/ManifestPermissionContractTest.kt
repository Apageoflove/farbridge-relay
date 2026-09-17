// 验证 Android 清单声明运行时状态采集所需的非敏感网络权限。
package com.zj.phonemirror

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 防止心跳在读取 ConnectivityManager 时因清单权限回归而提前失败。 */
class ManifestPermissionContractTest {
    /** 心跳读取网络类型前必须声明 ACCESS_NETWORK_STATE。 */
    @Test
    fun manifestDeclaresAccessNetworkState() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)
        val permissionNames = document.getElementsByTagName("uses-permission")
            .let { nodes ->
                (0 until nodes.length).map { index ->
                    nodes.item(index).attributes
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        ?.nodeValue
                }
            }

        assertTrue(
            "AndroidManifest.xml must declare android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_NETWORK_STATE" in permissionNames,
        )
    }
}
