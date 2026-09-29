package com.klocktx.moneyblocks.ui.theme

/**
 * 纯算术的取色工具，**不引任何 Android / Compose 类型**。
 *
 * 颜色一律用 0xAARRGGBB 的 Long 表示，所以整套配色都能在普通 JVM 里算、能被单测断言。
 * 我这边没有设备可以看界面，配色对不对只能靠可计算的判据（WCAG 相对亮度与对比度）来保证，
 * 这层就是为了让那些判据可测而存在的。
 */
object Pigment {

    const val WHITE = 0xFFFFFFFFL
    const val BLACK = 0xFF000000L

    fun channel(color: Long, shift: Int): Double =
        ((color shr shift) and 0xFF).toDouble() / 255.0

    private fun pack(r: Double, g: Double, b: Double): Long {
        fun byte(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0 + 0.5).toLong()
        return (0xFFL shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }

    /** 线性插值：t=0 得到 from，t=1 得到 to。 */
    fun mix(from: Long, to: Long, t: Double): Long {
        val k = t.coerceIn(0.0, 1.0)
        return pack(
            channel(from, 16) + (channel(to, 16) - channel(from, 16)) * k,
            channel(from, 8) + (channel(to, 8) - channel(from, 8)) * k,
            channel(from, 0) + (channel(to, 0) - channel(from, 0)) * k,
        )
    }

    fun lighten(color: Long, t: Double): Long = mix(color, WHITE, t)

    fun darken(color: Long, t: Double): Long = mix(color, BLACK, t)

    /** WCAG 2.1 相对亮度。 */
    fun luminance(color: Long): Double {
        fun part(shift: Int): Double {
            val v = channel(color, shift)
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * part(16) + 0.7152 * part(8) + 0.0722 * part(0)
    }

    /** 对比度，1.0（同色）到 21.0（黑白）。 */
    fun contrast(a: Long, b: Long): Double {
        val light = luminance(a)
        val dark = luminance(b)
        val hi = maxOf(light, dark)
        val lo = minOf(light, dark)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * 两个颜色的距离，0（同色）到 1（互补两端）。
     * 对比度只说明"明暗差多少"，分不出橙色和青色，所以判断两个主题撞不撞色得用它。
     */
    fun distance(a: Long, b: Long): Double {
        fun d(shift: Int) = channel(a, shift) - channel(b, shift)
        return Math.sqrt(d(16) * d(16) + d(8) * d(8) + d(0) * d(0)) / 1.7320508
    }

    /**
     * 把 [color] 朝 [toward] 推到能在 [background] 上读出 [min] 对比度为止。
     *
     * 派生出来的颜色好不好看是主观的，但"小字看不看得清"是有阈值的——
     * 有了这一步，换任何主题色都不会把界面上的次要文字推到低对比区。
     */
    fun ensureContrast(
        color: Long,
        background: Long,
        min: Double,
        toward: Long,
        steps: Int = 24,
    ): Long {
        var result = color
        repeat(steps) {
            if (contrast(result, background) >= min) return result
            result = mix(result, toward, 0.08)
        }
        return result
    }
}
