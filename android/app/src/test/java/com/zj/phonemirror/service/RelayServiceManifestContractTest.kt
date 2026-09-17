// 解析 AndroidManifest，验证短信中继前台服务类型和权限声明。
package com.zj.phonemirror.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止误用受六小时限制的 dataSync 类型或遗漏专用权限。 */
class RelayServiceManifestContractTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    @Test fun `清单声明 remoteMessaging 服务及权限`() {
        val permissions = document.getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).mapNotNull { index ->
                nodes.item(index).attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue
            }
        }
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING" in permissions)

        val services = document.getElementsByTagName("service")
        val relay = (0 until services.length).map { services.item(it) }.single { node ->
            node.attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue == ".service.RelayForegroundService"
        }
        assertEquals("remoteMessaging", relay.attributes.getNamedItemNS(androidNamespace, "foregroundServiceType")?.nodeValue)
        assertEquals("false", relay.attributes.getNamedItemNS(androidNamespace, "exported")?.nodeValue)
    }
}
