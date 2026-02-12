package com.controleescalas.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.DarkBackground
import com.controleescalas.app.data.repositories.StatsRepository
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SuperAdminRelatoriosScreen(
    superAdminId: String,
    initialTab: String? = null
) {
    val statsRepository = StatsRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var stats by remember { mutableStateOf<com.controleescalas.app.data.models.DashboardStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    
    // Tab selecionada
    var selectedTab by remember { mutableStateOf(initialTab ?: "visao_geral") }
    
    // Launcher para compartilhar
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}
    
    LaunchedEffect(Unit) {
        scope.launch {
            stats = statsRepository.getDashboardStats()
            isLoading = false
        }
    }
    
    // Limpar mensagens
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }
    
    // Função para exportar relatório CSV
    fun exportarRelatorioCSV() {
        try {
            val statsAtual = stats ?: return
            
            val csvContent = buildString {
                appendLine("Relatório do Sistema - ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
                appendLine()
                appendLine("TRANSPORTADORAS")
                appendLine("Total,${statsAtual.totalTransportadoras}")
                appendLine("Ativas,${statsAtual.transportadorasAtivas}")
                appendLine("Pendentes,${statsAtual.transportadorasPendentes}")
                appendLine("Rejeitadas,${statsAtual.transportadorasRejeitadas}")
                appendLine()
                appendLine("MOTORISTAS")
                appendLine("Total,${statsAtual.totalMotoristas}")
                appendLine()
                appendLine("ESCALAS")
                appendLine("Hoje,${statsAtual.escalasHoje}")
                appendLine("Esta Semana,${statsAtual.escalasEstaSemana}")
                appendLine()
                appendLine("RECEITA")
                appendLine("Mensal,R$ ${String.format("%.2f", statsAtual.receitaMensal)}")
                appendLine()
                appendLine("PLANOS")
                appendLine("Premium,${statsAtual.basesPremium}")
                appendLine("Gratuito,${statsAtual.basesGratuitas}")
                appendLine("Trial,${statsAtual.basesEmTrial}")
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Relatório do Sistema - ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}")
                putExtra(Intent.EXTRA_TEXT, csvContent)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "Exportar Relatório")
            shareLauncher.launch(chooserIntent)
            
            message = "Relatório pronto para compartilhar!"
        } catch (e: Exception) {
            message = "Erro ao exportar: ${e.message}"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonGreen)
            }
        } else {
            stats?.let { dashboardStats ->
                // Tabs de navegação
                TabRow(
                    selectedTabIndex = when (selectedTab) {
                        "visao_geral" -> 0
                        "financeiro" -> 1
                        "operacional" -> 2
                        "exportar" -> 3
                        else -> 0
                    },
                    containerColor = DarkSurface,
                    contentColor = TextWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == "visao_geral",
                        onClick = { selectedTab = "visao_geral" },
                        text = {
                            Text(
                                "Visão Geral",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == "financeiro",
                        onClick = { selectedTab = "financeiro" },
                        text = {
                            Text(
                                "Financeiro",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.AttachMoney,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == "operacional",
                        onClick = { selectedTab = "operacional" },
                        text = {
                            Text(
                                "Operacional",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == "exportar",
                        onClick = { selectedTab = "exportar" },
                        text = {
                            Text(
                                "Exportar",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Conteúdo das tabs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        "visao_geral" -> {
                            VisaoGeralTab(dashboardStats = dashboardStats)
                        }
                        "financeiro" -> {
                            FinanceiroTab(dashboardStats = dashboardStats)
                        }
                        "operacional" -> {
                            OperacionalTab(dashboardStats = dashboardStats)
                        }
                        "exportar" -> {
                            ExportarTab(
                                dashboardStats = dashboardStats,
                                onExportar = { exportarRelatorioCSV() }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Mensagens
    message?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = if (msg.contains("sucesso") || msg.contains("pronto")) NeonGreen else Color(0xFFEF4444),
            contentColor = if (msg.contains("sucesso") || msg.contains("pronto")) Color.Black else Color.White
        ) {
            Text(msg)
        }
    }
}

@Composable
fun VisaoGeralTab(dashboardStats: com.controleescalas.app.data.models.DashboardStats) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status das Transportadoras
        SectionHeader(title = "Status das Transportadoras")
        
        GlassCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusReportItem(
                        title = "Ativas",
                        count = dashboardStats.transportadorasAtivas,
                        color = NeonGreen
                    )
                    StatusReportItem(
                        title = "Pendentes",
                        count = dashboardStats.transportadorasPendentes,
                        color = NeonOrange
                    )
                    StatusReportItem(
                        title = "Rejeitadas",
                        count = dashboardStats.transportadorasRejeitadas,
                        color = StatusError
                    )
                }
            }
        }
        
        // Distribuição de Planos
        if (dashboardStats.basesPremium > 0 || dashboardStats.basesGratuitas > 0 || dashboardStats.basesEmTrial > 0) {
            SectionHeader(title = "Distribuição de Planos")
            
            GlassCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusReportItem(
                            title = "Premium",
                            count = dashboardStats.basesPremium,
                            color = NeonPurple
                        )
                        StatusReportItem(
                            title = "Gratuito",
                            count = dashboardStats.basesGratuitas,
                            color = NeonBlue
                        )
                        StatusReportItem(
                            title = "Trial",
                            count = dashboardStats.basesEmTrial,
                            color = NeonOrange
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceiroTab(dashboardStats: com.controleescalas.app.data.models.DashboardStats) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(title = "Relatórios Financeiros")
        
        GlassCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Receita Mensal",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "R$ ${String.format("%.2f", dashboardStats.receitaMensal)}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonGreen
                )
                
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                
                // Gráfico donut para planos
                if (dashboardStats.basesPremium > 0 || dashboardStats.basesGratuitas > 0 || dashboardStats.basesEmTrial > 0) {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                }
                
                // Cards de planos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Premium",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.basesPremium.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "Gratuitas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.basesGratuitas.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "Trial",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.basesEmTrial.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OperacionalTab(dashboardStats: com.controleescalas.app.data.models.DashboardStats) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(title = "Relatórios Operacionais")
        
        GlassCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Uso do Sistema",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Transportadoras",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.totalTransportadoras.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "Motoristas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.totalMotoristas.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Escalas Hoje",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.escalasHoje.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "Esta Semana",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dashboardStats.escalasEstaSemana.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExportarTab(
    dashboardStats: com.controleescalas.app.data.models.DashboardStats,
    onExportar: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(title = "Exportar Relatórios")
        
        GlassCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExportar,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar Relatório (CSV)", fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Exportar relatório CSV para compartilhar ou salvar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusReportItem(
    title: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
