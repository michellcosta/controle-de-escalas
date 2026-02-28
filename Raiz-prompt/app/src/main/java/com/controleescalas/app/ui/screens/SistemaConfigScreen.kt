package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.controleescalas.app.data.models.ModoAtivacao
import com.controleescalas.app.data.models.SistemaConfig
import com.controleescalas.app.data.repositories.SistemaRepository
import androidx.compose.ui.graphics.luminance
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SistemaConfigScreen(
    superAdminId: String,
    onBack: () -> Unit,
    onNavigateToFeedbacks: () -> Unit = {}
) {
    val sistemaRepository = SistemaRepository()
    val scope = rememberCoroutineScope()
    
    var config by remember { mutableStateOf<SistemaConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    
    var modoSelecionado by remember { mutableStateOf<ModoAtivacao?>(null) }
    var dataAgendada by remember { mutableStateOf<Date?>(null) }
    var mostrarSeletorData by remember { mutableStateOf(false) }
    var mostrarConfirmacaoAtivacao by remember { mutableStateOf(false) }
    var registrandoUid by remember { mutableStateOf(false) }
    
    // Carregar configuração atual
    LaunchedEffect(Unit) {
        try {
            val configAtual = sistemaRepository.getConfiguracao()
            config = configAtual
            modoSelecionado = configAtual.modoAtivacao
            if (configAtual.dataAtivacaoAutomatica != null) {
                dataAgendada = Date(configAtual.dataAtivacaoAutomatica)
            }
            isLoading = false
        } catch (e: Exception) {
            error = "Erro ao carregar configuração: ${e.message}"
            isLoading = false
        }
    }
    
    // Função para salvar configuração
    fun salvarConfiguracao() {
        scope.launch {
            try {
                when (modoSelecionado) {
                    ModoAtivacao.MANUAL -> {
                        mostrarConfirmacaoAtivacao = true
                    }
                    ModoAtivacao.AUTOMATICA -> {
                        if (dataAgendada != null) {
                            val sucesso = sistemaRepository.agendarAtivacaoAutomatica(
                                dataAgendada!!.time,
                                superAdminId
                            )
                            if (sucesso) {
                                message = "Ativação automática agendada com sucesso!"
                                // Recarregar configuração
                                config = sistemaRepository.getConfiguracao()
                            } else {
                                error = "Erro ao agendar ativação"
                            }
                        } else {
                            error = "Selecione uma data para agendamento"
                        }
                    }
                    null -> {
                        // Cancelar agendamento
                        val sucesso = sistemaRepository.cancelarAgendamento(superAdminId)
                        if (sucesso) {
                            message = "Agendamento cancelado"
                            config = sistemaRepository.getConfiguracao()
                            dataAgendada = null
                        }
                    }
                }
            } catch (e: Exception) {
                error = "Erro ao salvar: ${e.message}"
            }
        }
    }
    
    // Função para ativar manualmente
    fun ativarManual() {
        scope.launch {
            try {
                val sucesso = sistemaRepository.ativarMonetizacaoManual(superAdminId)
                if (sucesso) {
                    message = "Monetização ativada com sucesso!"
                    config = sistemaRepository.getConfiguracao()
                    mostrarConfirmacaoAtivacao = false
                } else {
                    error = "Erro ao ativar monetização"
                }
            } catch (e: Exception) {
                error = "Erro: ${e.message}"
            }
        }
    }
    
    // Função para desativar
    fun desativar() {
        scope.launch {
            try {
                val sucesso = sistemaRepository.desativarMonetizacao(superAdminId)
                if (sucesso) {
                    message = "Monetização desativada"
                    config = sistemaRepository.getConfiguracao()
                } else {
                    error = "Erro ao desativar"
                }
            } catch (e: Exception) {
                error = "Erro: ${e.message}"
            }
        }
    }
    
    // Calcular tempo restante
    val tempoRestante = remember(config) {
        if (config?.dataAtivacaoAutomatica != null && !config!!.monetizacaoAtiva) {
            val agora = System.currentTimeMillis()
            val dataAgendada = config!!.dataAtivacaoAutomatica!!
            if (dataAgendada > agora) {
                dataAgendada - agora
            } else {
                null
            }
        } else {
            null
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configurações do Sistema", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            } else {
                val context = LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // UID do Firebase (para configurar SUPERADMIN_UIDS no Render)
                    GlassCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SectionHeader(title = "Seu UID do Firebase")
                            Text(
                                "Para poder solicitar localização no Assistente:\n" +
                                    "1) Render: Environment → SUPERADMIN_UIDS = este UID (copie abaixo).\n" +
                                    "2) Ou no Firestore: coleção sistema, documento config, campo superadminUids (tipo array) com um item igual a este UID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                maxLines = 6
                            )
                            Text(
                                superAdminId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("uid", superAdminId))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("UID copiado! Cole no Render em SUPERADMIN_UIDS.")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copiar UID")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (registrandoUid) return@OutlinedButton
                                        registrandoUid = true
                                        scope.launch {
                                            sistemaRepository.registerSuperAdminUid(superAdminId)
                                                .fold(
                                                    onSuccess = {
                                                        snackbarHostState.showSnackbar("Seu UID foi registrado. Agora você pode solicitar localização no Assistente.")
                                                    },
                                                    onFailure = { e ->
                                                        snackbarHostState.showSnackbar("Erro ao registrar: ${e.message}")
                                                    }
                                                )
                                            registrandoUid = false
                                        }
                                    },
                                    enabled = !registrandoUid,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonGreen else NeonGreenContrast)
                                ) {
                                    if (registrandoUid) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = NeonGreen, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (registrandoUid) "Registrando…" else "Registrar meu UID no sistema")
                                }
                            }
                        }
                    }
                    // Status atual
                    GlassCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (config?.monetizacaoAtiva == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (config?.monetizacaoAtiva == true) NeonGreen else Color(0xFFEF4444),
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        if (config?.monetizacaoAtiva == true) "Ativo" else "Desativado",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    config?.modoAtivacao?.let { modo ->
                                        Text(
                                            when (modo) {
                                                ModoAtivacao.MANUAL -> "Modo Manual"
                                                ModoAtivacao.AUTOMATICA -> "Modo Automático"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            
                            if (tempoRestante != null) {
                                HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f))
                                val dias = tempoRestante / (1000 * 60 * 60 * 24)
                                val horas = (tempoRestante % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                                
                                Text(
                                    "Ativação em: ${dias}d ${horas}h",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonOrange else NeonOrangeContrast,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (dataAgendada != null) {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    Text(
                                        "Data: ${sdf.format(dataAgendada)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    
                    // Seção Feedbacks
                    SectionHeader(title = "Feedbacks")

                    GlassCard {
                        OutlinedButton(
                            onClick = onNavigateToFeedbacks,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonCyan else Color(0xFF00838F))
                        ) {
                            Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver Feedbacks dos Admins", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f))

                    // Planos e Assinatura (exibir para clientes)
                    SectionHeader(title = "Planos e Assinatura")

                    GlassCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Exibir para clientes",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Mostra o botão \"Planos e Assinatura\" em Configurações e o banner de trial expirado. Ative após configurar produtos no Play Console.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode
                                    )
                                }
                                Switch(
                                    checked = config?.planosHabilitados == true,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            val repo = SistemaRepository()
                                            val ok = repo.setPlanosHabilitados(checked)
                                            if (ok) {
                                                config = repo.getConfiguracao(forceRefresh = true)
                                                message = if (checked) "Planos exibidos para clientes" else "Planos ocultos"
                                            } else {
                                                error = "Erro ao atualizar"
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = NeonGreen
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f))

                    // Modo de ativação
                    SectionHeader(title = "Modo de Ativação")
                    
                    GlassCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Radio Manual
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = modoSelecionado == ModoAtivacao.MANUAL,
                                        onClick = {
                                            modoSelecionado = ModoAtivacao.MANUAL
                                            dataAgendada = null
                                        }
                                    )
                                    Column {
                                    Text(
                                        "Manual",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Ativar imediatamente",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    }
                                }
                            }
                            
                            HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f))
                            
                            // Radio Automática
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = modoSelecionado == ModoAtivacao.AUTOMATICA,
                                        onClick = {
                                            modoSelecionado = ModoAtivacao.AUTOMATICA
                                            mostrarSeletorData = true
                                        }
                                    )
                                    Column {
                                    Text(
                                        "Automática",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Agendar ativação",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    }
                                }
                            }
                            
                            // Seletor de data (se automática selecionada)
                            if (modoSelecionado == ModoAtivacao.AUTOMATICA) {
                                HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (dataAgendada != null) {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    Text(
                                        "Data: ${sdf.format(dataAgendada)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonCyan else Color(0xFF00838F),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Button(
                                    onClick = { mostrarSeletorData = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonBlue else NeonBlueContrast, contentColor = Color.White)
                                ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (dataAgendada == null) "Selecionar Data" else "Alterar Data",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                                }
                            }
                        }
                    }
                    
                    // Toggle para ativação manual imediata
                    if (config?.monetizacaoAtiva != true) {
                        SectionHeader(title = "Ação Rápida")
                        GlassCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Ativar Manualmente",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Sobrescreve agendamento",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Switch(
                                    checked = false,
                                    onCheckedChange = { ativar ->
                                        if (ativar) {
                                            mostrarConfirmacaoAtivacao = true
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        SectionHeader(title = "Ação Rápida")
                        GlassCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Desativar Monetização",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Desativa regras de monetização",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { desativar() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF4444),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Desativar")
                                }
                            }
                        }
                    }
                    
                    // Aviso
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonOrange.copy(alpha = 0.2f) else NeonOrangeContrast.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonOrange else NeonOrangeContrast,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "Ao ativar, todas as bases seguirão as regras de monetização (limite de 5 motoristas no plano gratuito).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Botões de ação
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (modoSelecionado != null || config?.dataAtivacaoAutomatica != null) {
                            OutlinedButton(
                                onClick = { salvarConfiguracao() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonGreen else NeonGreenContrast)
                            ) {
                                Text(
                                    "Salvar",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        if (config?.dataAtivacaoAutomatica != null && !config!!.monetizacaoAtiva) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        sistemaRepository.cancelarAgendamento(superAdminId)
                                        message = "Agendamento cancelado"
                                        config = sistemaRepository.getConfiguracao()
                                        dataAgendada = null
                                        modoSelecionado = null
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Text(
                                    "Cancelar",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    // Mensagens
                    message?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NeonGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.padding(16.dp),
                                color = Color.Black
                            )
                        }
                    }
                    
                    error?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                err,
                                modifier = Modifier.padding(16.dp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo de confirmação de ativação
    if (mostrarConfirmacaoAtivacao) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacaoAtivacao = false },
            title = {
                Text(
                    "Confirmar Ativação",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Ativar monetização agora? Todas as bases seguirão as regras (limite de 5 motoristas no plano gratuito).",
                    color = TextGray,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ativarManual()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacaoAtivacao = false }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            containerColor = DarkSurface
        )
    }
    
    // Seletor de data (simplificado - em produção usar DatePicker)
    if (mostrarSeletorData) {
        // TODO: Implementar DatePicker adequado
        // Por enquanto, usar um diálogo simples
        AlertDialog(
            onDismissRequest = { mostrarSeletorData = false },
            title = { Text("Selecionar Data", color = TextWhite) },
            text = {
                Text(
                    "Em produção, aqui haverá um DatePicker completo. Por enquanto, a data será definida programaticamente.",
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Definir data para 7 dias a partir de agora (exemplo)
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_MONTH, 7)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        dataAgendada = calendar.time
                        mostrarSeletorData = false
                    }
                ) {
                    Text("Definir para 7 dias")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarSeletorData = false }) {
                    Text("Cancelar")
                }
            },
            containerColor = DarkSurface
        )
    }
}

