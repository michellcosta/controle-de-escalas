package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.DashboardStats
import com.controleescalas.app.data.models.FeedbackStatus
import com.controleescalas.app.data.models.SistemaConfig
import com.controleescalas.app.data.repositories.FeedbackRepository
import com.controleescalas.app.data.repositories.SistemaRepository
import com.controleescalas.app.data.repositories.StatsRepository
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SuperAdminDashboardScreen(
    superAdminId: String,
    onNavigateToTransportadoras: ((String?) -> Unit)? = null,
    onNavigateToUsuarios: (() -> Unit)? = null,
    onNavigateToRelatorios: ((String?) -> Unit)? = null,
    onNavigateToFeedbacks: (() -> Unit)? = null,
    onNavigateToConfiguracoes: (() -> Unit)? = null
) {
    val statsRepository = StatsRepository()
    val feedbackRepository = FeedbackRepository()
    val sistemaRepository = SistemaRepository()
    val scope = rememberCoroutineScope()
    
    var stats by remember { mutableStateOf<DashboardStats?>(null) }
    var novosFeedbacksCount by remember { mutableStateOf(0) }
    var config by remember { mutableStateOf<SistemaConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            stats = statsRepository.getDashboardStats()
            config = sistemaRepository.getConfiguracao(forceRefresh = true)
            try {
                val feedbacks = feedbackRepository.getAllFeedbacks()
                novosFeedbacksCount = feedbacks.count { it.status == FeedbackStatus.NOVO }
            } catch (_: Exception) {}
            isLoading = false
        }
    }
    
    // Funções de navegação
    val navigateToTransportadoras = onNavigateToTransportadoras ?: {}
    val navigateToFeedbacks = onNavigateToFeedbacks ?: {}
    val navigateToConfiguracoes = onNavigateToConfiguracoes ?: {}
    val navigateToUsuarios = onNavigateToUsuarios ?: {}
    val navigateToRelatorios = onNavigateToRelatorios ?: {}
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading) {
            // Skeleton loading
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(400.dp)
            ) {
                items(4) {
                    MetricCardSkeleton()
                }
            }
        } else {
            stats?.let { dashboardStats ->
                // Cards de métricas principais com gradientes
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(400.dp)
                ) {
                    item {
                        GradientCard(
                            title = "Transportadoras",
                            value = dashboardStats.totalTransportadoras.toString(),
                            subtitle = "${dashboardStats.transportadorasAtivas} ativas",
                            icon = Icons.Default.Business,
                            gradientColors = listOf(GradientBlueStart, GradientBlueEnd),
                            trend = null, // Removido: tendência hardcoded
                            onClick = {
                                navigateToTransportadoras(null)
                            }
                        )
                    }
                    
                    item {
                        GradientCard(
                            title = "Motoristas",
                            value = dashboardStats.totalMotoristas.toString(),
                            subtitle = "Total no sistema",
                            icon = Icons.Default.People,
                            gradientColors = listOf(GradientGreenStart, GradientGreenEnd),
                            trend = null, // Removido: tendência hardcoded
                            onClick = {
                                navigateToUsuarios()
                            }
                        )
                    }
                    
                    item {
                        GradientCard(
                            title = "Escalas Hoje",
                            value = dashboardStats.escalasHoje.toString(),
                            subtitle = "${dashboardStats.escalasEstaSemana} esta semana",
                            icon = Icons.Default.DateRange,
                            gradientColors = listOf(GradientOrangeStart, GradientOrangeEnd),
                            trend = null, // Removido: tendência hardcoded
                            onClick = {
                                navigateToRelatorios("operacional")
                            }
                        )
                    }
                    
                    item {
                        GradientCard(
                            title = "Receita Mensal",
                            value = "R$ ${String.format("%.2f", dashboardStats.receitaMensal)}",
                            subtitle = "${dashboardStats.basesPremium} premium",
                            icon = Icons.Default.AttachMoney,
                            gradientColors = listOf(GradientPurpleStart, GradientPurpleEnd),
                            trend = null, // Removido: tendência hardcoded
                            onClick = {
                                navigateToRelatorios("financeiro")
                            }
                        )
                    }
                }
                
                // Card de ação rápida (transportadoras pendentes)
                if (dashboardStats.transportadorasPendentes > 0) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navigateToTransportadoras("pendente") }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ação Necessária",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dashboardStats.transportadorasPendentes} aguardando aprovação",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonOrange,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                // Card de novos feedbacks
                if (novosFeedbacksCount > 0) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navigateToFeedbacks() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Feedbacks Novos",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$novosFeedbacksCount aguardando leitura",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.Feedback,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                // Atalho Configurações do Sistema
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navigateToConfiguracoes() }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = "Configurações do Sistema",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Planos, monetização e exibição para clientes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // ✅ NOVO: Toggle rápido para Modo Claro/Escuro
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = TextGray.copy(alpha = 0.2f)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (config?.temaHabilitado == true) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (config?.temaHabilitado == true) NeonBlue else TextGray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Modo Claro/Escuro",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (config?.temaHabilitado == true) "Visível para todos os usuários" else "Oculto para usuários (em desenvolvimento)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextGray
                                    )
                                }
                            }
                            
                            Switch(
                                checked = config?.temaHabilitado == true,
                                onCheckedChange = { isEnabled ->
                                    scope.launch {
                                        val success = sistemaRepository.setTemaHabilitado(isEnabled)
                                        if (success) {
                                            config = config?.copy(temaHabilitado = isEnabled)
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = NeonBlue
                                )
                            )
                        }
                    }
                }
                
                // Seção de status de transportadoras
                SectionHeader(title = "Status das Transportadoras")
                
                // Gráfico donut para status (mais moderno)
                GlassCard {
                    DonutChart(
                        segments = listOf(
                            PieChartSegment(
                                label = "Ativas",
                                value = dashboardStats.transportadorasAtivas.toFloat(),
                                color = NeonGreen
                            ),
                            PieChartSegment(
                                label = "Pendentes",
                                value = dashboardStats.transportadorasPendentes.toFloat(),
                                color = NeonOrange
                            ),
                            PieChartSegment(
                                label = "Rejeitadas",
                                value = dashboardStats.transportadorasRejeitadas.toFloat(),
                                color = StatusError
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onSegmentClick = { segment ->
                            // Navegar para transportadoras com filtro
                            when (segment.label) {
                                "Ativas" -> navigateToTransportadoras("ativa")
                                "Pendentes" -> navigateToTransportadoras("pendente")
                                "Rejeitadas" -> navigateToTransportadoras("rejeitada")
                            }
                        }
                    )
                }
                
                // Cards de status (mantidos para referência rápida)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "Ativas",
                        count = dashboardStats.transportadorasAtivas,
                        color = NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Pendentes",
                        count = dashboardStats.transportadorasPendentes,
                        color = NeonOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Rejeitadas",
                        count = dashboardStats.transportadorasRejeitadas,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Seção de planos (se monetização ativa)
                if (dashboardStats.receitaMensal > 0 || dashboardStats.basesPremium > 0 || dashboardStats.basesGratuitas > 0 || dashboardStats.basesEmTrial > 0) {
                    SectionHeader(title = "Distribuição de Planos")
                    
                    // Gráfico donut para planos
                    GlassCard {
                        DonutChart(
                            segments = listOf(
                                PieChartSegment(
                                    label = "Premium",
                                    value = dashboardStats.basesPremium.toFloat(),
                                    color = NeonPurple
                                ),
                                PieChartSegment(
                                    label = "Gratuito",
                                    value = dashboardStats.basesGratuitas.toFloat(),
                                    color = NeonBlue
                                ),
                                PieChartSegment(
                                    label = "Trial",
                                    value = dashboardStats.basesEmTrial.toFloat(),
                                    color = NeonOrange
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            onSegmentClick = { segment ->
                                // Navegar para transportadoras (filtro por plano pode ser adicionado depois)
                                navigateToTransportadoras(null)
                            }
                        )
                    }
                    
                    // Cards de planos (mantidos para referência rápida)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusCard(
                            title = "Premium",
                            count = dashboardStats.basesPremium,
                            color = NeonPurple,
                            modifier = Modifier.weight(1f)
                        )
                        StatusCard(
                            title = "Gratuito",
                            count = dashboardStats.basesGratuitas,
                            color = NeonBlue,
                            modifier = Modifier.weight(1f)
                        )
                        StatusCard(
                            title = "Trial",
                            count = dashboardStats.basesEmTrial,
                            color = NeonOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    GlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

