package com.klocktx.moneyblocks.ui.theme

import com.klocktx.moneyblocks.ui.theme.Pigment.contrast
import com.klocktx.moneyblocks.ui.theme.Pigment.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题的量化验收。
 *
 * 我没法用眼睛看这六个主题，所以只认算得出来的判据：小字必须够清楚、
 * 按钮上的字必须够清楚、卡片与底必须分得开层。配色好不好看归你判断，
 * 但"看不看得清"不该靠运气，也不该等装机之后才发现。
 */
class MonetThemeTest {

    private companion object {
        /** 色卡上两栏实色（主色、亮色）的平均距离下限：低于这个数，两颗在屏幕上就分不出来。 */
        const val DECK_MIN_DISTANCE = 0.055
    }

    private fun cases() = MonetTheme.entries.flatMap { theme ->
        // 消息用 key（ASCII）：这台机器的控制台是 gbk，中文断言消息在终端里会变成一串问号
        listOf("light ${theme.key}" to theme.light(), "dark ${theme.key}" to theme.dark())
    }

    @Test
    fun bodyTextIsAaaReadableOnBothBackgrounds() {
        for ((name, surface) in cases()) {
            assertTrue("$name text/surface ${contrast(surface.text, surface.surface)}",
                contrast(surface.text, surface.surface) >= MonetTheme.MIN_TEXT)
            assertTrue("$name text/background ${contrast(surface.text, surface.background)}",
                contrast(surface.text, surface.background) >= MonetTheme.MIN_TEXT)
        }
    }

    @Test
    fun mutedTextIsAAReadable() {
        for ((name, surface) in cases()) {
            assertTrue("$name muted ${contrast(surface.muted, surface.surface)}",
                contrast(surface.muted, surface.surface) >= MonetTheme.MIN_MUTED)
            assertTrue("$name danger ${contrast(surface.danger, surface.surface)}",
                contrast(surface.danger, surface.surface) >= MonetTheme.MIN_MUTED)
        }
    }

    @Test
    fun buttonLabelsAreReadable() {
        for ((name, surface) in cases()) {
            assertTrue("$name primary ${contrast(surface.onPrimary, surface.primary)}",
                contrast(surface.onPrimary, surface.primary) >= MonetTheme.MIN_ON_PRIMARY)
            assertTrue("$name dangerBtn ${contrast(surface.onDanger, surface.danger)}",
                contrast(surface.onDanger, surface.danger) >= MonetTheme.MIN_ON_PRIMARY)
            assertTrue("$name accentBtn ${contrast(surface.onAccent, surface.accent)}",
                contrast(surface.onAccent, surface.accent) >= MonetTheme.MIN_ON_PRIMARY)
            assertTrue("$name blockOnBg ${contrast(surface.primary, surface.surface)}",
                contrast(surface.primary, surface.surface) >= MonetTheme.MIN_BLOCK)
        }
    }

