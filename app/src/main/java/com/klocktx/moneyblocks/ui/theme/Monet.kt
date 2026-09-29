package com.klocktx.moneyblocks.ui.theme

import com.klocktx.moneyblocks.ui.theme.Pigment.BLACK
import com.klocktx.moneyblocks.ui.theme.Pigment.WHITE
import com.klocktx.moneyblocks.ui.theme.Pigment.contrast
import com.klocktx.moneyblocks.ui.theme.Pigment.darken
import com.klocktx.moneyblocks.ui.theme.Pigment.ensureContrast
import com.klocktx.moneyblocks.ui.theme.Pigment.lighten
import com.klocktx.moneyblocks.ui.theme.Pigment.luminance
import com.klocktx.moneyblocks.ui.theme.Pigment.mix

/**
 * 莫奈主题。
 *
 * 每套主题给**五个色阶**，取自画面上真实存在的几层，而不是"一个主色配一个补色"：
 *
 * - [wash]  雾底：整页底色，就是画面那层空气
 * - [haze]  浅霭：卡片与凹陷面，比雾底实一点
 * - [tone]  中调主色：莫奈的颜料都夹在灰调中间明度，不是高饱和实色
 * - [light] 亮色：画里那一点温度（睡莲的粉、日出的橙、麦垛的金）
 * - [ink]   墨锚：正文与深色模式的骨架
 *
 * 上一版被否掉的原因就在这儿：我拿 `#A94A1E` 那种饱和实色当主色，那是企业配色不是莫奈。
 * 莫奈靠**并置的近似色**分层，颜色全都带雾。
 *
 * 其余槽位仍从这五级派生（改一处规则六个主题一起好），能不能读清由 MonetThemeTest 判。
 */
