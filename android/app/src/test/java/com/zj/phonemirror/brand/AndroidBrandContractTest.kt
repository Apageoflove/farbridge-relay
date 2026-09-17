// 验证 Android 品牌名、版本与启动图标资源在发布前保持一致。
package com.zj.phonemirror.brand

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止升级品牌时只改局部 UI，造成桌面名、通知或图标仍沿用旧品牌。 */
class AndroidBrandContractTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    /** 桌面展示名和清单图标必须统一指向远桥的新品牌资源。 */
    @Test
    fun `application uses YuanQiao label and adaptive launcher icons`() {
        val strings = parseXml(File("src/main/res/values/strings.xml"))
        val appName = strings.getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }.single { node ->
                node.attributes.getNamedItem("name")?.nodeValue == "app_name"
            }.textContent.trim()
        }
        assertEquals("远桥", appName)

        val manifest = parseXml(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0)
        assertEquals(
            "@mipmap/ic_launcher",
            application.attributes.getNamedItemNS(androidNamespace, "icon")?.nodeValue,
        )
        assertEquals(
            "@mipmap/ic_launcher_round",
            application.attributes.getNamedItemNS(androidNamespace, "roundIcon")?.nodeValue,
        )

        val adaptiveIcon = parseXml(File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml"))
            .documentElement
        assertEquals("adaptive-icon", adaptiveIcon.nodeName)
        assertEquals(
            "@color/ic_launcher_background",
            adaptiveIcon.getElementsByTagName("background").item(0).attributes
                .getNamedItemNS(androidNamespace, "drawable")?.nodeValue,
        )
        assertEquals(
            "@drawable/ic_launcher_foreground",
            adaptiveIcon.getElementsByTagName("foreground").item(0).attributes
                .getNamedItemNS(androidNamespace, "drawable")?.nodeValue,
        )
        assertTrue(File("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").isFile)
    }

    /** 前景矢量必须忠实使用母版的三段几何与品牌色，并缩入自适应图标安全区。 */
    @Test
    fun `adaptive foreground preserves master geometry and colors`() {
        val foreground = parseXml(File("src/main/res/drawable/ic_launcher_foreground.xml"))
        val vector = foreground.documentElement
        assertEquals("108dp", vector.attributes.getNamedItemNS(androidNamespace, "width")?.nodeValue)
        assertEquals("108dp", vector.attributes.getNamedItemNS(androidNamespace, "height")?.nodeValue)
        assertEquals("1024", vector.attributes.getNamedItemNS(androidNamespace, "viewportWidth")?.nodeValue)
        assertEquals("1024", vector.attributes.getNamedItemNS(androidNamespace, "viewportHeight")?.nodeValue)

        val group = vector.getElementsByTagName("group").item(0)
        assertEquals("512", group.attributes.getNamedItemNS(androidNamespace, "pivotX")?.nodeValue)
        assertEquals("512", group.attributes.getNamedItemNS(androidNamespace, "pivotY")?.nodeValue)
        assertEquals("0.72", group.attributes.getNamedItemNS(androidNamespace, "scaleX")?.nodeValue)
        assertEquals("0.72", group.attributes.getNamedItemNS(androidNamespace, "scaleY")?.nodeValue)

        val paths = vector.getElementsByTagName("path")
        assertEquals(3, paths.length)
        assertEquals("#FFF7E8", paths.item(0).attributes.getNamedItemNS(androidNamespace, "strokeColor")?.nodeValue)
        assertEquals("112", paths.item(0).attributes.getNamedItemNS(androidNamespace, "strokeWidth")?.nodeValue)
        assertEquals("round", paths.item(0).attributes.getNamedItemNS(androidNamespace, "strokeLineCap")?.nodeValue)
        assertEquals("M176,676 C244,478 350,366 458,344", paths.item(0).attributes.getNamedItemNS(androidNamespace, "pathData")?.nodeValue)
        assertEquals("M848,676 C780,478 674,366 566,344", paths.item(1).attributes.getNamedItemNS(androidNamespace, "pathData")?.nodeValue)
        assertEquals("#E8503A", paths.item(2).attributes.getNamedItemNS(androidNamespace, "fillColor")?.nodeValue)
        assertEquals("M458,286 A54,54 0,1 0,566 286 A54,54 0,1 0,458 286 Z", paths.item(2).attributes.getNamedItemNS(androidNamespace, "pathData")?.nodeValue)

        val colors = parseXml(File("src/main/res/values/colors.xml"))
        val background = colors.getElementsByTagName("color").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }.single { node ->
                node.attributes.getNamedItem("name")?.nodeValue == "ic_launcher_background"
            }.textContent.trim()
        }
        assertEquals("#163A70", background)

        assertTrue(File("src/main/res/mipmap-anydpi/ic_launcher.xml").isFile)
        assertTrue(File("src/main/res/mipmap-anydpi/ic_launcher_round.xml").isFile)
    }

    /** 版本只升级发布号，不允许改变包名，以保留覆盖安装、配置和签名兼容性。 */
    @Test
    fun `release 1_0_9 keeps existing application id`() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(Regex("applicationId\\s*=\\s*\"com\\.zj\\.phonemirror\"").containsMatchIn(gradle))
        assertTrue(Regex("versionCode\\s*=\\s*10\\b").containsMatchIn(gradle))
        assertTrue(Regex("versionName\\s*=\\s*\"1\\.0\\.9\"").containsMatchIn(gradle))
    }

    /** 可见标题与后台通知不能遗留旧品牌名。 */
    @Test
    fun `visible Android surfaces do not expose old brand`() {
        val visibleSources = listOf(
            File("src/main/java/com/zj/phonemirror/ui/MainActivity.kt"),
            File("src/main/java/com/zj/phonemirror/service/RelayForegroundService.kt"),
        )
        visibleSources.forEach { source ->
            assertFalse("${source.name} still exposes Phone Mirror", source.readText().contains("Phone Mirror"))
        }
    }

    /** 解析真实 Android XML，以验证资源消费契约而非复制实现常量。 */
    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

}
