package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.theme.StatusError

@Composable
fun DriverConfigScreen(
    baseId: String,
    motoristaId: String,
    onIdentificacaoClick: () -> Unit,
    onQuinzenaClick: () -> Unit,
    onDevolucaoClick: () -> Unit,
    onNotificacoesClick: () -> Unit = {},
    onSobreAppClick: () -> Unit,
    onAjudaClick: () -> Unit,
    onTermosClick: () -> Unit,
    onLogout: () -> Unit
) {
    PremiumBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(title = "Geral")
            AppMenuItem("Identificação", Icons.Default.QrCode, onIdentificacaoClick)
            AppMenuItem("Quinzena", Icons.Default.DateRange, onQuinzenaClick)
            AppMenuItem("Devolução", Icons.Default.Inventory2, onDevolucaoClick)

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Notificações")
            AppMenuItem("Notificações não aparecem?", Icons.Default.Notifications, onNotificacoesClick)

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Ajuda e Suporte")
            AppMenuItem("Sobre o App", Icons.Default.Info, onSobreAppClick)
            AppMenuItem("Ajuda", Icons.Default.Help, onAjudaClick)
            AppMenuItem("Termos de Uso", Icons.Default.Description, onTermosClick)

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Conta")
            AppMenuItem(
                text = "Sair da Conta",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = onLogout,
                iconTint = StatusError,
                textColor = StatusError
            )
        }
    }
}
