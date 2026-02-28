package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.controleescalas.app.navigation.Routes
import com.controleescalas.app.ui.navigation.BottomNavItem
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.SistemaRepository
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.theme.DarkSurface
import com.controleescalas.app.ui.theme.NeonGreen
import com.controleescalas.app.ui.theme.NeonOrange
import com.controleescalas.app.ui.theme.TextGray
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import com.controleescalas.app.ui.theme.DeepBlue
import com.controleescalas.app.ui.theme.DarkBackground
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import com.controleescalas.app.ui.viewmodels.BulkScaleAction
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

@Composable
fun MainAppScaffold(
    baseId: String,
    userId: String = "",
    userRole: String = "",
    onLogout: () -> Unit,
    onNavigateToLocationConfig: () -> Unit,
    onNavigateToUserManagement: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var assistenteInputFocused by remember { mutableStateOf(false) }
    var trialExpirado by remember { mutableStateOf(false) }
    var planosHabilitados by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != BottomNavItem.Assistente.route) {
            assistenteInputFocused = false
        }
    }

    LaunchedEffect(baseId) {
        if (baseId.isNotEmpty()) {
            val base = BaseRepository().getBase(baseId)
            trialExpirado = base?.trialExpirado() == true
        }
        val config = SistemaRepository().getConfiguracao()
        planosHabilitados = config.planosHabilitados
    }
    
    val hideBottomBar = currentRoute == BottomNavItem.Assistente.route && assistenteInputFocused

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                PremiumBottomNavigationBar(navController = navController)
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            // Banner: Trial expirado - faça upgrade (apenas quando planos estão habilitados)
            if (trialExpirado && planosHabilitados) {
                Surface(
                    color = NeonOrange.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(20.dp))
                            Text(
                                "Trial expirado. Escolha um plano para continuar.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = { navController.navigate(Routes.Planos.route) }) {
                            Text("Ver planos", color = NeonGreen)
                        }
                    }
                }
            }
            PremiumBackground(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Operation.route,
                    modifier = Modifier.padding(paddingValues)
                ) {
                    // Log quando o NavHost é criado
                    println("🔵 MainAppScaffold: NavHost criado, startDestination: ${BottomNavItem.Operation.route}")
                    android.util.Log.e("DEBUG", "🔵 MainAppScaffold: NavHost criado")
                    composable(BottomNavItem.Assistente.route) {
                        val operationEntry = navController.getBackStackEntry(BottomNavItem.Operation.route)
                        val operationalViewModel: OperationalViewModel = viewModel(operationEntry!!)
                        val currentTurno by operationalViewModel.turnoAtual.collectAsState()
                        AssistenteScreen(
                            baseId = baseId,
                            userId = userId,
                            userRole = userRole,
                            onBack = { navController.popBackStack(BottomNavItem.Operation.route, false) },
                            onAddToScaleAction = { motoristaId, nome, ondaIndex, vaga, rota, sacas ->
                                operationalViewModel.addMotoristaToOndaWithDetails(ondaIndex, motoristaId, nome, vaga, rota, sacas)
                            },
                            onUpdateInScaleAction = { motoristaId, ondaIndex, vaga, rota, sacas ->
                                operationalViewModel.updateMotoristaInOndaByDetails(ondaIndex, motoristaId, vaga, rota, sacas)
                            },
                            onBulkActions = { actions ->
                                operationalViewModel.bulkApplyScaleActions(actions)
                            },
                            onSendNotification = { motoristaId, nome, body ->
                                operationalViewModel.sendNotificationBatch(motoristaId, nome, body)
                            },
                            onInputFocusChange = { focused -> assistenteInputFocused = focused },
                            turno = currentTurno
                        )
                    }
                    composable(BottomNavItem.Operation.route) {
                        // OperationalDashboardScreen mostra a aba de Operação/Ondas (Operações do Dia)
                        OperationalDashboardScreen(
                            baseId = baseId,
                            onOpenAssistente = { navController.navigate(BottomNavItem.Assistente.route) }
                        )
                    }
                    composable(BottomNavItem.Availability.route) {
                        // Nova tela de Disponibilidade
                        val disponibilidadeViewModel: com.controleescalas.app.ui.viewmodels.DisponibilidadeViewModel = viewModel()
                        AvailabilityScreen(
                            baseId = baseId,
                            viewModel = disponibilidadeViewModel
                        )
                    }
                    composable(BottomNavItem.Configuration.route) {
                        AdminPanelScreen(
                            baseId = baseId,
                            onEscalaClick = { /* Removido */ },
                            onLocationConfigClick = onNavigateToLocationConfig,
                            onUserManagementClick = onNavigateToUserManagement,
                            onQuinzenaClick = { navController.navigate("quinzena_list") },
                            onDevolucaoClick = { navController.navigate(Routes.AdminDevolucoes.route) },
                            onPlanosClick = { navController.navigate(Routes.Planos.route) },
                            showPlanosButton = planosHabilitados,
                            onFeedbackClick = { navController.navigate(Routes.AdminFeedback.route) },
                            onLogout = onLogout
                        )
                    }
                    composable(Routes.Planos.route) {
                        PlanosScreen(
                            baseId = baseId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("quinzena_list") {
                        QuinzenaListScreen(
                            baseId = baseId,
                            onBack = { navController.popBackStack() },
                            onMotoristaClick = { motoristaId ->
                                navController.navigate("quinzena_motorista/$motoristaId")
                            }
                        )
                    }
                    composable("quinzena_motorista/{motoristaId}") { backStackEntry ->
                        val motoristaId = backStackEntry.arguments?.getString("motoristaId") ?: ""
                        val quinzenaViewModel: com.controleescalas.app.ui.viewmodels.QuinzenaViewModel = viewModel()
                        DriverQuinzenaScreen(
                            baseId = baseId,
                            motoristaId = motoristaId,
                            viewModel = quinzenaViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.AdminFeedback.route) {
                        val adminId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        AdminFeedbackScreen(
                            baseId = baseId,
                            adminId = adminId,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Rotas de Devolução (Admin)
                    composable(Routes.AdminDevolucoes.route) {
                        val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                        AdminDevolucoesScreen(
                            baseId = baseId,
                            onDismiss = { navController.popBackStack() },
                            onMotoristaClick = { motoristaId, motoristaNome ->
                                val route = "${Routes.AdminDevolucaoDetalhes.route}/$motoristaId"
                                navController.navigate(route)
                            },
                            viewModel = devolucaoViewModel
                        )
                    }
                    
                    composable("${Routes.AdminDevolucaoDetalhes.route}/{motoristaId}") { backStackEntry ->
                        val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                        val motoristaId = backStackEntry.arguments?.getString("motoristaId") ?: ""
                        var motoristaNome by remember { mutableStateOf("") }
                        LaunchedEffect(motoristaId, baseId) {
                            if (motoristaId.isNotEmpty() && baseId.isNotEmpty()) {
                                val repository = com.controleescalas.app.data.repositories.MotoristaRepository()
                                val nome = repository.getMotoristaNome(motoristaId, baseId)
                                motoristaNome = nome ?: "Motorista"
                            }
                        }
                        AdminDevolucaoDetalhesScreen(
                            baseId = baseId,
                            motoristaId = motoristaId,
                            motoristaNome = motoristaNome,
                            onDismiss = { navController.popBackStack() },
                            viewModel = devolucaoViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumBottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem.Operation,
        BottomNavItem.Availability,
        BottomNavItem.Configuration
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            border = BorderStroke(
                width = 0.5.dp,
                color = (if (isDarkMode) Color.White else Color.Black).copy(alpha = 0.15f)
            ),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    
                    val animatedWeight by animateFloatAsState(
                        targetValue = if (isSelected) 2.2f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "weight"
                    )
                    
                    val animatedColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else TextGray,
                        label = "color"
                    )

                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(animatedWeight)
                            .height(48.dp)
                            .padding(horizontal = 2.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) 
                                        else Color.Transparent
                            )
                            .clickable {
                                if (currentRoute == BottomNavItem.Assistente.route) {
                                    navController.popBackStack(BottomNavItem.Operation.route, false)
                                    if (item != BottomNavItem.Operation) {
                                        navController.navigate(item.route)
                                    }
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = animatedColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(animatedScale)
                            )
                            
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    color = animatedColor,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem.Operation,
        BottomNavItem.Availability,
        BottomNavItem.Configuration
    )
    
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute == BottomNavItem.Assistente.route) {
                        navController.popBackStack(BottomNavItem.Operation.route, false)
                        if (item != BottomNavItem.Operation) {
                            navController.navigate(item.route)
                        }
                    } else {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray
                )
            )
        }
    }
}
