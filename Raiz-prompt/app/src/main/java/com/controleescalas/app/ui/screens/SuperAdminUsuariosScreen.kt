package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class AdminInfo(
    val id: String,
    val nome: String,
    val telefone: String,
    val baseId: String,
    val baseNome: String,
    val ativo: Boolean,
    val criadoEm: Long
)

@Composable
fun SuperAdminUsuariosScreen(
    superAdminId: String
) {
    val motoristaRepository = MotoristaRepository()
    val baseRepository = BaseRepository()
    val scope = rememberCoroutineScope()
    
    var admins by remember { mutableStateOf<List<AdminInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showResetPinDialog by remember { mutableStateOf<AdminInfo?>(null) }
    var showDesativarDialog by remember { mutableStateOf<AdminInfo?>(null) }
    var showLimparInativosDialog by remember { mutableStateOf(false) }
    var adminsInativosCount by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Carregar todos os admins
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                val firestore = FirebaseManager.firestore
                val allAdmins = mutableListOf<AdminInfo>()
                
                // ✅ OTIMIZADO: Usar collectionGroup para buscar TODOS os admins de uma vez
                // Isso reduz de N queries (uma por base) para apenas 1 query
                try {
                    val adminsSnapshot = firestore
                        .collectionGroup("motoristas")
                        .whereIn("papel", listOf("admin", "auxiliar"))
                        .get()
                        .await()
                    
                    // Criar mapa de bases para lookup rápido
                    val basesMap = mutableMapOf<String, String>()
                    val basesSnapshot = firestore.collection("bases").get().await()
                    for (baseDoc in basesSnapshot.documents) {
                        val baseId = baseDoc.id
                        if (baseId != "super_admin_base") {
                            basesMap[baseId] = baseDoc.getString("nome") ?: "Transportadora"
                        }
                    }
                    
                    // Processar admins encontrados
                    for (adminDoc in adminsSnapshot.documents) {
                        val motorista = adminDoc.toObject(Motorista::class.java)
                        if (motorista != null) {
                            // Extrair baseId do path: bases/{baseId}/motoristas/{motoristaId}
                            val path = adminDoc.reference.path
                            val pathParts = path.split("/")
                            if (pathParts.size >= 2) {
                                val baseId = pathParts[1]
                                if (baseId != "super_admin_base" && basesMap.containsKey(baseId)) {
                                    allAdmins.add(
                                        AdminInfo(
                                            id = adminDoc.id,
                                            nome = motorista.nome,
                                            telefone = motorista.telefone,
                                            baseId = baseId,
                                            baseNome = basesMap[baseId] ?: "Transportadora",
                                            ativo = motorista.ativo,
                                            criadoEm = motorista.criadoEm
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    println("✅ SuperAdminUsuariosScreen: ${allAdmins.size} admins encontrados via collectionGroup")
                } catch (e: Exception) {
                    println("⚠️ SuperAdminUsuariosScreen: CollectionGroup não disponível, usando método alternativo")
                    // Fallback: método antigo (menos eficiente)
                    val basesSnapshot = firestore.collection("bases").get().await()
                    
                    for (baseDoc in basesSnapshot.documents) {
                        val baseId = baseDoc.id
                        if (baseId == "super_admin_base") continue
                        
                        val baseNome = baseDoc.getString("nome") ?: "Transportadora"
                        
                        val adminsSnapshot = firestore
                            .collection("bases")
                            .document(baseId)
                            .collection("motoristas")
                            .whereIn("papel", listOf("admin", "auxiliar"))
                            .get()
                            .await()
                        
                        for (adminDoc in adminsSnapshot.documents) {
                            val motorista = adminDoc.toObject(Motorista::class.java)
                            if (motorista != null) {
                                allAdmins.add(
                                    AdminInfo(
                                        id = adminDoc.id,
                                        nome = motorista.nome,
                                        telefone = motorista.telefone,
                                        baseId = baseId,
                                        baseNome = baseNome,
                                        ativo = motorista.ativo,
                                        criadoEm = motorista.criadoEm
                                    )
                                )
                            }
                        }
                    }
                }
                
                admins = allAdmins.sortedBy { it.nome }
            } catch (e: Exception) {
                error = "Erro ao carregar admins: ${e.message}"
                println("❌ SuperAdminUsuariosScreen: Erro - ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    // Contar administradores inativos
    LaunchedEffect(admins) {
        adminsInativosCount = admins.count { !it.ativo }
    }
    
    // Filtrar admins
    val adminsFiltrados = remember(admins, searchQuery) {
        if (searchQuery.isBlank()) {
            admins
        } else {
            admins.filter {
                it.nome.lowercase().contains(searchQuery.lowercase()) ||
                it.telefone.contains(searchQuery) ||
                it.baseNome.lowercase().contains(searchQuery.lowercase())
            }
        }
    }
    
    // Limpar mensagens
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(5000)
            error = null
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Campo de pesquisa
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { 
                Text(
                    "Pesquisar...",
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ) 
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = NeonGreen)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = TextGray
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        SectionHeader(title = "Administradores (${adminsFiltrados.size})")
        
        // Botão para limpar administradores inativos
        if (adminsInativosCount > 0) {
            OutlinedButton(
                onClick = { showLimparInativosDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Limpar Administradores Inativos ($adminsInativosCount)")
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonGreen)
            }
        } else if (adminsFiltrados.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Nenhum administrador encontrado",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(adminsFiltrados) { admin ->
                    AdminCard(
                        admin = admin,
                        onResetPin = { showResetPinDialog = admin },
                        onDesativar = { showDesativarDialog = admin }
                    )
                }
            }
        }
        
        // Mensagens (fora do Column, dentro do Box)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
            }
            
            error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White
                ) {
                    Text(err)
                }
            }
        }
    }
    
    // Diálogo de resetar PIN
    showResetPinDialog?.let { admin ->
        AlertDialog(
            onDismissRequest = { showResetPinDialog = null },
            title = { Text("Resetar PIN", color = TextWhite) },
            text = {
                Text(
                    "Tem certeza que deseja resetar o PIN do administrador ${admin.nome}?",
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                // Gerar novo PIN aleatório (4 dígitos)
                                val novoPin = (1000..9999).random().toString()
                                
                                // Hash do PIN (usando bcrypt no backend ou simples hash)
                                // Por enquanto, vamos apenas atualizar no Firestore
                                // Em produção, isso deveria ser feito via Cloud Function
                                val firestore = FirebaseManager.firestore
                                firestore
                                    .collection("bases")
                                    .document(admin.baseId)
                                    .collection("motoristas")
                                    .document(admin.id)
                                    .update("pinHash", novoPin) // ⚠️ Em produção, usar hash
                                    .await()
                                
                                message = "PIN resetado para: $novoPin"
                                showResetPinDialog = null
                            } catch (e: Exception) {
                                error = "Erro ao resetar PIN: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("Resetar", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPinDialog = null }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            containerColor = DarkSurface
        )
    }
    
    // Diálogo de desativar/reativar
    showDesativarDialog?.let { admin ->
        AlertDialog(
            onDismissRequest = { showDesativarDialog = null },
            title = {
                Text(
                    if (admin.ativo) "Desativar Conta" else "Reativar Conta",
                    color = TextWhite
                )
            },
            text = {
                Text(
                    if (admin.ativo) {
                        "Tem certeza que deseja desativar a conta do administrador ${admin.nome}?"
                    } else {
                        "Tem certeza que deseja reativar a conta do administrador ${admin.nome}?"
                    },
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val firestore = FirebaseManager.firestore
                                firestore
                                    .collection("bases")
                                    .document(admin.baseId)
                                    .collection("motoristas")
                                    .document(admin.id)
                                    .update("ativo", !admin.ativo)
                                    .await()
                                
                                // Atualizar lista local
                                admins = admins.map {
                                    if (it.id == admin.id && it.baseId == admin.baseId) {
                                        it.copy(ativo = !it.ativo)
                                    } else {
                                        it
                                    }
                                }
                                
                                message = if (admin.ativo) "Conta desativada" else "Conta reativada"
                                showDesativarDialog = null
                            } catch (e: Exception) {
                                error = "Erro ao ${if (admin.ativo) "desativar" else "reativar"} conta: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (admin.ativo) Color(0xFFEF4444) else NeonGreen
                    )
                ) {
                    Text(
                        if (admin.ativo) "Desativar" else "Reativar",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDesativarDialog = null }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            containerColor = DarkSurface
        )
    }
    
    // Dialog de limpar administradores inativos
    if (showLimparInativosDialog) {
        AlertDialog(
            onDismissRequest = { showLimparInativosDialog = false },
            title = {
                Text(
                    "Confirmar Limpeza",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Tem certeza que deseja excluir permanentemente $adminsInativosCount administrador(es) inativo(s)?\n\n" +
                    "⚠️ Esta ação não pode ser desfeita e excluirá todos os dados relacionados.",
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                showLimparInativosDialog = false
                                isLoading = true
                                error = null
                                
                                val firestore = FirebaseManager.firestore
                                var deletadosCount = 0
                                var errosCount = 0
                                
                                // Deletar todos os administradores inativos
                                val adminsInativos = admins.filter { !it.ativo }
                                
                                for (admin in adminsInativos) {
                                    try {
                                        // Verificar se não é superadmin antes de deletar
                                        val motoristaDoc = firestore
                                            .collection("bases")
                                            .document(admin.baseId)
                                            .collection("motoristas")
                                            .document(admin.id)
                                            .get()
                                            .await()
                                        
                                        val motorista = motoristaDoc.toObject(Motorista::class.java)
                                        
                                        // Não deletar superadmin
                                        if (motorista?.papel == "superadmin") {
                                            println("⚠️ SuperAdminUsuariosScreen: Ignorando superadmin ${admin.id}")
                                            continue
                                        }
                                        
                                        // Deletar o documento
                                        firestore
                                            .collection("bases")
                                            .document(admin.baseId)
                                            .collection("motoristas")
                                            .document(admin.id)
                                            .delete()
                                            .await()
                                        
                                        deletadosCount++
                                        println("✅ SuperAdminUsuariosScreen: Admin inativo ${admin.nome} deletado")
                                    } catch (e: Exception) {
                                        errosCount++
                                        println("❌ SuperAdminUsuariosScreen: Erro ao deletar ${admin.nome}: ${e.message}")
                                    }
                                }
                                
                                // Recarregar lista
                                val allAdmins = mutableListOf<AdminInfo>()
                                
                                try {
                                    val adminsSnapshot = firestore
                                        .collectionGroup("motoristas")
                                        .whereIn("papel", listOf("admin", "auxiliar"))
                                        .get()
                                        .await()
                                    
                                    val basesMap = mutableMapOf<String, String>()
                                    val basesSnapshot = firestore.collection("bases").get().await()
                                    for (baseDoc in basesSnapshot.documents) {
                                        val baseId = baseDoc.id
                                        if (baseId != "super_admin_base") {
                                            basesMap[baseId] = baseDoc.getString("nome") ?: "Transportadora"
                                        }
                                    }
                                    
                                    for (adminDoc in adminsSnapshot.documents) {
                                        val motorista = adminDoc.toObject(Motorista::class.java)
                                        if (motorista != null) {
                                            val path = adminDoc.reference.path
                                            val pathParts = path.split("/")
                                            if (pathParts.size >= 2) {
                                                val baseId = pathParts[1]
                                                if (baseId != "super_admin_base" && basesMap.containsKey(baseId)) {
                                                    allAdmins.add(
                                                        AdminInfo(
                                                            id = adminDoc.id,
                                                            nome = motorista.nome,
                                                            telefone = motorista.telefone,
                                                            baseId = baseId,
                                                            baseNome = basesMap[baseId] ?: "Transportadora",
                                                            ativo = motorista.ativo,
                                                            criadoEm = motorista.criadoEm
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    val basesSnapshot = firestore.collection("bases").get().await()
                                    
                                    for (baseDoc in basesSnapshot.documents) {
                                        val baseId = baseDoc.id
                                        if (baseId == "super_admin_base") continue
                                        
                                        val baseNome = baseDoc.getString("nome") ?: "Transportadora"
                                        
                                        val adminsSnapshot = firestore
                                            .collection("bases")
                                            .document(baseId)
                                            .collection("motoristas")
                                            .whereIn("papel", listOf("admin", "auxiliar"))
                                            .get()
                                            .await()
                                        
                                        for (adminDoc in adminsSnapshot.documents) {
                                            val motorista = adminDoc.toObject(Motorista::class.java)
                                            if (motorista != null) {
                                                allAdmins.add(
                                                    AdminInfo(
                                                        id = adminDoc.id,
                                                        nome = motorista.nome,
                                                        telefone = motorista.telefone,
                                                        baseId = baseId,
                                                        baseNome = baseNome,
                                                        ativo = motorista.ativo,
                                                        criadoEm = motorista.criadoEm
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                admins = allAdmins.sortedBy { it.nome }
                                
                                if (errosCount == 0) {
                                    message = "$deletadosCount administrador(es) inativo(s) excluído(s) com sucesso"
                                } else {
                                    message = "$deletadosCount administrador(es) excluído(s), $errosCount erro(s)"
                                }
                            } catch (e: Exception) {
                                error = "Erro ao limpar administradores inativos: ${e.message}"
                                println("❌ SuperAdminUsuariosScreen: Erro - ${e.message}")
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444)
                    )
                ) {
                    Text("Deletar Todos", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLimparInativosDialog = false }
                ) {
                    Text("Cancelar", color = TextWhite)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextWhite,
            textContentColor = TextGray
        )
    }
}

@Composable
fun AdminCard(
    admin: AdminInfo,
    onResetPin: () -> Unit,
    onDesativar: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val dataFormatada = remember(admin.criadoEm) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date(admin.criadoEm))
    }
    
    GlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = admin.nome,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = admin.baseNome,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge de status
                    Surface(
                        color = if (admin.ativo) NeonGreen.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (admin.ativo) "Ativo" else "Inativo",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (admin.ativo) NeonGreen else Color(0xFFEF4444),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Menu de ações
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = TextWhite
                        )
                    }
                }
            }
            
            // Informações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Telefone",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Text(
                        text = admin.telefone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column {
                    Text(
                        text = "Criado em",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dataFormatada,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    
    // Menu dropdown
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.background(DarkSurface)
    ) {
        DropdownMenuItem(
            text = { Text("Resetar PIN", color = TextWhite) },
            onClick = {
                showMenu = false
                onResetPin()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (admin.ativo) "Desativar Conta" else "Reativar Conta",
                    color = if (admin.ativo) Color(0xFFEF4444) else NeonGreen
                )
            },
            onClick = {
                showMenu = false
                onDesativar()
            }
        )
    }
}

