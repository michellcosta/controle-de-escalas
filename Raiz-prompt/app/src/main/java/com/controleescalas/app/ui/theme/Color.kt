package com.controleescalas.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// Tema claro único (estilo Meli)
// ============================================================
val MeliYellow = Color(0xFFFFE600)      // Topo / status bar / destaque
val MeliYellowDark = Color(0xFFE6CF00)  // Detalhes e hover
val MeliBlue = Color(0xFF3483FA)        // Ações primárias, links, seleção
val CardBorderLight = Color(0xFFE6E6E6) // Borda de card
val TextDisabled = Color(0xFF9E9E9E)    // Placeholder / desabilitado

// Fundo claro
val LightBackground = Color(0xFFF2F2F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE5E5EA)

// Texto
val TextBlack = Color(0xFF121212)
val TextGrayLightMode = Color(0xFF495057)       // Texto secundário no claro
val TextGrayMediumLightMode = Color(0xFF343A40) // Labels no claro
val TextGray = Color(0xFFA0A0A0)                // Neutro (badges "sem resposta", categoria genérica)
val TextWhite = Color(0xFFFFFFFF)               // Texto sobre fundo colorido (snackbar, status)

// Cores de STATUS (semânticas — não usar como cor de ação)
val NeonGreen = Color(0xFF10B981)      // CHEGUEI / disponível / ativa
val NeonBlue = Color(0xFF3B82F6)       // A_CAMINHO
val NeonPurple = Color(0xFF7C3AED)     // ESTACIONAMENTO / premium
val NeonOrange = Color(0xFFF59E0B)     // CARREGANDO / pendente / aviso
val NeonCyan = Color(0xFF0891B2)       // UTILITARIO
val NeonPink = Color(0xFFDB2777)       // Paleta de avatares
val StatusIndigo = Color(0xFF6366F1)   // IR_ESTACIONAMENTO
val StatusAmber = Color(0xFFF59E0B)    // PROXIMO
val StatusMuted = Color(0xFF6B7280)    // CONCLUIDO

// Gradientes para cards de dados (dashboard/avatares)
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

// Semânticas gerais
val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFFFF8C00)
val StatusError = Color(0xFFEF4444)
val StatusInfo = Color(0xFF3B82F6)

// Tendências
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

fun modalidadeColor(modalidade: String?): Color {
    return when (modalidade?.trim()?.uppercase()) {
        "PASSEIO" -> Color(0xFF7CB342)
        "DEDICADO" -> NeonPurple
        "UTILITARIO" -> NeonCyan
        else -> NeonBlue // FROTA
    }
}
