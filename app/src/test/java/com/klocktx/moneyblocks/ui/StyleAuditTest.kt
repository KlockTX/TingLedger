package com.klocktx.moneyblocks.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 样式判据守卫。
 *
 * 这一轮把散落的尺寸/圆角/透明度/动画时长收进了 `ui/theme` 的 token。那种收口**很容易退回去**：
 * 下一个人赶时间写一个 `RoundedCornerShape(13.dp)`，没人会去翻历史。所以把判据写成会红的测试，
 * 而不是靠审计脚本记得跑、靠 review 的眼睛。
 *
 * 判据故意只有一条条明确的规则，不追求"完美"：
 * `0.dp` 允许（贴边、无偏移是语义，不是随手值），其余数值必须来自 token。
 */
class StyleAuditTest {

    private val themeDirName = "theme"

    private fun uiDir(): File {
        // 单测的工作目录是模块目录；两处都试一次，找不到就硬失败——
        // 扫描根缺失时静默返回"没问题"是最坏的结果（历史上真发生过）。
        val candidates = listOf(
            File("src/main/java/com/klocktx/moneyblocks/ui"),
            File("app/src/main/java/com/klocktx/moneyblocks/ui"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException(
                "找不到 ui 源码根目录，审计无意义。当前工作目录：" + File(".").absolutePath,
            )
    }

    /** 返回 `文件:行号 内容` 列表，失败信息直接可读。 */
    private fun hits(pattern: Regex, allow: (String) -> Boolean = { false }): List<String> {
        val out = ArrayList<String>()
        uiDir().walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { f ->
            if (f.parentFile?.name == themeDirName) return@forEach
            f.readLines().forEachIndexed { index, line ->
                val text = line.trim()
                if (text.startsWith("import") || text.startsWith("//") || text.startsWith("*")) return@forEachIndexed
                if (pattern.containsMatchIn(line) && !allow(text)) {
                    out += "${f.name}:${index + 1} $text"
                }
            }
        }
        return out
    }

    @Test
    fun noInlineCornerShapes() {
        val found = hits(Regex("""RoundedCornerShape\("""))
        assertTrue("圆角必须走 Radius/Panels：\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noRawDpOutsideTheme() {
        // 0.dp 是"没有偏移"的语义写法，不是随手值
        val found = hits(Regex("""[1-9][0-9]*(\.[0-9]+)?\.dp"""))
        assertTrue("尺寸必须走 Spacing/IconSize/Badge/Bar/Rows/Chart/Col/Skeleton/…：\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noRawAlphaOutsideTheme() {
        val found = hits(Regex("""alpha = 0\.[0-9]+f|tint\(0\.[0-9]+f"""))
        assertTrue("透明度必须走 Alpha：\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noRawAnimationDurationsOutsideTheme() {
        val found = hits(Regex("""tween\([0-9]"""))
        assertTrue("时长必须走 Motion：\n" + found.joinToString("\n"), found.isEmpty())
    }

    /** token 自己不能没人用：留着一套没人引用的尺度，迟早有人再写第二套。 */
    @Test
    fun radiusTokensAreActuallyUsed() {
        val body = uiDir().walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
            .joinToString("\n") { it.readText() }
        listOf("Radius.Card", "Radius.Field", "Radius.Chip").forEach {
            assertTrue("$it 没有被任何界面引用", body.contains(it))
        }
    }
}
