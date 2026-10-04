package app.chargeshare.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.chargeshare.resources.Res
import app.chargeshare.resources.plus_jakarta_sans_bold
import app.chargeshare.resources.plus_jakarta_sans_extra_bold
import app.chargeshare.resources.plus_jakarta_sans_medium
import app.chargeshare.resources.plus_jakarta_sans_regular
import app.chargeshare.resources.plus_jakarta_sans_semi_bold
import org.jetbrains.compose.resources.Font

/**
 * Die Schrift „Plus Jakarta Sans“ (SIL Open Font License 1.1, Lizenztext in `composeResources/files/plus_jakarta_sans_ofl.txt`)
 * in den Stärken 400–800 aus den Compose-Ressourcen.
 */
@Composable
fun rememberChargeShareFontFamily(): FontFamily {
    val regular = Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal)
    val medium = Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.plus_jakarta_sans_semi_bold, FontWeight.SemiBold)
    val bold = Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold)
    val extraBold = Font(Res.font.plus_jakarta_sans_extra_bold, FontWeight.ExtraBold)
    return remember(regular, medium, semiBold, bold, extraBold) {
        FontFamily(regular, medium, semiBold, bold, extraBold)
    }
}

/**
 * Typografie-Skala des Designs (Größen in sp, Stärken 400–800). Alle Texte der App erben daraus;
 * einzelne Größen nie im Screen hartkodieren, sondern einen dieser Stile wählen.
 */
fun chargeShareTypography(fontFamily: FontFamily): Typography = Typography(
    displaySmall = style(fontFamily, 32.sp, FontWeight.ExtraBold, 38.sp),
    headlineLarge = style(fontFamily, 28.sp, FontWeight.ExtraBold, 34.sp),
    headlineMedium = style(fontFamily, 24.sp, FontWeight.ExtraBold, 30.sp),
    headlineSmall = style(fontFamily, 22.sp, FontWeight.ExtraBold, 28.sp),
    titleLarge = style(fontFamily, 20.sp, FontWeight.ExtraBold, 26.sp),
    titleMedium = style(fontFamily, 16.sp, FontWeight.Bold, 22.sp),
    titleSmall = style(fontFamily, 14.sp, FontWeight.Bold, 20.sp),
    bodyLarge = style(fontFamily, 15.sp, FontWeight.Medium, 22.sp),
    bodyMedium = style(fontFamily, 13.sp, FontWeight.Medium, 19.sp),
    bodySmall = style(fontFamily, 12.sp, FontWeight.Normal, 17.sp),
    labelLarge = style(fontFamily, 13.sp, FontWeight.SemiBold, 18.sp),
    labelMedium = style(fontFamily, 12.sp, FontWeight.SemiBold, 16.sp),
    labelSmall = style(fontFamily, 10.sp, FontWeight.SemiBold, 14.sp),
)

private fun style(fontFamily: FontFamily, size: TextUnit, weight: FontWeight, lineHeight: TextUnit) =
    TextStyle(fontFamily = fontFamily, fontSize = size, fontWeight = weight, lineHeight = lineHeight)
