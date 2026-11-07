package shifa.doctor.ui.theme

import androidx.compose.ui.graphics.Color

// Primitives (docs/design/shifa-ds.css `:root`, first block). The doctor app adds the 70-steps for words and grey-550.
private val Teal50 = Color(0xFF00BBB0)
private val Teal70 = Color(0xFF007A72)
private val Charcoal = Color(0xFF272727)
private val Grey600 = Color(0xFF494D4C)
private val Grey550 = Color(0xFF666B6B)
private val Grey500 = Color(0xFF7D8282)
private val Grey400 = Color(0xFF979B9B)
private val Grey200 = Color(0xFFE8E8E8)
private val Grey100 = Color(0xFFF2F3F2)
private val White = Color(0xFFFFFFFF)
private val Red = Color(0xFFDC2F2F)
private val Red70 = Color(0xFFC42828)
private val Amber = Color(0xFFEBA400)
private val Amber70 = Color(0xFF8A5D00)
private val Blue = Color(0xFF1C75CF)
private val Blue70 = Color(0xFF1766B5)

// Semantic tokens, named 1:1 after the stylesheet (`--text-brand` = ShifaColors.Text.Brand). Words use the 70-steps
// (AuTokens: teal-50 text fails contrast); fills and icons keep the 50-steps.
object ShifaColors {
    object Text {
        val Primary = Charcoal
        val Secondary = Grey600
        val Tertiary = Grey550
        val Disabled = Grey400
        val Inverse = White
        val Brand = Teal70
        val BrandStrong = Teal70
        val Link = Teal70
        val Error = Red70
        val Warning = Amber70
        val WarningStrong = Amber70
        val Info = Blue70
    }

    object Icon {
        val Primary = Charcoal
        val Secondary = Grey600
        val Disabled = Grey400
        val Inverse = White
        val Brand = Teal50
        val Error = Red
        val Warning = Amber
        val Info = Blue
    }

    object Bg {
        val Canvas = Grey100
        val Surface = White
        val Disabled = Grey200
        val Brand = Teal50
        val SuccessSubtle = Color(0xFFE5F8F7)
        val WarningSubtle = Color(0xFFFCF6E8)
        val ErrorSubtle = Color(0xFFF9EBEB)
        val InfoSubtle = Color(0xFFEBF2F9)
        val Overlay = Color(0x80000000)
        val Media = Color(0xFF1B1D1D) // video and image viewers
        val Hover = Alpha.Black5 // pressed rows (web hover)
        val Selected = SuccessSubtle // selected row, day, tab
    }

    object Border {
        val Default = Grey200
        val Disabled = Grey400
        val Brand = Teal50
        val Focus = Teal50
        val Error = Red
        val Warning = Amber
        val Success = Teal50
    }

    object Interactive {
        val Primary = Teal50
        val OnPrimary = White
        val DisabledBg = Grey200
        val Destructive = Red
    }

    object Alpha {
        val White15 = Color(0x26FFFFFF)
        val White20 = Color(0x33FFFFFF)
        val White25 = Color(0x40FFFFFF)
        val White35 = Color(0x59FFFFFF)
        val White40 = Color(0x66FFFFFF)
        val White55 = Color(0x8CFFFFFF)
        val White65 = Color(0xA6FFFFFF)
        val White70 = Color(0xB3FFFFFF)
        val White90 = Color(0xE6FFFFFF)
        val Teal75 = Color(0xBF00BBB0)
        val Teal25 = Color(0x4000BBB0)
        val Teal15 = Color(0x2600BBB0)
        val Black5 = Color(0x0D000000)
        val Black25 = Color(0x40000000)
        val Black50 = Color(0x80000000)
    }

    // Chart series in order, and the grid (`--chart-*`): home analytics and finance.
    object Chart {
        val Series1 = Teal50
        val Series2 = Charcoal
        val Series3 = Amber
        val Series4 = Blue
        val Grid = Grey200
    }

    // Colours the design sets as raw values (`--raw-*`), named <component><Part>.
    object Raw {
        val SplashBlob = Color(0xA600A39A)
        val OrbHighlight = Color(0xFFA6E8E4) // voice orb gradient start: teal-50 35% in white (DaIntel `ai-orb`)
        val SplashLogo = Color(0xFF59C2BC)
        val GlassTile = Color(0x5CFFFFFF) // splash and login hero tile
        val GlassTileBorder = White
        val VideoPreview = Color(0xFF2F5D5A) // video tile before the camera starts
        val MapLand = Color(0xFFE8EFE9) // map placeholder (location picker)
        val MapPark = Color(0xFFCFE5C6)
        val MapWater = Color(0xFFBCDCF1)
        val MapStreet = Color(0xFFD6E3D3)
    }
}
