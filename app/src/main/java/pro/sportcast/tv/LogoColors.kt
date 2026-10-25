package pro.sportcast.tv

import kotlin.math.roundToInt

object LogoColors {
    fun darkAverage(pixels: IntArray): Int? {
        var alpha = 0L; var red = 0L; var green = 0L; var blue = 0L
        for (pixel in pixels) {
            val a = pixel ushr 24
            alpha += a; red += ((pixel ushr 16) and 255) * a; green += ((pixel ushr 8) and 255) * a; blue += (pixel and 255) * a
        }
        if (alpha == 0L) return null
        val r = red.toDouble() / alpha; val g = green.toDouble() / alpha; val b = blue.toDouble() / alpha
        val neutral = maxOf(r, g, b) * (1 - .55)
        fun dark(value: Double) = ((value * .55 + neutral) * .22).roundToInt()
        return (0xff000000L or (dark(r).toLong() shl 16) or (dark(g).toLong() shl 8) or dark(b).toLong()).toInt()
    }
}
