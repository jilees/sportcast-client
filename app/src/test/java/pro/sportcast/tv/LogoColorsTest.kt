package pro.sportcast.tv

import org.junit.Assert.*
import org.junit.Test

class LogoColorsTest {
    @Test fun transparentPaddingDoesNotDiluteLogo() { assertEquals(0xff381919.toInt(), LogoColors.darkAverage(intArrayOf(0xffff0000.toInt(), 0, 0x00ffffff))) }
    @Test fun averagesVisibleColorsBeforeDarkening() { assertEquals(0xff1c0d1c.toInt(), LogoColors.darkAverage(intArrayOf(0xffff0000.toInt(), 0xff0000ff.toInt()))) }
    @Test fun antialiasedPixelsContributeInProportionToOpacity() { assertEquals(0xff25111b.toInt(), LogoColors.darkAverage(intArrayOf(0xffff0000.toInt(), 0x800000ff.toInt()))) }
    @Test fun missingVisiblePixelsPreserveFallback() {
        assertNull(LogoColors.darkAverage(intArrayOf())); assertNull(LogoColors.darkAverage(intArrayOf(0x00ffffff, 0)))
    }
    @Test fun evenWhiteLogosStayDark() {
        assertEquals(0xff383838.toInt(), LogoColors.darkAverage(intArrayOf(0xffffffff.toInt())))
        assertEquals(0xff000000.toInt(), LogoColors.darkAverage(intArrayOf(0xff000000.toInt())))
    }
}
