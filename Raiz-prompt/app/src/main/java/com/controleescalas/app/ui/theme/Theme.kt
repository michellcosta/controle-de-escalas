package com.controleescalas.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Constantes de Espaçamento Premium
val HorizontalPadding = 16.dp
val SpacingSmall = 8.dp
val SpacingMedium = 16.dp
val SpacingLarge = 24.dp

// Tema claro único (estilo Meli): topo amarelo, fundo branco, botões azuis, texto preto
private val LightColorScheme = lightColorScheme(
    primary = MeliBlue,
    secondary = MeliBlue,
    tertiary = MeliYellow,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = TextBlack,
    onBackground = TextBlack,
    onSurface = TextBlack,
    onSurfaceVariant = TextGrayMediumLightMode,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceTint = Color.White
)

@Composable
fun ControleEscalasTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MeliYellow.toArgb()
            window.navigationBarColor = Color.White.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
