package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.controleescalas.app.ui.theme.DarkSurface
import com.controleescalas.app.ui.theme.NeonGreen
import com.controleescalas.app.ui.theme.NeonOrange
import com.controleescalas.app.ui.theme.TextGray
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

@Composable
fun MainAppScaffold(
    baseId: String,
    onLogout: () -> Unit,
    onNavigateToLocationConfig: () -> Unit,
    onNavigateToUserManagement: () -> Unit
) {
    val navController = rememberNavController()
    var trialExpirado by remember { mutableStateOf(false) }
    var planosHabilitados by remember { mutableStateOf(false) }
    
    LaunchedEffect(baseId) {
        if (baseId.isNotEmpty()) {
            val base = BaseRepository().getBase(baseId)
            trialExpirado = base?.trialExpirado() == true
        }
        val config = SistemaRepository().getConfiguracao()
        planosHabilitados = config.planosHabilitados
    }
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
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
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Operation.route,
                modifier = Modifier
                    .weight(1f)
                    .padding(paddingValues)
            ) {
            // Log quando o NavHost é criado
            println("🔵 MainAppScaffold: NavHost criado, startDestination: ${BottomNavItem.Operation.route}")
            android.util.Log.e("DEBUG", "🔵 MainAppScaffold: NavHost criado")
            composable(BottomNavItem.Assistente.route) {
                val operationEntry = navController.getBackStackEntry(BottomNavItem.Operation.route)
                val operationalViewModel: OperationalViewModel = viewModel(operationEntry!!)
                AssistenteScreen(
                    baseId = baseId,
                    onBack = { navController.popBackStack(BottomNavItem.Operation.route, false) },
                    onAddToScaleAction = { motoristaId, nome, ondaIndex, vaga, rota, sacas ->
                        operationalViewModel.addMotoristaToOndaWithDetails(ondaIndex, motoristaId, nome, vaga, rota, sacas)
                    }
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
                    onQuinzenaClick = { navController.navigate(Routes.QuinzenaList.route) },
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
                android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: AdminDevolucoes route composable REGISTERED - route: ${Routes.AdminDevolucoes.route}")
                println("🔵 [HYP-C] MainAppScaffold: AdminDevolucoes route composable registered - route: ${Routes.AdminDevolucoes.route}")
                android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: About to create ViewModel")
                println("🔵 [HYP-C] MainAppScaffold: About to create ViewModel")
                val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: ViewModel created successfully")
                println("🔵 [HYP-C] MainAppScaffold: ViewModel created successfully")
                android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: About to call AdminDevolucoesScreen")
                println("🔵 [HYP-C] MainAppScaffold: About to call AdminDevolucoesScreen")
                AdminDevolucoesScreen(
                    baseId = baseId,
                    onDismiss = { navController.popBackStack() },
                    onMotoristaClick = { motoristaId, motoristaNome ->
                        android.util.Log.e("DEBUG", "🔵 [HYP-B] MainAppScaffold: onMotoristaClick CALLBACK RECEIVED - motoristaId: $motoristaId, nome: $motoristaNome")
                        println("🔵 [HYP-B] MainAppScaffold: onMotoristaClick callback received - motoristaId: $motoristaId, nome: $motoristaNome")
                        android.util.Log.e("DEBUG", "🔵 [HYP-B] MainAppScaffold: navController is null: ${navController == null}")
                        println("🔵 [HYP-B] MainAppScaffold: navController is null: ${navController == null}")
                        // Usar apenas motoristaId na rota para evitar problemas com caracteres especiais
                        val route = "${Routes.AdminDevolucaoDetalhes.route}/$motoristaId"
                        android.util.Log.e("DEBUG", "🔵 [HYP-D] MainAppScaffold: Route constructed - baseRoute: ${Routes.AdminDevolucaoDetalhes.route}, fullRoute: $route")
                        println("🔵 [HYP-D] MainAppScaffold: Route constructed - baseRoute: ${Routes.AdminDevolucaoDetalhes.route}, fullRoute: $route")
                        android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: About to call navController.navigate()")
                        println("🔵 [HYP-C] MainAppScaffold: About to call navController.navigate()")
                        try {
                            navController.navigate(route) {
                                android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: navigate() lambda executed")
                                println("🔵 [HYP-C] MainAppScaffold: navigate() lambda executed")
                            }
                            android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: navigate() completed without exception")
                            println("🔵 [HYP-C] MainAppScaffold: navigate() completed without exception")
                        } catch (e: Exception) {
                            android.util.Log.e("DEBUG", "❌ [HYP-C] MainAppScaffold: Exception in navigate() - ${e.javaClass.simpleName}: ${e.message}")
                            println("❌ [HYP-C] MainAppScaffold: Exception in navigate() - ${e.javaClass.simpleName}: ${e.message}")
                            e.printStackTrace()
                        }
                    },
                    viewModel = devolucaoViewModel
                )
                android.util.Log.e("DEBUG", "🔵 [HYP-C] MainAppScaffold: AdminDevolucoesScreen call completed")
                println("🔵 [HYP-C] MainAppScaffold: AdminDevolucoesScreen call completed")
            }
            
            composable("${Routes.AdminDevolucaoDetalhes.route}/{motoristaId}") { backStackEntry ->
                android.util.Log.e("DEBUG", "🔵 [HYP-E] MainAppScaffold: AdminDevolucaoDetalhes composable MATCHED - route: ${backStackEntry.destination.route}")
                println("🔵 [HYP-E] MainAppScaffold: AdminDevolucaoDetalhes composable matched - route: ${backStackEntry.destination.route}")
                val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                val motoristaId = backStackEntry.arguments?.getString("motoristaId") ?: ""
                println("🔵 [HYP-E] MainAppScaffold: Extracted motoristaId: '$motoristaId' (empty: ${motoristaId.isEmpty()})")
                // Buscar o nome do motorista a partir do ID
                var motoristaNome by remember { mutableStateOf("") }
                LaunchedEffect(motoristaId, baseId) {
                    if (motoristaId.isNotEmpty() && baseId.isNotEmpty()) {
                        val repository = com.controleescalas.app.data.repositories.MotoristaRepository()
                        val nome = repository.getMotoristaNome(motoristaId, baseId)
                        motoristaNome = nome ?: "Motorista"
                    }
                }
                println("🔵 [HYP-E] MainAppScaffold: About to render AdminDevolucaoDetalhesScreen - motoristaNome: '$motoristaNome', baseId: '$baseId'")
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

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem.Operation,
        BottomNavItem.Availability,
        BottomNavItem.Configuration
    )
    
    NavigationBar(
        containerColor = DarkSurface,
        contentColor = Color.White
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
                    selectedIconColor = Color.Black,
                    selectedTextColor = NeonGreen,
                    indicatorColor = NeonGreen,
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray
                )
            )
        }
    }
}
