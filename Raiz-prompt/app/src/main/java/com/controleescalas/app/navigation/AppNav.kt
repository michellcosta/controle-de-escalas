package com.controleescalas.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.data.models.SavedSession
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.SistemaRepository
import com.controleescalas.app.ui.screens.*
import com.controleescalas.app.ui.screens.DriverAppScaffold
import com.controleescalas.app.ui.viewmodels.DevolucaoViewModel
import com.controleescalas.app.ui.theme.ControleEscalasTheme
import com.controleescalas.app.ui.viewmodels.AdminViewModel
import com.controleescalas.app.ui.viewmodels.CreateBaseViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

/**
 * AppNavHost
 *
 * Navegação básica entre as telas.
 * Gerencia o estado de autenticação e roteamento.
 */

sealed class Routes(val route: String) {
    object Splash : Routes("splash")
    object AccountSelection : Routes("account_selection")
    object Main : Routes("main")
    object CreateBase : Routes("create_base")
    object LoginExisting : Routes("login_existing")
    object DriverHome : Routes("driver_home")
    object AdminPanel : Routes("admin_panel")
    object OperationalDashboard : Routes("operational_dashboard")
    object Scale : Routes("scale_screen")
    object LocationConfig : Routes("location_config")
    object UserManagement : Routes("user_management")
    object QuinzenaList : Routes("quinzena_list")
    object BaseApproval : Routes("base_approval")
    object SistemaConfig : Routes("sistema_config")
    object AdminFeedback : Routes("admin_feedback")
    object SuperAdminFeedback : Routes("superadmin_feedback")
    object SuperAdminApp : Routes("superadmin_app") // ✅ NOVO: App dedicado do Super Admin
    object AdminDevolucoes : Routes("admin_devolucoes")
    object AdminDevolucaoDetalhes : Routes("admin_devolucao_detalhes")
    object Planos : Routes("planos")
    object Assistente : Routes("assistente")
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }

    // Estado para manter os dados do usuário logado na memória durante a sessão
    val currentUserId = remember { mutableStateOf<String?>(FirebaseAuth.getInstance().currentUser?.uid) }
    val currentUserBaseId = remember { mutableStateOf<String?>(null) }
    val currentUserRole = remember { mutableStateOf<String?>(null) }
    val isLoggingOut = remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route
    ) {

            // Splash Screen com auto-login
            composable(Routes.Splash.route) {
                SplashScreen(
                    onNavigateToMain = {
                        navController.navigate(Routes.AccountSelection.route) {
                            popUpTo(Routes.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToApp = { userId, baseId, role ->
                        currentUserId.value = userId
                        currentUserBaseId.value = baseId
                        currentUserRole.value = role
                        
                        val destination = when (role) {
                            "motorista" -> Routes.DriverHome.route
                            "superadmin" -> Routes.SuperAdminApp.route // Super admin vai para o app dedicado
                            else -> Routes.OperationalDashboard.route
                        }
                        
                        navController.navigate(destination) {
                            popUpTo(Routes.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // Account Selection Screen
            composable(Routes.AccountSelection.route) {
                AccountSelectionScreen(
                    onAccountSelected = { session ->
                        // ✅ NOVO: Verificar status da base antes de permitir acesso
                        scope.launch {
                            try {
                                // Super admin não precisa verificar status (pode acessar tudo)
                                if (session.userRole == "superadmin") {
                                    currentUserId.value = session.userId
                                    currentUserBaseId.value = session.baseId
                                    currentUserRole.value = session.userRole
                                    
                                    navController.navigate(Routes.SuperAdminApp.route) {
                                        popUpTo(Routes.AccountSelection.route) { inclusive = true }
                                    }
                                    return@launch
                                }
                                
                                // Verificar status da base para outros usuários
                                // Achado 10 da auditoria: getBase() retorna null
                                // tanto para "não existe" quanto para erro de
                                // rede — o usuário selecionava a conta e NADA
                                // acontecia, sem mensagem. getBaseOuErro lança
                                // em erro de rede (o catch de baixo avisa).
                                val baseRepository = BaseRepository()
                                val base = baseRepository.getBaseOuErro(session.baseId)

                                if (base == null) {
                                    println("❌ AppNav: Base ${session.baseId} não encontrada")
                                    // Não navegar - a sessão será removida na próxima validação
                                    return@launch
                                }

                                if (base.statusAprovacao != "ativa") {
                                    println("⚠️ AppNav: Base ${session.baseId} não está ativa (status: ${base.statusAprovacao})")
                                    // Não navegar - a sessão será removida na próxima validação
                                    return@launch
                                }
                                
                                // Base está ativa, permitir navegação
                                println("✅ AppNav: Base ${session.baseId} está ativa, permitindo acesso")
                                currentUserId.value = session.userId
                                currentUserBaseId.value = session.baseId
                                currentUserRole.value = session.userRole
                                
                                val destination = when (session.userRole) {
                                    "motorista" -> Routes.DriverHome.route
                                    else -> Routes.OperationalDashboard.route
                                }
                                
                                navController.navigate(destination) {
                                    popUpTo(Routes.AccountSelection.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                println("❌ AppNav: Erro ao verificar status da base: ${e.message}")
                                e.printStackTrace()
                                // Achado 10: erro visível ao usuário (antes era
                                // só println — clique morto)
                                android.widget.Toast.makeText(
                                    context,
                                    "Não foi possível validar a conta. Verifique sua conexão e tente novamente.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                // Em caso de erro, não navegar (forçar revalidação)
                            }
                        }
                    },
                    onAddNewAccount = {
                        // O diálogo de login será controlado internamente na tela
                        // Não precisa navegar para outra tela
                    },
                    onBack = {
                        navController.navigate(Routes.Main.route)
                    }
                )
            }

            composable(Routes.Main.route) {
                MainScreen(
                    onCreateBaseClick = {
                        navController.navigate(Routes.CreateBase.route)
                    },
                    onExistingAccountClick = {
                        navController.navigate(Routes.AccountSelection.route)
                    }
                )
            }

            composable(Routes.CreateBase.route) {
                val viewModel: CreateBaseViewModel = viewModel()
                val successMessage by viewModel.success.collectAsState()
                
                CreateBaseScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onCreateBaseClick = { createBaseData ->
                        viewModel.createBase(createBaseData)
                    },
                    successMessage = successMessage,
                    onSuccessAcknowledged = {
                        // Navegar de volta para MainScreen
                        navController.navigate(Routes.Main.route) {
                            popUpTo(Routes.CreateBase.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.LoginExisting.route) {
                LoginExistingScreen(
                    onLoginAsMotorista = { motoristaId, baseId ->
                        println("✅ AppNav: Login como motorista - motoristaId='$motoristaId', baseId='$baseId'")
                        // ✅ NOVO: Verificar status da base antes de permitir acesso
                        scope.launch {
                            try {
                                val baseRepository = BaseRepository()
                                val base = baseRepository.getBase(baseId)
                                
                                if (base == null || base.statusAprovacao != "ativa") {
                                    println("⚠️ AppNav: Base $baseId não está ativa (status: ${base?.statusAprovacao})")
                                    // Não navegar - o erro será mostrado pelo LoginExistingScreen
                                    return@launch
                                }
                                
                                currentUserId.value = motoristaId
                                currentUserBaseId.value = baseId
                                currentUserRole.value = "motorista"
                                println("✅ AppNav: Valores salvos - currentUserId='${currentUserId.value}', currentUserBaseId='${currentUserBaseId.value}'")
                                navController.navigate(Routes.DriverHome.route) {
                                    popUpTo(Routes.Main.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                println("❌ AppNav: Erro ao verificar status da base: ${e.message}")
                            }
                        }
                    },
                    onLoginAsAdmin = { adminId, baseId ->
                        // ✅ NOVO: Verificar status da base antes de permitir acesso
                        scope.launch {
                            try {
                                val baseRepository = BaseRepository()
                                val base = baseRepository.getBase(baseId)
                                
                                if (base == null || base.statusAprovacao != "ativa") {
                                    println("⚠️ AppNav: Base $baseId não está ativa (status: ${base?.statusAprovacao})")
                                    // Não navegar - o erro será mostrado pelo LoginExistingScreen
                                    return@launch
                                }
                                
                                currentUserId.value = adminId
                                currentUserBaseId.value = baseId
                                currentUserRole.value = "admin"
                                navController.navigate(Routes.OperationalDashboard.route) {
                                    popUpTo(Routes.Main.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                println("❌ AppNav: Erro ao verificar status da base: ${e.message}")
                            }
                        }
                    },
                    onLoginAsSuperAdmin = { superAdminId ->
                        currentUserId.value = superAdminId
                        currentUserBaseId.value = "" // Super admin não tem base própria
                        currentUserRole.value = "superadmin"
                        navController.navigate(Routes.BaseApproval.route) {
                            popUpTo(Routes.Main.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DriverHome.route) {
                // IMPORTANTE: Verificar flag ANTES de ler qualquer estado
                val isLoggingOutNow = isLoggingOut.value
                if (isLoggingOutNow) {
                    println("⏭️ AppNav: Logout em andamento, não renderizando DriverAppScaffold")
                    return@composable
                }
                
                // Agora sim, ler os estados
                val motoristaId = currentUserId.value ?: ""
                val baseId = currentUserBaseId.value ?: ""
                android.util.Log.i("AppNav", "📋 [MOTORISTA] Tela DriverHome - motoristaId='$motoristaId', baseId='$baseId'")
                println("📋 AppNav: Renderizando DriverAppScaffold - motoristaId='$motoristaId', baseId='$baseId'")
                
                DriverAppScaffold(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    onLogout = {
                        // Capturar valores ANTES de limpar
                        val userIdToUpdate = motoristaId
                        val baseIdToUpdate = baseId
                        
                        // Marcar que está fazendo logout para evitar recomposição
                        isLoggingOut.value = true
                        
                        // Limpar estados ANTES de navegar
                        currentUserId.value = null
                        currentUserBaseId.value = null
                        currentUserRole.value = null
                        
                        // Navegar IMEDIATAMENTE
                        navController.navigate(Routes.AccountSelection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                        
                        // Salvar sessão completa em background (não bloqueia navegação)
                        scope.launch {
                            if (userIdToUpdate.isNotBlank() && baseIdToUpdate.isNotBlank()) {
                                try {
                                    println("🔍 AppNav: Buscando dados do motorista para salvar sessão...")
                                    
                                    // Buscar dados do motorista
                                    val motoristaDoc = FirebaseFirestore.getInstance()
                                        .collection("bases")
                                        .document(baseIdToUpdate)
                                        .collection("motoristas")
                                        .document(userIdToUpdate)
                                        .get()
                                        .await()
                                    
                                    // Buscar dados da base
                                    val baseDoc = FirebaseFirestore.getInstance()
                                        .collection("bases")
                                        .document(baseIdToUpdate)
                                        .get()
                                        .await()
                                    
                                    if (motoristaDoc.exists() && baseDoc.exists()) {
                                        val session = SavedSession(
                                            userId = userIdToUpdate,
                                            baseId = baseIdToUpdate,
                                            baseName = baseDoc.getString("nome") ?: "Transportadora",
                                            userName = motoristaDoc.getString("nome") ?: "Motorista",
                                            userRole = motoristaDoc.getString("papel") ?: "motorista"
                                        )
                                        
                                        sessionManager.saveSession(session)
                                        println("✅ AppNav: Sessão salva no logout - ${session.baseName} (${session.userName})")
                                    } else {
                                        println("⚠️ AppNav: Dados não encontrados no Firestore, apenas atualizando timestamp")
                                        sessionManager.updateSessionTimestamp(userIdToUpdate, baseIdToUpdate)
                                    }
                                } catch (e: Exception) {
                                    println("❌ AppNav: Erro ao salvar sessão: ${e.message}")
                                    e.printStackTrace()
                                    // Fallback: apenas atualizar timestamp
                                    try {
                                        sessionManager.updateSessionTimestamp(userIdToUpdate, baseIdToUpdate)
                                        println("✅ AppNav: Timestamp atualizado como fallback")
                                    } catch (e2: Exception) {
                                        println("❌ AppNav: Erro ao atualizar timestamp: ${e2.message}")
                                    }
                                }
                            } else {
                                println("⚠️ AppNav: userId ou baseId vazio, não salvando sessão")
                            }
                            
                            // Resetar flag SOMENTE após salvar sessão
                            isLoggingOut.value = false
                        }
                    }
                )
            }

            composable(Routes.OperationalDashboard.route) {
                // IMPORTANTE: Verificar flag ANTES de ler qualquer estado
                val isLoggingOutNow = isLoggingOut.value
                if (isLoggingOutNow) {
                    println("⏭️ AppNav: Logout em andamento, não renderizando MainAppScaffold")
                    return@composable
                }
                
                MainAppScaffold(
                    baseId = currentUserBaseId.value ?: "",
                    userId = currentUserId.value ?: "",
                    userRole = currentUserRole.value ?: "",
                    onLogout = {
                        // Capturar valores ANTES de limpar (baseId necessário
                        // para salvar a sessão sem varrer bases — achado 11)
                        val userIdToUpdate = currentUserId.value
                        val baseIdParaSessao = currentUserBaseId.value
                        val baseIdToUpdate = currentUserBaseId.value
                        
                        // Marcar que está fazendo logout para evitar recomposição
                        isLoggingOut.value = true
                        
                        // Limpar estados ANTES de navegar
                        currentUserId.value = null
                        currentUserBaseId.value = null
                        currentUserRole.value = null
                        
                        // Navegar IMEDIATAMENTE
                        navController.navigate(Routes.AccountSelection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                        
                        // Salvar sessão completa em background (não bloqueia navegação)
                        scope.launch {
                            if (userIdToUpdate != null && baseIdToUpdate != null) {
                                try {
                                    println("🔍 AppNav: Buscando dados do admin para salvar sessão...")
                                    
                                    // Buscar dados do usuário (pode ser admin or auxiliar)
                                    val userDoc = FirebaseFirestore.getInstance()
                                        .collection("bases")
                                        .document(baseIdToUpdate)
                                        .collection("usuarios")
                                        .document(userIdToUpdate)
                                        .get()
                                        .await()
                                    
                                    // Buscar dados da base
                                    val baseDoc = FirebaseFirestore.getInstance()
                                        .collection("bases")
                                        .document(baseIdToUpdate)
                                        .get()
                                        .await()
                                    
                                    if (userDoc.exists() && baseDoc.exists()) {
                                        val session = SavedSession(
                                            userId = userIdToUpdate,
                                            baseId = baseIdToUpdate,
                                            baseName = baseDoc.getString("nome") ?: "Transportadora",
                                            userName = userDoc.getString("nome") ?: "Admin",
                                            userRole = userDoc.getString("papel") ?: "admin"
                                        )
                                        
                                        sessionManager.saveSession(session)
                                        println("✅ AppNav: Sessão admin salva no logout - ${session.baseName} (${session.userName})")
                                    } else {
                                        println("⚠️ AppNav: Dados admin não encontrados no Firestore, apenas atualizando timestamp")
                                        sessionManager.updateSessionTimestamp(userIdToUpdate, baseIdToUpdate)
                                    }
                                } catch (e: Exception) {
                                    println("❌ AppNav: Erro ao salvar sessão admin: ${e.message}")
                                    e.printStackTrace()
                                    // Fallback: apenas atualizar timestamp
                                    try {
                                        sessionManager.updateSessionTimestamp(userIdToUpdate, baseIdToUpdate)
                                        println("✅ AppNav: Timestamp admin atualizado como fallback")
                                    } catch (e2: Exception) {
                                        println("❌ AppNav: Erro ao atualizar timestamp admin: ${e2.message}")
                                    }
                                }
                            } else {
                                println("⚠️ AppNav: userId ou baseId null, não salvando sessão admin")
                            }
                            
                            // Resetar flag SOMENTE após salvar sessão
                            isLoggingOut.value = false
                        }
                    },
                    onNavigateToLocationConfig = { navController.navigate(Routes.LocationConfig.route) },
                    onNavigateToUserManagement = { navController.navigate(Routes.UserManagement.route) }
                )
            }

            composable(Routes.AdminPanel.route) {
                val viewModel: AdminViewModel = viewModel()
                val isSuperAdmin = currentUserRole.value == "superadmin"
                var planosHabilitados by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    planosHabilitados = SistemaRepository().getConfiguracao().planosHabilitados
                }
                
                println("🔵 AppNav: Renderizando AdminPanelScreen")
                println("🔵 AppNav: baseId = ${currentUserBaseId.value}")
                println("🔵 AppNav: isSuperAdmin = $isSuperAdmin")
                
                AdminPanelScreen(
                    baseId = currentUserBaseId.value ?: "",
                    isSuperAdmin = isSuperAdmin,
                    superAdminId = currentUserId.value ?: "",
                    onEscalaClick = { navController.navigate(Routes.Scale.route) },
                    onLocationConfigClick = { navController.navigate(Routes.LocationConfig.route) },
                    onUserManagementClick = { navController.navigate(Routes.UserManagement.route) },
                    onQuinzenaClick = { navController.navigate(Routes.QuinzenaList.route) },
                    onDevolucaoClick = { 
                        println("🔵 AppNav: onDevolucaoClick callback executado!")
                        println("🔵 AppNav: Botão Devolução clicado, navegando para admin_devolucoes")
                        println("🔵 AppNav: baseId atual = ${currentUserBaseId.value}")
                        println("🔵 AppNav: Rota AdminDevolucoes = ${Routes.AdminDevolucoes.route}")
                        println("🔵 AppNav: navController não é null? ${navController != null}")
                        try {
                            val route = Routes.AdminDevolucoes.route
                            println("🔵 AppNav: Navegando para rota: $route")
                            navController.navigate(route)
                            println("✅ AppNav: navController.navigate() chamado com sucesso")
                        } catch (e: Exception) {
                            println("❌ AppNav: Erro ao navegar para admin_devolucoes: ${e.message}")
                            e.printStackTrace()
                        }
                    },
                    onPlanosClick = { navController.navigate(Routes.Planos.route) },
                    showPlanosButton = planosHabilitados,
                    onBaseApprovalClick = { navController.navigate(Routes.BaseApproval.route) },
                    onFeedbackClick = {
                        navController.navigate(Routes.AdminFeedback.route)
                    }
                )
            }
            
            // ✅ NOVO: App dedicado do Super Admin
            composable(Routes.SuperAdminApp.route) {
                SuperAdminAppScaffold(
                    superAdminId = currentUserId.value ?: "",
                    onNavigateToBase = { baseId ->
                        // Atualizar o baseId atual para que o superadmin entre como admin na transportadora
                        currentUserBaseId.value = baseId
                        // Navegar para o dashboard operacional
                        navController.navigate(Routes.OperationalDashboard.route) {
                            popUpTo(Routes.SuperAdminApp.route) { inclusive = false }
                        }
                    },
                    onLogout = {
                        // Capturar valores ANTES de limpar (baseId necessário
                        // para salvar a sessão sem varrer bases — achado 11)
                        val userIdToUpdate = currentUserId.value
                        val baseIdParaSessao = currentUserBaseId.value
                        
                        // Marcar que está fazendo logout para evitar recomposição
                        isLoggingOut.value = true
                        
                        // Limpar estados ANTES de navegar
                        currentUserId.value = null
                        currentUserBaseId.value = null
                        currentUserRole.value = null
                        
                        // Navegar IMEDIATAMENTE
                        navController.navigate(Routes.AccountSelection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                        
                        // Salvar sessão completa em background (não bloqueia navegação)
                        scope.launch {
                            if (userIdToUpdate != null) {
                                try {
                                    println("🔍 AppNav: Buscando dados do super admin para salvar sessão...")

                                    // Achado 11 da auditoria: era varredura de
                                    // TODAS as bases + 1 get por base (N+1
                                    // leituras por logout). Com o baseId capturado
                                    // antes de limpar o estado, 2 leituras diretas.
                                    val baseIdSalvo = baseIdParaSessao
                                    if (!baseIdSalvo.isNullOrBlank()) {
                                        val userDoc = FirebaseFirestore.getInstance()
                                            .collection("bases")
                                            .document(baseIdSalvo)
                                            .collection("motoristas")
                                            .document(userIdToUpdate)
                                            .get()
                                            .await()
                                        if (userDoc.exists() && userDoc.getString("papel") == "superadmin") {
                                            val baseDoc = FirebaseFirestore.getInstance()
                                                .collection("bases")
                                                .document(baseIdSalvo)
                                                .get()
                                                .await()
                                            val session = SavedSession(
                                                userId = userIdToUpdate,
                                                baseId = baseIdSalvo,
                                                baseName = baseDoc.getString("nome") ?: "Transportadora",
                                                userName = userDoc.getString("nome") ?: "Super Admin",
                                                userRole = "superadmin"
                                            )
                                            sessionManager.saveSession(session)
                                            println("✅ AppNav: Sessão super admin salva no logout - ${session.baseName} (${session.userName})")
                                        } else {
                                            println("⚠️ AppNav: Super admin não encontrado na base $baseIdSalvo")
                                        }
                                    } else {
                                        println("⚠️ AppNav: Sem baseId em memória para salvar sessão do super admin")
                                    }
                                } catch (e: Exception) {
                                    println("❌ AppNav: Erro ao salvar sessão super admin: ${e.message}")
                                    e.printStackTrace()
                                }
                            } else {
                                println("⚠️ AppNav: userId null, não salvando sessão super admin")
                            }
                            
                            // Resetar flag SOMENTE após salvar sessão
                            isLoggingOut.value = false
                        }
                    }
                )
            }
            
            // Manter BaseApproval para compatibilidade (pode ser removido depois)
            composable(Routes.BaseApproval.route) {
                BaseApprovalScreen(
                    superAdminId = currentUserId.value ?: "",
                    onNavigateToBase = { baseId ->
                        currentUserBaseId.value = baseId
                        navController.navigate(Routes.OperationalDashboard.route) {
                            popUpTo(Routes.BaseApproval.route) { inclusive = false }
                        }
                    },
                    onNavigateToSistemaConfig = {
                        navController.navigate(Routes.SistemaConfig.route)
                    },
                    onBack = {
                        navController.navigate(Routes.Main.route) {
                            popUpTo(Routes.BaseApproval.route) { inclusive = true }
                        }
                    },
                    onLogout = {
                        val userIdToUpdate = currentUserId.value
                        val baseIdParaSessao = currentUserBaseId.value
                        isLoggingOut.value = true
                        currentUserId.value = null
                        currentUserBaseId.value = null
                        currentUserRole.value = null
                        navController.navigate(Routes.AccountSelection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                        scope.launch {
                            if (userIdToUpdate != null) {
                                try {
                                    // Achado 11: 2 leituras diretas em vez de N+1
                                    val baseIdSalvo2 = baseIdParaSessao
                                    if (!baseIdSalvo2.isNullOrBlank()) {
                                        val userDoc = FirebaseFirestore.getInstance()
                                            .collection("bases")
                                            .document(baseIdSalvo2)
                                            .collection("motoristas")
                                            .document(userIdToUpdate)
                                            .get()
                                            .await()
                                        if (userDoc.exists() && userDoc.getString("papel") == "superadmin") {
                                            val baseDoc = FirebaseFirestore.getInstance()
                                                .collection("bases")
                                                .document(baseIdSalvo2)
                                                .get()
                                                .await()
                                            sessionManager.saveSession(SavedSession(
                                                userId = userIdToUpdate,
                                                baseId = baseIdSalvo2,
                                                baseName = baseDoc.getString("nome") ?: "Transportadora",
                                                userName = userDoc.getString("nome") ?: "Super Admin",
                                                userRole = "superadmin"
                                            ))
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("❌ AppNav: Erro ao salvar sessão: ${e.message}")
                                }
                            }
                            isLoggingOut.value = false
                        }
                    }
                )
            }

            composable(Routes.SistemaConfig.route) {
                SistemaConfigScreen(
                    superAdminId = currentUserId.value ?: "",
                    onBack = {
                        navController.popBackStack()
                    },
                    onNavigateToFeedbacks = {
                        navController.navigate(Routes.SuperAdminFeedback.route)
                    }
                )
            }

            composable(Routes.AdminFeedback.route) {
                AdminFeedbackScreen(
                    baseId = currentUserBaseId.value ?: "",
                    adminId = currentUserId.value ?: "",
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Rotas de Devolução (Admin)
            composable(Routes.AdminDevolucoes.route) {
                println("🔵 [HYP-C] AppNav: AdminDevolucoes route composable registered - route: ${Routes.AdminDevolucoes.route}")
                println("🔵 AppNav: Renderizando AdminDevolucoesScreen")
                // Achado 15 da auditoria: VM criado NA ENTRADA DA LISTA — a
                // tela de detalhes compartilha o MESMO estado (antes cada
                // composable criava um VM vazio e recarregava tudo do
                // Firestore a cada navegação)
                val devolucaoViewModel: DevolucaoViewModel = viewModel(
                    viewModelStoreOwner = navController.getBackStackEntry(Routes.AdminDevolucoes.route)
                )
                val baseIdValue = currentUserBaseId.value ?: ""
                println("🔵 AppNav: baseId para AdminDevolucoesScreen = '$baseIdValue'")
                AdminDevolucoesScreen(
                    baseId = baseIdValue,
                    onDismiss = { 
                        println("🔵 AppNav: Voltar de AdminDevolucoesScreen")
                        navController.popBackStack() 
                    },
                    onMotoristaClick = { motoristaId, motoristaNome ->
                        println("🔵 [HYP-B] AppNav: onMotoristaClick callback received - motoristaId: $motoristaId, nome: $motoristaNome")
                        println("🔵 [HYP-B] AppNav: navController is null: ${navController == null}")
                        // Usar apenas motoristaId na rota para evitar problemas com caracteres especiais
                        val route = "${Routes.AdminDevolucaoDetalhes.route}/$motoristaId"
                        println("🔵 [HYP-D] AppNav: Route constructed - baseRoute: ${Routes.AdminDevolucaoDetalhes.route}, fullRoute: $route")
                        println("🔵 [HYP-C] AppNav: About to call navController.navigate()")
                        try {
                            navController.navigate(route) {
                                println("🔵 [HYP-C] AppNav: navigate() lambda executed")
                            }
                            println("🔵 [HYP-C] AppNav: navigate() completed without exception")
                        } catch (e: Exception) {
                            println("❌ [HYP-C] AppNav: Exception in navigate() - ${e.javaClass.simpleName}: ${e.message}")
                            e.printStackTrace()
                        }
                    },
                    viewModel = devolucaoViewModel
                )
            }
            
            composable("${Routes.AdminDevolucaoDetalhes.route}/{motoristaId}") { backStackEntry ->
                println("🔵 [HYP-E] AppNav: AdminDevolucaoDetalhes composable matched - route: ${backStackEntry.destination.route}")
                // Achado 15: mesmo VM da lista (escopo da entrada AdminDevolucoes)
                val devolucaoViewModel: DevolucaoViewModel = viewModel(
                    viewModelStoreOwner = navController.getBackStackEntry(Routes.AdminDevolucoes.route)
                )
                val motoristaId = backStackEntry.arguments?.getString("motoristaId") ?: ""
                println("🔵 [HYP-E] AppNav: Extracted motoristaId: '$motoristaId' (empty: ${motoristaId.isEmpty()})")
                // Buscar o nome do motorista a partir do ID
                var motoristaNome by remember { mutableStateOf("") }
                LaunchedEffect(motoristaId, currentUserBaseId.value) {
                    val baseId = currentUserBaseId.value ?: ""
                    if (motoristaId.isNotEmpty() && baseId.isNotEmpty()) {
                        val repository = com.controleescalas.app.data.repositories.MotoristaRepository()
                        val nome = repository.getMotoristaNome(motoristaId, baseId)
                        motoristaNome = nome ?: "Motorista"
                    }
                }
                println("🔵 [HYP-E] AppNav: About to render AdminDevolucaoDetalhesScreen - motoristaNome: '$motoristaNome', baseId: '${currentUserBaseId.value ?: ""}'")
                AdminDevolucaoDetalhesScreen(
                    baseId = currentUserBaseId.value ?: "",
                    motoristaId = motoristaId,
                    motoristaNome = motoristaNome,
                    onDismiss = { navController.popBackStack() },
                    viewModel = devolucaoViewModel
                )
            }

            composable(Routes.SuperAdminFeedback.route) {
                SuperAdminFeedbackScreen(
                    superAdminId = currentUserId.value ?: "",
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.Planos.route) {
                PlanosScreen(
                    baseId = currentUserBaseId.value ?: "",
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.Scale.route) {
                ScaleScreen(
                    baseId = currentUserBaseId.value ?: "",
                    onVoltar = { navController.popBackStack() },
                    onOpenAssistente = { navController.navigate(Routes.Assistente.route) }
                )
            }

            composable(Routes.Assistente.route) {
                AssistenteScreen(
                    baseId = currentUserBaseId.value ?: "",
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.LocationConfig.route) {
                LocationConfigOSMScreen(
                    baseId = currentUserBaseId.value ?: "",
                    onVoltar = { navController.popBackStack() }
                )
            }

            composable(Routes.UserManagement.route) {
                val viewModel: com.controleescalas.app.ui.viewmodels.AdminViewModel = viewModel()
                UserManagementScreen(
                    navController = navController,
                    viewModel = viewModel,
                    baseId = currentUserBaseId.value ?: "",
                    currentUserId = currentUserId.value ?: ""
                )
            }

            composable(Routes.QuinzenaList.route) {
                QuinzenaListScreen(
                    baseId = currentUserBaseId.value ?: "",
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
                    baseId = currentUserBaseId.value ?: "",
                    motoristaId = motoristaId,
                    viewModel = quinzenaViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
    }
}

@Preview(showBackground = true, name = "App nav (Main)")
@Composable
private fun AppNavHostPreview() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    ControleEscalasTheme {
        AppNavHost()
    }
}
