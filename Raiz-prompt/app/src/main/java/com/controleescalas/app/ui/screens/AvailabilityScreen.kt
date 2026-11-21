package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.AppButton
import com.controleescalas.app.ui.components.PreEscalaRapidaDialog
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DisponibilidadeViewModel
import com.controleescalas.app.ui.viewmodels.PreEscalaViewModel
import com.controleescalas.app.data.models.DisponibilidadeMotorista
import com.controleescalas.app.utils.BrasilDate
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    baseId: String,
    viewModel: DisponibilidadeViewModel = viewModel()
) {
    val disponibilidade by viewModel.disponibilidade.collectAsState()
    val message by viewModel.message.collectAsState()
    val turnosDaBase by viewModel.turnosDaBase.collectAsState()
    var turnoSelecionado by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Pré-escala rápida (quem vai trabalhar + rota → WhatsApp)
    val preEscalaViewModel: PreEscalaViewModel = viewModel()
    var showPreEscala by remember { mutableStateOf(false) }
    val preEscalaMessage by preEscalaViewModel.message.collectAsState()
    val preEscalaError by preEscalaViewModel.error.collectAsState()

    // Garantir que existe disponibilidade ao carregar (cria se não existir)
    // E iniciar observação em tempo real
    LaunchedEffect(baseId) {
        viewModel.carregarTurnosDaBase(baseId)
        viewModel.garantirDisponibilidadeParaAmanha(baseId)
        viewModel.observarDisponibilidade(baseId) // Observar em tempo real
    }

    // Acompanhar a lista de turnos: seleciona o primeiro enquanto nenhum foi escolhido
    LaunchedEffect(turnosDaBase) {
        if (turnoSelecionado !in turnosDaBase) {
            turnoSelecionado = turnosDaBase.firstOrNull()
        }
    }

    // Limpar listener quando sair da tela
    DisposableEffect(baseId) {
        onDispose {
            viewModel.pararObservacaoDisponibilidade()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Gestão de Disponibilidade",
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = "Informações sobre disponibilidade"
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            val turnoSelecionadoSeguro = turnoSelecionado ?: turnosDaBase.firstOrNull() ?: ""

            // Estatísticas DO TURNO SELECIONADO
            val motoristas = disponibilidade?.motoristas.orEmpty()
            val totalMotoristas = motoristas.size
            val totalDisponiveis = motoristas.count { it.estadoNoTurno(turnoSelecionadoSeguro) == true }
            val totalIndisponiveis = motoristas.count { it.estadoNoTurno(turnoSelecionadoSeguro) == false }
            val semResposta = motoristas.count { it.estadoNoTurno(turnoSelecionadoSeguro) == null }

            // Card de estatísticas
            if (totalMotoristas > 0) {
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalDisponiveis",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Disponíveis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalIndisponiveis",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Indisponíveis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$semResposta",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sem resposta",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Abas = turnos cadastrados da base
            Column(modifier = Modifier.weight(1f)) {
                if (turnosDaBase.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        turnosDaBase.forEach { turno ->
                            val selecionado = turno == turnoSelecionadoSeguro
                            val countTurno = motoristas.count {
                                it.estadoNoTurno(turno) != null
                            }
                            FilterChip(
                                selected = selecionado,
                                onClick = { turnoSelecionado = turno },
                                label = { Text(turno, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MeliBlue,
                                    selectedLabelColor = Color.White,
                                    labelColor = TextGrayLightMode
                                )
                            )
                        }
                    }
                }

                // Pré-escala rápida: quem vai trabalhar no turno + rota → WhatsApp
                Spacer(modifier = Modifier.height(12.dp))
                AppButton(
                    text = "📋 Escala rápida + WhatsApp",
                    onClick = { showPreEscala = true },
                    modifier = Modifier.fillMaxWidth()
                )

                // Conteúdo da aba (motoristas por estado NO TURNO selecionado)
                Spacer(modifier = Modifier.height(16.dp))

                val disponiveis = motoristas.filter { it.estadoNoTurno(turnoSelecionadoSeguro) == true }
                val indisponiveis = motoristas.filter { it.estadoNoTurno(turnoSelecionadoSeguro) == false }
                val semResposta = motoristas.filter { it.estadoNoTurno(turnoSelecionadoSeguro) == null }

                // Sub-abas de estado dentro do turno
                val estadoSelecionado = remember(turnoSelecionadoSeguro) { mutableStateOf(0) }
                val tabsEstado = listOf("Disponível", "Indisponível", "Sem Resposta")
                TabRow(
                    selectedTabIndex = estadoSelecionado.value,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabsEstado.forEachIndexed { index, title ->
                        Tab(
                            selected = estadoSelecionado.value == index,
                            onClick = { estadoSelecionado.value = index },
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = if (estadoSelecionado.value == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val count = when (index) {
                                        0 -> disponiveis.size
                                        1 -> indisponiveis.size
                                        else -> semResposta.size
                                    }
                                    if (count > 0) {
                                        Surface(
                                            color = when (index) {
                                                0 -> NeonGreen
                                                1 -> NeonOrange
                                                else -> TextGrayLightMode
                                            },
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = "$count",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val motoristasFiltrados = when (estadoSelecionado.value) {
                    0 -> disponiveis
                    1 -> indisponiveis
                    else -> semResposta
                }

                if (motoristasFiltrados.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = when (estadoSelecionado.value) {
                                    0 -> "Nenhum motorista disponível no turno $turnoSelecionadoSeguro"
                                    1 -> "Nenhum motorista indisponível no turno $turnoSelecionadoSeguro"
                                    else -> "Todos os motoristas já responderam o turno $turnoSelecionadoSeguro"
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (estadoSelecionado.value) {
                                    0 -> "Os motoristas que marcarem 'SIM' no turno aparecerão aqui"
                                    1 -> "Os motoristas que marcarem 'NÃO' no turno aparecerão aqui"
                                    else -> "Os motoristas que ainda não responderem este turno aparecerão aqui"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(motoristasFiltrados, key = { "${it.motoristaId}-${it.telefone}" }) { motorista ->
                            AvailabilityItemRow(
                                motorista = motorista,
                                estado = motorista.estadoNoTurno(turnoSelecionadoSeguro)
                            )
                        }
                    }
                }
            }
            }
        }
        }

        // Diálogo de informações sobre disponibilidade automática
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MeliBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Disponibilidade Automática",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "A disponibilidade é solicitada automaticamente todos os dias às 00:00 para o dia seguinte. Os motoristas respondem turno por turno.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        disponibilidade?.let { disp ->
                            Text(
                                text = "Disponibilidade para: ${formatarDataDisponibilidade(disp.data)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeliBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Entendi", color = MeliBlue)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Feedback (disponibilidade + pré-escala)
        val feedbackMsg = message ?: preEscalaMessage
        val feedbackErro = preEscalaError
        (feedbackMsg ?: feedbackErro)?.let { msg ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                 Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    containerColor = if (feedbackErro != null) StatusError else MeliBlue,
                    contentColor = if (feedbackErro != null) Color.White else Color.Black
                ) {
                    Text(msg)
                }
            }
        }

        // Dialog da pré-escala rápida (padrão: hoje)
        val turnoParaPreEscala = turnoSelecionado ?: turnosDaBase.firstOrNull() ?: ""
        if (showPreEscala && turnoParaPreEscala.isNotBlank()) {
            PreEscalaRapidaDialog(
                baseId = baseId,
                turno = turnoParaPreEscala,
                dataPadrao = BrasilDate.hoje(),
                onDismiss = { showPreEscala = false },
                viewModel = preEscalaViewModel
            )
        }
    }
}

@Composable
fun AvailabilityItemRow(
    motorista: DisponibilidadeMotorista,
    estado: Boolean?
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Nome do motorista (compacto)
                Text(
                    text = motorista.nome.ifEmpty { "Motorista ${motorista.motoristaId.take(8)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                // Horário da resposta (compacto)
                if (motorista.respondidoEm != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = BrasilDate.format("HH:mm", Date(motorista.respondidoEm)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Aguardando",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Indicador de status NO TURNO selecionado
            when (estado) {
                true -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Disponível",
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                false -> {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Indisponível",
                        tint = NeonOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
                null -> {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Sem resposta",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formata data de disponibilidade (YYYY-MM-DD) para exibição (DD/MM/YYYY)
 */
fun formatarDataDisponibilidade(data: String): String =
    BrasilDate.yyyyMmDdParaDdMmYyyy(data)
