package com.controleescalas.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.QuinzenaResumo
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.QuinzenaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuinzenaListScreen(
    baseId: String,
    onBack: () -> Unit,
    onMotoristaClick: (String) -> Unit, // Callback para quando clicar no card do motorista
    quinzenaViewModel: QuinzenaViewModel = viewModel()
) {
    val todasQuinzenas by quinzenaViewModel.todasQuinzenas.collectAsState()
    val isLoading by quinzenaViewModel.isLoading.collectAsState()
    
    // ✅ NOVO: Estado para pesquisa
    var searchQuery by remember { mutableStateOf("") }
    
    // Carregar dados ao iniciar
    LaunchedEffect(baseId) {
        quinzenaViewModel.carregarTodasQuinzenas(baseId)
    }
    
    val resumos = remember(todasQuinzenas) {
        quinzenaViewModel.gerarResumos()
    }
    
    // ✅ NOVO: Filtrar resumos por nome do motorista
    val filteredResumos = remember(resumos, searchQuery) {
        if (searchQuery.isBlank()) {
            resumos
        } else {
            val query = searchQuery.lowercase().trim()
            resumos.filter { resumo ->
                resumo.motoristaNome.lowercase().contains(query)
            }
        }
    }
    
    val calendar = Calendar.getInstance()
    val mesAtual = SimpleDateFormat("MMMM/yyyy", Locale("pt", "BR"))
        .format(calendar.time)
        .replaceFirstChar { it.uppercase() }
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Controle de Quinzena",
                subtitle = mesAtual,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        },
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MeliBlue
                )
            } else if (resumos.isEmpty()) {
                // Estado vazio
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = TextGrayLightMode,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum registro de quinzena",
                        fontSize = 16.sp,
                        color = TextGrayLightMode
                    )
                    Text(
                        text = "Os dias trabalhados aparecerão aqui",
                        fontSize = 14.sp,
                        color = TextGrayLightMode.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ✅ NOVO: Campo de pesquisa acima do Resumo Geral
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            placeholder = { Text("Pesquisar...", color = TextGrayLightMode) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Pesquisar",
                                    tint = MeliBlue
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextBlack,
                                unfocusedTextColor = TextBlack,
                                focusedBorderColor = MeliBlue,
                                unfocusedBorderColor = CardBorderLight,
                                focusedLabelColor = MeliBlue,
                                unfocusedLabelColor = TextGrayLightMode,
                                focusedPlaceholderColor = TextDisabled,
                                unfocusedPlaceholderColor = TextDisabled
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    item {
                        SectionHeader(
                            title = "Resumo Geral - ${filteredResumos.size} motoristas"
                        )
                    }
                    
                    // ✅ MUDANÇA: Usar filteredResumos em vez de resumos
                    items(filteredResumos) { resumo ->
                        QuinzenaResumoCard(
                            resumo = resumo,
                            onClick = { onMotoristaClick(resumo.motoristaId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card de resumo de quinzena para cada motorista
 */
@Composable
private fun QuinzenaResumoCard(
    resumo: QuinzenaResumo,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Nome do motorista
            Text(
                text = resumo.motoristaNome,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextBlack
            )
            
            // Estatísticas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuinzenaStatItem(
                    label = "1ª Quinzena",
                    valor = resumo.primeiraQuinzenaDias,
                    cor = MeliBlue
                )
                
                QuinzenaStatItem(
                    label = "2ª Quinzena",
                    valor = resumo.segundaQuinzenaDias,
                    cor = NeonPurple
                )
                
                QuinzenaStatItem(
                    label = "Total",
                    valor = resumo.totalDias,
                    cor = MeliBlue
                )
            }
        }
    }
}

/**
 * Item de estatística individual
 */
@Composable
private fun QuinzenaStatItem(
    label: String,
    valor: Int,
    cor: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextGrayLightMode
        )
        Text(
            text = "$valor",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = cor
        )
    }
}