enum class MonetTheme(
    val key: String,
    val label: String,
    /** 色板下面那两个字。四字词在窄格子里挤不下。 */
    val short: String,
    val note: String,
    val wash: Long,
    val haze: Long,
    val tone: Long,
    val light: Long,
    val ink: Long,
) {
    GREY_BLUE(
        key = "grey-blue",
        label = "灰蓝晨雾",
        short = "晨雾",
        note = "清晨的水面 · 与启动图标同色",
        wash = 0xFFE6EEF4,
        haze = 0xFFCFE0EB,
        tone = 0xFF4E7189,
        light = 0xFFD09A6A,
        ink = 0xFF1F303C,
    ),
    WATERLILY(
        key = "waterlily",
        label = "睡莲水光",
        short = "水光",
        note = "睡莲 · 池面的紫与粉",
        wash = 0xFFE7EEF4,
        haze = 0xFFCFDEEA,
        tone = 0xFF6C8AA0,
        light = 0xFFC79AAE,
        ink = 0xFF2B3541,
    ),
    SUNRISE(
        key = "sunrise",
        label = "日出橙雾",
        short = "日出",
        note = "印象 · 日出 · 灰紫的海雾",
        wash = 0xFFF1E9E4,
        haze = 0xFFDED0CE,
        tone = 0xFF8A7288,
        light = 0xFFD98A5A,
        ink = 0xFF33293A,
    ),
    WHEAT(
        key = "wheat",
        label = "麦田赭金",
        short = "麦金",
        note = "拾穗者 · 夏末的麦垛",
        wash = 0xFFF4EEDF,
        haze = 0xFFE5DBC3,
        tone = 0xFF9A8448,
        light = 0xFF6E8A5E,
        ink = 0xFF3A3320,
    ),
    FOOTBRIDGE(
        key = "footbridge",
        label = "竹舟池绿",
        short = "池绿",
        note = "日本桥 · 柳荫下的水面",
        wash = 0xFFEAF0E8,
        haze = 0xFFD6E2D6,
        tone = 0xFF5E8A70,
        light = 0xFF9A7FA8,
        ink = 0xFF23321F,
    ),
    ROUEN_DUSK(
        key = "rouen-dusk",
        label = "教堂黄昏",
        short = "黄昏",
        note = "鲁昂大教堂 · 日落时的玫瑰灰",
        wash = 0xFFF3E9E6,
        haze = 0xFFE6D2CD,
        tone = 0xFF8A6472,
        // 原来这里也是赭黄，和「日出」的橙只在明度上差一点，实测两栏并置距离只有 0.058，
        // 色板上一眼看过去分不出谁是谁。那幅画落日打在立面上的就是玫瑰色，改回玫灰。
        light = 0xFFC5849A,
        ink = 0xFF33232A,
    ),
    ;

    /** 算完的一整套配色，全是 0xAARRGGBB。[deck] 是给色板用的四层示意色。 */
    data class Surface(
        val background: Long,
        val surface: Long,
        val containerLow: Long,
        val container: Long,
        val containerHigh: Long,
        val outline: Long,
        val text: Long,
        val muted: Long,
        val primary: Long,
        val onPrimary: Long,
        val accent: Long,
        val onAccent: Long,
        val danger: Long,
        val onDanger: Long,
        val deck: List<Long>,
    )

    /**
     * 色块和它上面的字一起解：先让色块在底上够分得开，再取黑/白里对比度高的当字色，
     * 还不够就把色块往那一侧推。
     */
    private fun pair(base: Long, background: Long): Pair<Long, Long> {
        var block = ensureContrast(
            base,
            background,
            MIN_BLOCK,
            if (luminance(background) > 0.5) BLACK else WHITE,
        )
        val label = if (contrast(WHITE, block) >= contrast(BLACK, block)) WHITE else BLACK
        repeat(20) {
            if (contrast(label, block) >= MIN_ON_PRIMARY) return block to label
            block = if (label == WHITE) darken(block, 0.06) else lighten(block, 0.06)
        }
        return block to label
    }

    /** 浅色：页面是雾，卡片是雾里最亮的一块，容器一档档往实里走。 */
    fun light(): Surface {
        val surface = lighten(wash, 0.55)
        val (primary, onPrimary) = pair(tone, surface)
        val (accent, onAccent) = pair(light, surface)
        // 危险色在浅色里是「超支 剩/超多少」那行**文字**，按文字判据保 4.5，
        // 不能走 pair() 的色块判据（只保 3.0）——上一版就是这么漏掉的
        val danger = ensureContrast(mix(light, DANGER_SEED, 0.55), surface, MIN_MUTED, BLACK)
        val onDanger = if (contrast(WHITE, danger) >= MIN_ON_PRIMARY) WHITE else BLACK
        return Surface(
            background = wash,
            surface = surface,
            containerLow = mix(wash, haze, 0.55),
            container = haze,
            containerHigh = mix(haze, tone, 0.30),
            outline = mix(haze, tone, 0.62),
            text = ensureContrast(ink, surface, MIN_TEXT, BLACK),
            muted = ensureContrast(mix(tone, ink, 0.45), surface, MIN_MUTED, BLACK),
            primary = primary,
            onPrimary = onPrimary,
            accent = accent,
            onAccent = onAccent,
            danger = danger,
            onDanger = onDanger,
            deck = listOf(wash, haze, primary, accent),
        )
    }

    /** 深色：拿墨锚当骨架，主色提亮后当容器里的反光。 */
    fun dark(): Surface {
        val surface = mix(ink, BLACK, 0.34)
        val (primary, onPrimary) = pair(lighten(tone, 0.38), surface)
        val (accent, onAccent) = pair(lighten(light, 0.16), surface)
        val danger = ensureContrast(lighten(mix(light, DANGER_SEED, 0.55), 0.30), surface, MIN_MUTED, WHITE)
        val onDanger = if (contrast(BLACK, danger) >= MIN_ON_PRIMARY) BLACK else WHITE
        return Surface(
            background = mix(ink, BLACK, 0.55),
            surface = surface,
            containerLow = mix(ink, tone, 0.14),
            container = mix(ink, tone, 0.28),
            containerHigh = mix(ink, tone, 0.42),
            outline = mix(tone, ink, 0.45),
            text = ensureContrast(lighten(wash, 0.55), surface, MIN_TEXT, WHITE),
            muted = ensureContrast(mix(wash, tone, 0.45), surface, MIN_MUTED, WHITE),
            primary = primary,
            onPrimary = onPrimary,
            accent = accent,
            onAccent = onAccent,
            danger = danger,
            onDanger = onDanger,
            deck = listOf(mix(ink, BLACK, 0.55), mix(ink, tone, 0.28), primary, accent),
        )
    }

    companion object {
        /** 正文小字对卡片底：AAA 级。 */
        const val MIN_TEXT = 7.0

        /** 次要说明文字与危险色：AA 级。 */
        const val MIN_MUTED = 4.5

        /** 色块上的字。 */
        const val MIN_ON_PRIMARY = 4.5

        /** 色块本身要在底上看得出边界（非文字判据取 3.0）。 */
        const val MIN_BLOCK = 3.0

        /** 超支/删除的锚点。莫奈没有"报错红"，从灰陶红里取。 */
        const val DANGER_SEED = 0xFFA4544CL

        fun fromKey(key: String?): MonetTheme = entries.firstOrNull { it.key == key } ?: GREY_BLUE
    }
}
