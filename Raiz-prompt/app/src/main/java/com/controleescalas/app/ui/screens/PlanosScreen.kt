package com.controleescalas.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.controleescalas.app.data.BillingManager
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.data.models.PlanoTipo
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanosScreen(
    baseId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var base by remember { mutableStateOf<Base?>(null) }
    val billingManager = remember { BillingManager(context as Activity) }
    val products by billingManager.products.collectAsState()
    val purchaseResult by billingManager.purchaseResult.collectAsState()
    
    LaunchedEffect(baseId) {
        base = BaseRepository().getBase(baseId)
    }
    
    LaunchedEffect(purchaseResult) {
        purchaseResult?.let { result ->
            when (result) {
                is BillingManager.PurchaseResult.SUCCESS -> {
                    // TODO: Atualizar plano da base no Firestore após compra
                    billingManager.clearPurchaseResult()
                }
                is BillingManager.PurchaseResult.CANCELLED -> {
                    billingManager.clearPurchaseResult()
                }
                is BillingManager.PurchaseResult.ERROR -> {
                    billingManager.clearPurchaseResult()
                }
            }
        }
    }
    
    val planoAtual = base?.plano ?: "trial"
    val diasRestantes = base?.let {
        if (it.plano == "trial" && it.dataFimTrial != null) {
            val restante = (it.dataFimTrial!! - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
            maxOf(0, restante.toInt())
        } else null
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planos", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status atual
            if (planoAtual == "trial" && diasRestantes != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = NeonGreen)
                            Text(
                                "Versão Beta",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            "Acesso gratuito para testes",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Aproveite todos os recursos durante o período de testes.",
                            color = TextGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Toggle Mensal / Anual
            var isAnual by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isAnual,
                    onClick = { isAnual = false },
                    label = { Text("Mensal") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = isAnual,
                    onClick = { isAnual = true },
                    label = { Text("Anual") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                if (isAnual) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Economize 15%",
                        color = NeonGreen,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            SectionHeader(title = "Planos disponíveis")
            
            // PRO
            PlanoCard(
                plano = PlanoTipo.PRO,
                isAnual = isAnual,
                descricao = "1 transportadora\nMotoristas ilimitados\nEscalas, vagas, histórico\nRelatórios e backup",
                isAtual = planoAtual == "pro",
                productDetails = products.find { it.productId == PlanoTipo.PRO.productId(isAnual) },
                onAssinarClick = { product ->
                    billingManager.launchPurchaseFlow(product)
                }
            )
            
            // MULTI
            PlanoCard(
                plano = PlanoTipo.MULTI,
                isAnual = isAnual,
                descricao = "Até 5 transportadoras\nTudo do PRO\nGestão multi-frota",
                isAtual = planoAtual == "multi",
                productDetails = products.find { it.productId == PlanoTipo.MULTI.productId(isAnual) },
                onAssinarClick = { product ->
                    billingManager.launchPurchaseFlow(product)
                }
            )
            
            // MULTI PRO
            PlanoCard(
                plano = PlanoTipo.MULTI_PRO,
                isAnual = isAnual,
                descricao = "Até 15 transportadoras\nTudo do MULTI\nSuporte prioritário",
                isAtual = planoAtual == "multi_pro",
                productDetails = products.find { it.productId == PlanoTipo.MULTI_PRO.productId(isAnual) },
                onAssinarClick = { product ->
                    billingManager.launchPurchaseFlow(product)
                }
            )
            
            // ENTERPRISE
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        PlanoTipo.ENTERPRISE.nome,
                        color = NeonPurple,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Sob consulta",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Ilimitado + customizações\nMulti usuários\nPermissões avançadas\nSLA e suporte dedicado",
                        color = TextGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { /* Contato para Enterprise */ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                    ) {
                        Text("Entre em contato")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanoCard(
    plano: PlanoTipo,
    isAnual: Boolean,
    descricao: String,
    isAtual: Boolean,
    productDetails: ProductDetails?,
    onAssinarClick: (ProductDetails) -> Unit
) {
    val precoTexto = if (isAnual && plano.precoAnual > 0) {
        "R$ ${plano.precoAnual.toInt()}/ano"
    } else {
        "R$ ${plano.precoMensal.toInt()}/mês"
    }
    val subtituloPreco = if (isAnual && plano.precoAnual > 0) {
        "R$ ${plano.precoMensalEquivalenteAnual.toInt()}/mês · Economize R$ ${plano.economiaAnual.toInt()}"
    } else null

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    plano.nome,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (isAtual) {
                    Surface(
                        color = NeonGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atual", color = NeonGreen, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(
                precoTexto,
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            if (subtituloPreco != null) {
                Text(
                    subtituloPreco,
                    color = TextGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                descricao,
                color = TextGray,
                style = MaterialTheme.typography.bodySmall
            )
            if (!isAtual && productDetails != null) {
                Button(
                    onClick = { productDetails?.let { onAssinarClick(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Assinar")
                }
            } else if (!isAtual && productDetails == null) {
                Text(
                    "Configurando... (produtos no Play Console)",
                    color = TextGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