    /**
     * 层次靠 tonal 阶拉开而不是阴影：浅色模式越高的面越暗，深色模式越高的面越亮。
     * 顺序固定为 surface → background → containerLow → container → containerHigh。
     */
    @Test
    fun containersStepAwayFromThePageInOneDirection() {
        for (theme in MonetTheme.entries) {
            val light = theme.light()
            // 浅色：卡片最亮，页面底其次，容器一档比一档浓（暗）
            val ladder = listOf(
                light.surface, light.background, light.containerLow, light.container, light.containerHigh,
            ).map { luminance(it) }
            assertTrue("${theme.key} light ladder not descending: $ladder",
                ladder.zipWithNext().all { (a, b) -> a > b })
            assertTrue("${theme.key} light span too small: $ladder",
                kotlin.math.abs(ladder.first() - ladder.last()) >= 0.05)

            // 深色：页面底最暗，往上每一档更亮
            val dark = theme.dark()
            val darkLadder = listOf(
                dark.background, dark.surface, dark.containerLow, dark.container, dark.containerHigh,
            ).map { luminance(it) }
            assertTrue("${theme.key} dark ladder not ascending: $darkLadder",
                darkLadder.zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test
    fun outlineIsVisibleButQuieterThanText() {
        for ((name, surface) in cases()) {
            val against = contrast(surface.outline, surface.surface)
            assertTrue("$name outline too faint $against", against >= 1.18)
            assertTrue("$name outline louder than text $against",
                against < contrast(surface.text, surface.surface))
        }
    }

    /** 两个主题至少要在"主色明度"或"页面底色温"之一上分得开。 */
    @Test
    fun themesAreDistinguishableFromEachOther() {
        val surfaces = MonetTheme.entries.map { it.key to it.light() }
        surfaces.forEach { (keyA, a) ->
            surfaces.forEach { (keyB, b) ->
                if (keyA == keyB) return@forEach
                // 只量色卡上那两栏**实色**（主色与亮色）。两栏雾色相近是应该的——
                // 莫奈的画面底本来就都是灰白雾，把它们算进平均值会把判据稀释一半。
                val drift = listOf(2, 3).map { Pigment.distance(a.deck[it], b.deck[it]) }.average()
                assertTrue("$keyA vs $keyB too close: $drift", drift > DECK_MIN_DISTANCE)
            }
        }
    }

    /**
     * 把每个主题的最差对比度打出来（不判定，只报数）。
     * 调种子的时候需要看这些边界值，不然只能凭感觉猜"是不是快糊了"。
     */
    @Test
    fun contrastReport() {
        for (theme in MonetTheme.entries) {
            listOf("light" to theme.light(), "dark" to theme.dark()).forEach { (mode, s) ->
                val text = contrast(s.text, s.surface)
                val muted = contrast(s.muted, s.surface)
                val btn = contrast(s.onPrimary, s.primary)
                val block = contrast(s.primary, s.surface)
                println(
                    String.format(
                        java.util.Locale.US,
                        "PALETTE %s %s text=%.2f muted=%.2f btn=%.2f blockOnCard=%.2f " +
                            "deck=%06X/%06X/%06X/%06X ui:bg=%06X card=%06X text=%06X muted=%06X outline=%06X",
                        theme.key, mode, text, muted, btn, block,
                        s.deck[0] and 0xFFFFFF, s.deck[1] and 0xFFFFFF,
                        s.deck[2] and 0xFFFFFF, s.deck[3] and 0xFFFFFF,
                        s.background and 0xFFFFFF, s.surface and 0xFFFFFF,
                        s.text and 0xFFFFFF, s.muted and 0xFFFFFF, s.outline and 0xFFFFFF,
                    ),
                )
            }
        }
    }

    @Test
    fun keysAreUniqueAndUnknownKeyFallsBackToDefault() {
        assertEquals(MonetTheme.entries.size, MonetTheme.entries.map { it.key }.toSet().size)
        // 色板上看得见的是两个字短名，重名就没法区分两颗漆点
        assertEquals(MonetTheme.entries.size, MonetTheme.entries.map { it.short }.toSet().size)
        assertEquals(MonetTheme.entries.size, MonetTheme.entries.map { it.label }.toSet().size)
        assertEquals(MonetTheme.GREY_BLUE, MonetTheme.fromKey("grey-blue"))
        assertEquals(MonetTheme.WATERLILY, MonetTheme.fromKey("waterlily"))
        assertEquals(MonetTheme.GREY_BLUE, MonetTheme.fromKey("从没有这个键"))
        assertEquals(MonetTheme.GREY_BLUE, MonetTheme.fromKey(null))
        assertTrue(MonetTheme.entries.size >= 5)
    }

    @Test
    fun pigmentMathMatchesTheWcagReferencePoints() {
        assertEquals(21.0, contrast(Pigment.BLACK, Pigment.WHITE), 0.01)
        assertEquals(1.0, contrast(0xFF123456, 0xFF123456), 0.001)
        // #767676 on white 是 WCAG 常用的 AA 临界值，约 4.54
        assertTrue(contrast(0xFF767676, Pigment.WHITE) in 4.4..4.7)
        assertEquals(Pigment.WHITE, Pigment.mix(0xFF000000, Pigment.WHITE, 1.0))
        assertEquals(0xFF808080, Pigment.mix(0xFF000000, Pigment.WHITE, 0.5))
    }
}
