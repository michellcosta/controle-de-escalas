package com.controleescalas.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Tela inicial do app com animação e auto-login
 */
@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToApp: (String, String, String) -> Unit // userId, baseId, role
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    // Animação de fade e escala
    val infiniteTransition = rememberInfiniteTransition(label = "splash_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )
    
    // Verificar sessão ativa
    LaunchedEffect(Unit) {
        delay(1500) // Mostrar logo por 1.5 segundos
        
        val lastSession = sessionManager.getLastActiveSession()
        
        if (lastSession != null) {
            // Validar se sessão ainda é válida
            val isValid = sessionManager.validateSession(lastSession)
            
            if (isValid) {
                // Auto-login
                onNavigateToApp(
                    lastSession.userId,
                    lastSession.baseId,
                    lastSession.userRole
                )
            } else {
                // Sessão expirada, ir para seleção de contas
                onNavigateToMain()
            }
        } else {
            // Sem sessão, ir para tela principal
            onNavigateToMain()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DeepBlue, DarkBackground)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo/Título com animação
            Text(
                text = "CONTROLE DE\nESCALAS",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    lineHeight = 42.sp
                ),
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
            
            // Indicador de loading
            CircularProgressIndicator(
                color = NeonGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Carregando...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
    }
}

