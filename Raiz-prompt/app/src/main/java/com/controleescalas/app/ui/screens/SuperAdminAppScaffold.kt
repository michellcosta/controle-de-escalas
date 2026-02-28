package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NamedNavArgument
import androidx.navigation.navArgument
import com.controleescalas.app.data.repositories.FeedbackRepository
import com.controleescalas.app.data.models.FeedbackStatus
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.NotificationService
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.data.models.Feedback
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.DisposableEffect
import com.controleescalas.app.ui.components.PremiumBackground

sealed class SuperAdminNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val shortLabel: String = label // Label curto para evitar quebra de texto
) {
    object Dashboard : SuperAdminNavItem(
        route = "superadmin_dashboard",
        icon = Icons.Default.Dashboard,
        label = "Dashboard",
        shortLabel = "Início"
    )
    
    object Transportadoras : SuperAdminNavItem(
        route = "superadmin_transportadoras",
        icon = Icons.Default.Business,
        label = "Transportadoras",
        shortLabel = "Bases"
    )
    
    object Relatorios : SuperAdminNavItem(
        route = "superadmin_relatorios",
        icon = Icons.Default.BarChart,
        label = "Relatórios",
        shortLabel = "Relatórios"
    )
    
    object Usuarios : SuperAdminNavItem(
        route = "superadmin_usuarios",
        icon = Icons.Default.People,
        label = "Usuários",
        shortLabel = "Usuários"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminAppScaffold(
    superAdminId: String,
    onNavigateToBase: (String) -> Unit, // ✅ NOVO: Callback para navegar para base
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val context = LocalContext.current
    val notificationService = remember { NotificationService(context) }
    
    var showMenu by remember { mutableStateOf(false) }
    
    // Contador de feedbacks novos e bases pendentes
    var novosFeedbacksCount by remember { mutableStateOf(0) }
    var transportadorasPendentesCount by remember { mutableStateOf(0) }
    
    // IDs já notificados para evitar duplicatas
    var basesNotificadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var feedbacksNotificados by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Referências aos listeners
    var basesListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var feedbacksListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    
    // Carregar contador de feedbacks novos e inicializar IDs já notificados
    LaunchedEffect(Unit) {
        try {
            val feedbackRepository = FeedbackRepository()
            val feedbacks = feedbackRepository.getAllFeedbacks()
            novosFeedbacksCount = feedbacks.count { it.status == FeedbackStatus.NOVO }
            
            // Inicializar IDs já notificados com feedbacks existentes
            feedbacksNotificados = feedbacks.map { it.id }.toSet()
            
            // Buscar bases existentes para inicializar IDs já notificados
            val firestore = FirebaseManager.firestore
            val basesSnapshot = firestore.collection("bases").get().await()
            basesNotificadas = basesSnapshot.documents.map { it.id }.toSet()
            
            // Contar bases pendentes para badge
            transportadorasPendentesCount = basesSnapshot.documents.count { doc ->
                (doc.get("statusAprovacao") as? String) == "pendente"
            }
        } catch (e: Exception) {
            println("⚠️ SuperAdminAppScaffold: Erro ao inicializar: ${e.message}")
        }
        
        // Configurar listeners
        val firestore = FirebaseManager.firestore
        try {
            // Listener para novas bases
            basesListener = firestore.collection("bases")
                .whereEqualTo("statusAprovacao", "pendente")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        println("❌ SuperAdminAppScaffold: Erro no listener de bases: ${error.message}")
                        return@addSnapshotListener
                    }
                    
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val baseId = change.document.id
                            if (!basesNotificadas.contains(baseId)) {
                                val base = change.document.toObject(Base::class.java)
                                if (base.statusAprovacao == "pendente") {
                                    notificationService.sendSuperAdminNovaBaseNotification(
                                        transportadoraNome = base.transportadora ?: "Transportadora",
                                        baseNome = base.nome ?: "Base"
                                    )
                                    basesNotificadas = basesNotificadas + baseId
                                    println("✅ SuperAdminAppScaffold: Super admin notificado sobre nova base: ${base.nome}")
                                }
                            }
                        }
                    }
                }
            
            // Listener para novos feedbacks
            feedbacksListener = firestore.collection("feedbacks")
                .orderBy("data", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50) // Limitar para performance
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        println("❌ SuperAdminAppScaffold: Erro no listener de feedbacks: ${error.message}")
                        return@addSnapshotListener
                    }
                    
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val feedbackId = change.document.id
                            if (!feedbacksNotificados.contains(feedbackId)) {
                                try {
                                    val feedbackDoc = change.document.toObject(Feedback::class.java)
                                    
                                    if (feedbackDoc != null) {
                                        val feedback = feedbackDoc.copy(id = feedbackId)
                                        notificationService.sendSuperAdminNovoFeedbackNotification(
                                            adminNome = feedback.adminNome ?: "Admin",
                                            baseNome = feedback.baseNome ?: "Base",
                                            mensagem = feedback.mensagem ?: ""
                                        )
                                        feedbacksNotificados = feedbacksNotificados + feedbackId
                                        if (feedback.status == FeedbackStatus.NOVO) {
                                            novosFeedbacksCount++
                                        }
                                        println("✅ SuperAdminAppScaffold: Super admin notificado sobre novo feedback de ${feedback.adminNome}")
                                    }
                                } catch (e: Exception) {
                                    println("⚠️ SuperAdminAppScaffold: Erro ao processar feedback: ${e.message}")
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            println("❌ SuperAdminAppScaffold: Erro ao configurar listeners: ${e.message}")
        }
    }
    
    // Cleanup dos listeners quando o composable for desmontado
    DisposableEffect(Unit) {
        onDispose {
            basesListener?.remove()
            feedbacksListener?.remove()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Super Admin",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        // Feedbacks (com badge de novos)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Feedback,
                                            contentDescription = null,
                                            tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonCyan else Color(0xFF00838F),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("Feedbacks", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    if (novosFeedbacksCount > 0) {
                                        Badge(
                                            containerColor = NeonOrange,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = novosFeedbacksCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                showMenu = false
                                navController.navigate("superadmin_feedbacks")
                            }
                        )
                        
                        HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f))
                        
                        // Configurações do Sistema
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonBlue else NeonBlueContrast,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Configurações", color = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            onClick = {
                                showMenu = false
                                navController.navigate("superadmin_configuracoes")
                            }
                        )
                        
                        // Histórico de Ações
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonPurple else NeonPurpleContrast,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Histórico", color = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            onClick = {
                                showMenu = false
                                navController.navigate("superadmin_historico")
                            }
                        )
                        
                        // Minha Conta
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Minha Conta", color = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            onClick = {
                                showMenu = false
                                navController.navigate("superadmin_conta")
                            }
                        )
                        
                        HorizontalDivider(color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f))
                        
                        // Sair da Conta
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Sair da Conta", color = Color(0xFFEF4444))
                                }
                            },
                            onClick = {
                                showMenu = false
                                onLogout()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                listOf(
                    SuperAdminNavItem.Dashboard,
                    SuperAdminNavItem.Transportadoras,
                    SuperAdminNavItem.Relatorios,
                    SuperAdminNavItem.Usuarios
                ).forEach { item ->
                    val showBadge = item == SuperAdminNavItem.Transportadoras && transportadorasPendentesCount > 0
                    NavigationBarItem(
                        icon = {
                            Box {
                                Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(24.dp))
                                if (showBadge) {
                                    Badge(
                                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp),
                                        containerColor = NeonOrange,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = transportadorasPendentesCount.toString().take(3),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        },
                        label = { 
                            Text(
                                text = item.shortLabel,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            ) 
                        },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonGreen,
                            selectedTextColor = NeonGreen,
                            unselectedIconColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                            unselectedTextColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                            indicatorColor = NeonGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = SuperAdminNavItem.Dashboard.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(SuperAdminNavItem.Dashboard.route) {
                    SuperAdminDashboardScreen(
                        superAdminId = superAdminId,
                        onNavigateToTransportadoras = { filtro ->
                            val route = if (filtro != null) {
                                "${SuperAdminNavItem.Transportadoras.route}?status=$filtro"
                            } else {
                                SuperAdminNavItem.Transportadoras.route
                            }
                            navController.navigate(route)
                        },
                        onNavigateToUsuarios = {
                            navController.navigate(SuperAdminNavItem.Usuarios.route)
                        },
                        onNavigateToRelatorios = { filtro ->
                            val route = if (filtro != null) {
                                "${SuperAdminNavItem.Relatorios.route}?tab=$filtro"
                            } else {
                                SuperAdminNavItem.Relatorios.route
                            }
                            navController.navigate(route)
                        },
                        onNavigateToFeedbacks = { navController.navigate("superadmin_feedbacks") },
                        onNavigateToConfiguracoes = { navController.navigate("superadmin_configuracoes") }
                    )
                }
                
                composable(
                    route = "${SuperAdminNavItem.Transportadoras.route}?status={status}",
                    arguments = listOf(
                        navArgument("status") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    var showDetalhes by remember { mutableStateOf<String?>(null) }
                    val statusFilter = backStackEntry.arguments?.getString("status")
                    
                    if (showDetalhes != null) {
                        SuperAdminBaseDetalhesScreen(
                            baseId = showDetalhes!!,
                            superAdminId = superAdminId,
                            onBack = { showDetalhes = null },
                            onNavigateToBase = { baseId ->
                                onNavigateToBase(baseId)
                            }
                        )
                    } else {
                        SuperAdminTransportadorasScreen(
                            superAdminId = superAdminId,
                            onNavigateToBase = { baseId ->
                                onNavigateToBase(baseId)
                            },
                            onVerDetalhes = { baseId ->
                                showDetalhes = baseId
                            },
                            initialStatusFilter = statusFilter
                        )
                    }
                }
                
                composable(
                    route = "${SuperAdminNavItem.Relatorios.route}?tab={tab}",
                    arguments = listOf(
                        navArgument("tab") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val tab = backStackEntry.arguments?.getString("tab")
                    SuperAdminRelatoriosScreen(
                        superAdminId = superAdminId,
                        initialTab = when (tab) {
                            "financeiro" -> "financeiro"
                            "operacional" -> "operacional"
                            else -> null
                        }
                    )
                }
                
                composable(SuperAdminNavItem.Usuarios.route) {
                    SuperAdminUsuariosScreen(superAdminId = superAdminId)
                }
                
                // Rotas do menu superior (não aparecem na bottom bar)
                composable("superadmin_configuracoes") {
                    SistemaConfigScreen(
                        superAdminId = superAdminId,
                        onBack = { navController.popBackStack() },
                        onNavigateToFeedbacks = {
                            navController.navigate("superadmin_feedbacks")
                        }
                    )
                }
                
                composable("superadmin_feedbacks") {
                    SuperAdminFeedbackScreen(
                        superAdminId = superAdminId,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("superadmin_conta") {
                    SuperAdminContaScreen(
                        superAdminId = superAdminId,
                        onBack = { navController.popBackStack() },
                        onLogout = onLogout
                    )
                }
                
                composable("superadmin_historico") {
                    SuperAdminHistoricoScreen(
                        superAdminId = superAdminId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
