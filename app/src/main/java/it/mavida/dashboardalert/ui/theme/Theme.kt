package it.mavida.dashboardalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tema dell'app. Due esigenze guida (spec §4):
 * - leggibilita' a distanza: corpi di testo piu' grandi del default Material;
 * - uso "sempre acceso": il tema scuro e' il default (meno consumo su OLED
 *   e meno abbagliamento in ambienti bui).
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFFA5D6A7),
    error = Color(0xFFEF9A9A),
    background = Color(0xFF101418),
    surface = Color(0xFF1A1F24),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF2E7D32),
    error = Color(0xFFC62828),
)

/** Tipografia ingrandita rispetto al default: il cruscotto si guarda da lontano. */
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp),
    bodyMedium = TextStyle(fontSize = 17.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = true, // default scuro: uso kiosk sempre acceso
    content: @Composable () -> Unit,
) {
    // isSystemInDarkTheme() letto per permettere in futuro di seguire il sistema;
    // per ora la scelta e' esplicita (vedi impostazioni, fase 9).
    val systemDark = isSystemInDarkTheme()
    val colors = if (darkTheme || systemDark && darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
