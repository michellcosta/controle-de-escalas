package com.controleescalas.app.ui.theme

import androidx.compose.ui.graphics.Color

// Cores Neon (Destaques)
val NeonGreen = Color(0xFF10B981) // Verde mais suave e profissional
val NeonGreenDark = Color(0xFF059669)
val NeonBlue = Color(0xFF3B82F6)
val NeonPurple = Color(0xFF7C3AED)
val NeonPink = Color(0xFFDB2777)
val NeonOrange = Color(0xFFF59E0B)
val NeonCyan = Color(0xFF0891B2)
val StatusIndigo = Color(0xFF6366F1)
val StatusAmber = Color(0xFFF59E0B)
val StatusMuted = Color(0xFF6B7280)

// Gradientes para cards especiais
val GradientGreenStart = Color(0xFF10B981)
val GradientGreenEnd = Color(0xFF059669)
val GradientBlueStart = Color(0xFF3B82F6)
val GradientBlueEnd = Color(0xFF1E40AF)
val GradientPurpleStart = Color(0xFF7C3AED)
val GradientPurpleEnd = Color(0xFF5B21B6)
val GradientOrangeStart = Color(0xFFF59E0B)
val GradientOrangeEnd = Color(0xFFD97706)
val GradientCyanStart = Color(0xFF0891B2)
val GradientCyanEnd = Color(0xFF0E7490)

// Cores de Borda
val CardBorder = Color(0xFF2A2A2A)

// Cores de Fundo (Dark Mode Premium) - Sistema expandido
val DarkBackground = Color(0xFF0A0A0A)
val DeepBlue = Color(0xFF050A14)
val DarkSurface = Color(0xFF121212) // Surface1
val DarkSurfaceVariant = Color(0xFF1E1E1E) // Surface2
val Surface3 = Color(0xFF2A2A2A)
val Surface4 = Color(0xFF363636)

// Cores de Fundo (Light Mode) - Ajustadas para serem mais suaves e menos cansativas
val LightBackground = Color(0xFFF2F2F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE5E5EA)

// Cores Glass (Efeito Vidro)
val GlassBlack = Color(0x80000000)
val GlassDark = Color(0x40FFFFFF)

// Cores de Texto
val TextWhite = Color(0xFFFFFFFF)
val TextBlack = Color(0xFF121212) // Preto quase puro para máximo contraste (era 0xFF1A1A1A)
val TextGray = Color(0xFFA0A0A0)
val TextGrayLight = Color(0xFFC0C0C0)
val TextGrayDark = Color(0xFF808080)
val TextGrayLightMode = Color(0xFF495057) // Cinza escuro para textos secundários (era 0xFF6C757D)
val TextGrayMediumLightMode = Color(0xFF343A40) // Cinza intermediário para labels

// Cores de Destaque Adaptativas (Light Mode)
val NeonBlueContrast = Color(0xFF007BFF) // Azul mais escuro para contraste no modo claro
val NeonOrangeContrast = Color(0xFFD94800) // Laranja mais escuro para contraste no modo claro
val NeonPurpleContrast = Color(0xFF8A2BE2) // Roxo mais escuro para contraste no modo claro
val NeonGreenContrast = Color(0xFF065F46) // Verde mais escuro para contraste no modo claro

// Cores Semânticas (Status)
val StatusSuccess = Color(0xFF10B981) // Verde mais sóbrio para sucesso/concluído
val StatusWarning = Color(0xFFFF8C00) // Laranja para alertas/próximo
val StatusError = Color(0xFFEF4444)   // Vermelho para erros
val StatusInfo = Color(0xFF3B82F6)    // Azul para informações neutras

// Cores para tendências
val TrendUp = Color(0xFF10B981)
val TrendDown = Color(0xFFEF4444)
val TrendNeutral = Color(0xFFA0A0A0)

fun statusColor(estado: String?): Color {
    return when (estado?.trim()?.uppercase()) {
        "A_CAMINHO", "A CAMINHO", "AGUARDANDO" -> NeonBlue
        "IR_ESTACIONAMENTO" -> StatusIndigo
        "CHEGUEI" -> NeonGreen
        "ESTACIONAMENTO" -> NeonPurple
        "CARREGANDO" -> StatusAmber
        "CONCLUIDO" -> StatusMuted
        "PROXIMO" -> StatusAmber
        else -> TextGray
    }
}
