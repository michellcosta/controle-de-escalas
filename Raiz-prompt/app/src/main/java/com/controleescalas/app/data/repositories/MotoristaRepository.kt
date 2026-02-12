package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.data.models.StatusMotorista
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.ui.screens.DriverStatusInfo
import com.controleescalas.app.data.models.toAdminMotoristaCardData
import com.controleescalas.app.data.models.toDriverStatusInfo
import com.controleescalas.app.data.repositories.SistemaRepository
import com.controleescalas.app.utils.MonetizacaoUtils
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Repository para operações de Motorista
 */
class MotoristaRepository {
    private val firestore = FirebaseManager.firestore
    
    /**
     * Garante que o usuário esteja autenticado (anônimo ou não)
     */
    private suspend fun ensureAuth() {
        if (FirebaseManager.auth.currentUser == null) {
            println("👤 MotoristaRepository: Usuário não autenticado. Tentando login anônimo...")
            try {
                FirebaseManager.auth.signInAnonymously().await()
                println("✅ MotoristaRepository: Login anônimo realizado com sucesso: ${FirebaseManager.auth.currentUser?.uid}")
            } catch (e: Exception) {
                println("❌ MotoristaRepository: Falha no login anônimo: ${e.message}")
            }
        }
    }
    
    /**
     * Normalizar telefone removendo caracteres especiais
     */
    private fun normalizeTelefone(telefone: String): String {
        return telefone.replace(Regex("[^0-9]"), "")
    }

    /**
     * Criar motorista
     */
    suspend fun createMotorista(
        baseId: String,
        nome: String,
        telefone: String,
        pin: String,
        papel: String = "motorista",
        modalidade: String = "FROTA",
        criadoPor: String? = null
    ): String? {
        return try {
            ensureAuth()
            
            // VALIDAÇÃO: Impedir criação de super admin
            if (papel == "superadmin") {
                println("❌ MotoristaRepository: Tentativa de criar super admin bloqueada!")
                throw Exception("Não é permitido criar super admins. Apenas o sistema pode criar o super admin único.")
            }
            
            println("👤 MotoristaRepository: Criando motorista: $nome ($papel) - Telefone: $telefone - Modalidade: $modalidade")
            
            // ✅ NOVO: Verificar limite de motoristas (monetização)
            val sistemaRepository = SistemaRepository()
            val monetizacaoAtiva = sistemaRepository.verificarMonetizacaoAtiva()
            
            if (monetizacaoAtiva) {
                // Contar motoristas ativos
                val motoristasAtivos = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereEqualTo("ativo", true)
                    .get()
                    .await()
                    .documents.size
                
                // Limite padrão para plano gratuito: 5 motoristas
                val limiteMotoristas = 5
                
                if (motoristasAtivos >= limiteMotoristas) {
                    println("❌ MotoristaRepository: Limite de $limiteMotoristas motoristas atingido (ativo: $motoristasAtivos)")
                    throw Exception("Limite de $limiteMotoristas motoristas atingido. Faça upgrade para o plano premium para motoristas ilimitados.")
                }
                
                println("✅ MotoristaRepository: Verificação de limite OK ($motoristasAtivos/$limiteMotoristas)")
            } else {
                println("ℹ️ MotoristaRepository: Monetização desativada, sem limite de motoristas")
            }
            
            val telefoneNormalizado = normalizeTelefone(telefone)
            
            // Verificar se já existe um motorista inativo com este telefone
            val motoristaInativo = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .whereEqualTo("telefone", telefoneNormalizado)
                .whereEqualTo("ativo", false)
                .limit(1)
                .get()
                .await()
            
            if (motoristaInativo.documents.isNotEmpty()) {
                // Reativar motorista existente e resetar status
                val docRef = motoristaInativo.documents[0].reference
                val motoristaId = docRef.id
                
                println("✅ MotoristaRepository: Motorista inativo encontrado, reativando: $motoristaId")
                
                // Atualizar dados do motorista e reativar
                docRef.update(
                    mapOf(
                        "nome" to nome,
                        "telefone" to telefoneNormalizado,
                        "pinHash" to pin,
                        "papel" to papel,
                        "modalidade" to modalidade,
                        "ativo" to true,
                        "criadoPor" to (criadoPor ?: "")
                    )
                ).await()
                
                // Resetar status para A_CAMINHO (SEMPRE resetar quando reativar)
                try {
                    val statusRef = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("status_motoristas")
                        .document(motoristaId)
                    
                    // Verificar se o status existe antes de resetar
                    val statusExistente = statusRef.get().await()
                    if (statusExistente.exists()) {
                        val statusAntigo = statusExistente.toObject(StatusMotorista::class.java)
                        println("⚠️ MotoristaRepository: Status antigo encontrado: ${statusAntigo?.estado}, resetando para A_CAMINHO")
                    } else {
                        println("ℹ️ MotoristaRepository: Status não existe, criando novo com A_CAMINHO")
                    }
                    
                    val status = StatusMotorista(
                        id = motoristaId,
                        motoristaId = motoristaId,
                        baseId = baseId,
                        estado = "A_CAMINHO",
                        mensagem = "Aguardando instruções",
                        vagaAtual = null,
                        rotaAtual = null,
                        inicioCarregamento = null,
                        fimCarregamento = null,
                        confirmadoEm = null,
                        atualizadoEm = System.currentTimeMillis()
                    )
                    
                    // Usar set() para sobrescrever completamente o documento
                    statusRef.set(status).await()
                    
                    // Verificar se foi salvo corretamente
                    val statusVerificado = statusRef.get().await().toObject(StatusMotorista::class.java)
                    if (statusVerificado?.estado == "A_CAMINHO") {
                        println("✅ MotoristaRepository: Status resetado para A_CAMINHO com sucesso")
                    } else {
                        println("❌ MotoristaRepository: ERRO! Status não foi resetado corretamente. Estado atual: ${statusVerificado?.estado}")
                    }
                } catch (e: Exception) {
                    println("❌ MotoristaRepository: Erro ao resetar status: ${e.message}")
                    e.printStackTrace()
                    // Não falhar a reativação se o status não puder ser resetado
                }
                
                println("✅ MotoristaRepository: Motorista reativado com sucesso: $motoristaId")
                return motoristaId
            }
            
            // Se não encontrou motorista inativo, criar novo
            val motorista = Motorista(
                nome = nome,
                telefone = telefoneNormalizado,
                pinHash = pin, // Já deve vir como hash
                papel = papel,
                modalidade = modalidade,
                baseId = baseId,
                criadoPor = criadoPor
            )
            
            val docRef = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .add(motorista)
                .await()
            
            val motoristaId = docRef.id
            println("✅ MotoristaRepository: Motorista criado com ID: $motoristaId")
            
            motoristaId
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao criar motorista: ${e.message}")
            null
        }
    }
    
    /**
     * Buscar motoristas de uma base
     */
    suspend fun getMotoristas(baseId: String): List<AdminMotoristaCardData> {
        return try {
            ensureAuth()
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .whereEqualTo("ativo", true)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val motorista = doc.toObject(Motorista::class.java)
                motorista?.toAdminMotoristaCardData()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Buscar nome do motorista por ID
     * Se não encontrar pelo ID, tenta buscar pelo telefone (caso o ID seja o telefone)
     */
    suspend fun getMotoristaNome(motoristaId: String, baseId: String): String? {
        return try {
            // VALIDAÇÃO: Verificar se os valores não estão vazios
            if (motoristaId.isBlank() || baseId.isBlank()) {
                println("❌ MotoristaRepository.getMotoristaNome: motoristaId ou baseId está vazio! motoristaId='$motoristaId', baseId='$baseId'")
                return null
            }
            
            println("🔍 MotoristaRepository.getMotoristaNome: Buscando motoristaId=$motoristaId, baseId=$baseId")
            ensureAuth()
            val motoristaIdTrimmed = motoristaId.trim()
            val docPath = "bases/$baseId/motoristas/$motoristaIdTrimmed"
            println("📂 MotoristaRepository: Caminho do documento: $docPath")
            
            // Primeiro, tentar buscar pelo ID do documento
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(motoristaIdTrimmed)
                .get()
                .await()
            
            println("📄 MotoristaRepository: Documento existe? ${snapshot.exists()}")
            println("📄 MotoristaRepository: Documento ID retornado: ${snapshot.id}")
            
            if (snapshot.exists()) {
                val nome = snapshot.getString("nome")
                println("✅ MotoristaRepository.getMotoristaNome: Documento existe, nome=$nome")
                return nome
            }
            
            // Se não encontrou pelo ID, tentar buscar pelo telefone (caso o motoristaId seja o telefone)
            println("⚠️ MotoristaRepository.getMotoristaNome: Documento NÃO existe para motoristaId='$motoristaIdTrimmed'")
            println("🔍 Tentando buscar pelo telefone como alternativa...")
            
            try {
                val telefoneNormalizado = normalizeTelefone(motoristaIdTrimmed)
                val buscaPorTelefone = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                
                val docPorTelefone = buscaPorTelefone.documents.firstOrNull()
                if (docPorTelefone != null) {
                    val nome = docPorTelefone.getString("nome")
                    println("✅ MotoristaRepository.getMotoristaNome: Encontrado pelo telefone! ID real='${docPorTelefone.id}', nome=$nome")
                    println("⚠️ ATENÇÃO: O motoristaId passado ('$motoristaIdTrimmed') é o telefone, não o ID do documento!")
                    println("⚠️ O ID correto do documento é: '${docPorTelefone.id}'")
                    return nome
                }
            } catch (e: Exception) {
                println("⚠️ Erro ao buscar por telefone: ${e.message}")
            }
            
            // Se ainda não encontrou, listar todos para debug
            println("🔍 Listando todos os motoristas da base para debug...")
            try {
                val alternativa = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .get()
                    .await()
                
                println("📊 Total de motoristas na base: ${alternativa.documents.size}")
                alternativa.documents.forEach { doc ->
                    val m = doc.toObject(Motorista::class.java)
                    println("  👤 Motorista: ID='${doc.id}' (Buscando: '$motoristaIdTrimmed'), Nome=${m?.nome}, Telefone=${m?.telefone}")
                    
                    // Verificar se há correspondência por telefone
                    if (m?.telefone?.trim() == motoristaIdTrimmed || normalizeTelefone(m?.telefone ?: "") == normalizeTelefone(motoristaIdTrimmed)) {
                        println("    ✅ MATCH FOUND POR TELEFONE! ID correto: '${doc.id}'")
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Erro ao buscar motoristas alternativos: ${e.message}")
            }
            
            null
        } catch (e: Exception) {
            println("❌ MotoristaRepository.getMotoristaNome: Erro ao buscar nome: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Observar mudanças no nome do motorista em tempo real
     * Se não encontrar pelo ID, tenta buscar pelo telefone
     */
    fun observeMotoristaNome(
        motoristaId: String,
        baseId: String,
        onUpdate: (String?) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        // VALIDAÇÃO: Verificar se os valores não estão vazios
        if (motoristaId.isBlank() || baseId.isBlank()) {
            println("❌ MotoristaRepository.observeMotoristaNome: motoristaId ou baseId está vazio! motoristaId='$motoristaId', baseId='$baseId'")
            onError(IllegalArgumentException("motoristaId ou baseId não pode estar vazio"))
            // Retornar um listener vazio que não faz nada
            return object : com.google.firebase.firestore.ListenerRegistration {
                override fun remove() {}
            }
        }
        
        val motoristaIdTrimmed = motoristaId.trim()
        val docPath = "bases/$baseId/motoristas/$motoristaIdTrimmed"
        println("🔍 MotoristaRepository.observeMotoristaNome: Iniciando listener para motorista '$motoristaIdTrimmed' na base $baseId")
        println("📂 MotoristaRepository.observeMotoristaNome: Caminho do documento: $docPath")
        
        // Primeiro, tentar buscar pelo ID
        val docRef = firestore
            .collection("bases")
            .document(baseId)
            .collection("motoristas")
            .document(motoristaIdTrimmed)
        
        return docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("❌ MotoristaRepository.observeMotoristaNome: Erro no listener: ${error.message}")
                onError(error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                println("📄 MotoristaRepository.observeMotoristaNome: Documento existe? ${snapshot.exists()}")
                println("📄 MotoristaRepository.observeMotoristaNome: Documento ID: ${snapshot.id}")
                
                if (snapshot.exists()) {
                    val nome = snapshot.getString("nome")
                    println("✅ MotoristaRepository.observeMotoristaNome: Nome recebido: $nome")
                    onUpdate(nome)
                } else {
                    println("⚠️ MotoristaRepository.observeMotoristaNome: Documento não existe para motorista '$motoristaIdTrimmed'")
                    println("⚠️ MotoristaRepository.observeMotoristaNome: Tentou buscar em: $docPath")
                    
                    // Tentar buscar pelo telefone em background
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val telefoneNormalizado = normalizeTelefone(motoristaIdTrimmed)
                            val buscaPorTelefone = firestore
                                .collection("bases")
                                .document(baseId)
                                .collection("motoristas")
                                .whereEqualTo("telefone", telefoneNormalizado)
                                .whereEqualTo("ativo", true)
                                .limit(1)
                                .get()
                                .await()
                            
                            val docPorTelefone = buscaPorTelefone.documents.firstOrNull()
                            if (docPorTelefone != null) {
                                val nome = docPorTelefone.getString("nome")
                                println("✅ MotoristaRepository.observeMotoristaNome: Encontrado pelo telefone! ID real='${docPorTelefone.id}', nome=$nome")
                                onUpdate(nome)
                                
                                // Configurar listener no documento correto
                                // (mas não podemos retornar um novo listener aqui, então apenas atualizamos o nome)
                            }
                        } catch (e: Exception) {
                            println("⚠️ Erro ao buscar por telefone no listener: ${e.message}")
                        }
                    }
                    
                    onUpdate(null)
                }
            } else {
                println("⚠️ MotoristaRepository.observeMotoristaNome: Snapshot é null")
                onUpdate(null)
            }
        }
    }
    
    /**
     * Atualizar papel do usuário
     */
    suspend fun updateUserRole(userId: String, baseId: String, newRole: String) {
        try {
            ensureAuth()
            
            // VALIDAÇÃO: Impedir mudança para superadmin
            if (newRole == "superadmin") {
                println("❌ MotoristaRepository: Tentativa de promover para superadmin bloqueada!")
                throw Exception("Não é permitido promover usuários para super admin.")
            }
            
            // VALIDAÇÃO: Impedir remoção de superadmin
            val motoristaDoc = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .get()
                .await()
            
            if (motoristaDoc.exists()) {
                val motorista = motoristaDoc.toObject(Motorista::class.java)
                if (motorista?.papel == "superadmin") {
                    println("❌ MotoristaRepository: Tentativa de alterar papel do super admin bloqueada!")
                    throw Exception("O Super Admin não pode ter seu papel alterado.")
                }
            }
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .update("papel", newRole)
                .await()
            
            println("✅ MotoristaRepository: Papel atualizado para $newRole")
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao atualizar papel: ${e.message}")
            throw e
        }
    }
    
    /**
     * Verificar se um motorista é super admin
     */
    suspend fun isSuperAdmin(motoristaId: String, baseId: String): Boolean {
        return try {
            ensureAuth()
            val motoristaDoc = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(motoristaId)
                .get()
                .await()
            
            if (motoristaDoc.exists()) {
                val motorista = motoristaDoc.toObject(Motorista::class.java)
                val isSuper = motorista?.papel == "superadmin"
                println("🔍 MotoristaRepository.isSuperAdmin: $motoristaId é super admin? $isSuper")
                isSuper
            } else {
                false
            }
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao verificar super admin: ${e.message}")
            false
        }
    }
    
    /**
     * Verificar se um motorista é super admin (busca global)
     */
    suspend fun isSuperAdminGlobal(motoristaId: String): Boolean {
        return try {
            ensureAuth()
            // Buscar em todas as bases
            val basesSnapshot = firestore.collection("bases").get().await()
            
            for (baseDoc in basesSnapshot.documents) {
                val motoristaDoc = firestore
                    .collection("bases")
                    .document(baseDoc.id)
                    .collection("motoristas")
                    .document(motoristaId)
                    .get()
                    .await()
                
                if (motoristaDoc.exists()) {
                    val motorista = motoristaDoc.toObject(Motorista::class.java)
                    if (motorista?.papel == "superadmin") {
                        println("✅ MotoristaRepository.isSuperAdminGlobal: $motoristaId é super admin")
                        return true
                    }
                }
            }
            
            false
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao verificar super admin global: ${e.message}")
            false
        }
    }
    
    /**
     * Contar quantos admins ativos existem na base (excluindo super admins)
     */
    suspend fun countActiveAdmins(baseId: String): Int {
        return try {
            ensureAuth()
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .whereEqualTo("papel", "admin")
                .whereEqualTo("ativo", true)
                .get()
                .await()
            
            // Filtrar para garantir que não inclua super admins
            val count = snapshot.documents.count { doc ->
                val motorista = doc.toObject(Motorista::class.java)
                motorista?.papel == "admin" && motorista.papel != "superadmin"
            }
            
            println("✅ MotoristaRepository: Total de admins ativos na base $baseId: $count (super admins excluídos)")
            count
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao contar admins: ${e.message}")
            0
        }
    }
    
    /**
     * Obter papel do usuário
     */
    suspend fun getUserRole(userId: String, baseId: String): String? {
        return try {
            ensureAuth()
            val doc = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .get()
                .await()
            
            val papel = doc.getString("papel")
            println("✅ MotoristaRepository: Papel do usuário $userId: $papel")
            papel
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao obter papel do usuário: ${e.message}")
            null
        }
    }
    
    /**
     * Remover usuário
     */
    suspend fun removeUser(userId: String, baseId: String) {
        try {
            ensureAuth()
            
            // VALIDAÇÃO: Impedir exclusão do super admin
            val motoristaDoc = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .get()
                .await()
            
            if (motoristaDoc.exists()) {
                val motorista = motoristaDoc.toObject(Motorista::class.java)
                
                // VALIDAÇÃO: Impedir exclusão do super admin
                if (motorista?.papel == "superadmin") {
                    println("❌ MotoristaRepository: Tentativa de excluir super admin bloqueada!")
                    throw Exception("O Super Admin não pode ser excluído.")
                }
            }
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .update("ativo", false)
                .await()
            
            println("✅ MotoristaRepository: Usuário removido (desativado)")
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao remover usuário: ${e.message}")
            throw e
        }
    }
    
    /**
     * Atualizar dados de um motorista
     */
    suspend fun updateMotorista(
        motoristaId: String,
        baseId: String,
        nome: String,
        telefone: String,
        modalidade: String,
        pin: String? = null, // PIN opcional
        funcao: String? = null // ✅ NOVO: Função (papel) opcional
    ): Boolean {
        return try {
            ensureAuth()
            
            // Verificar se o usuário é superadmin antes de permitir alteração
            val motoristaAtual = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(motoristaId)
                .get()
                .await()
                .toObject(com.controleescalas.app.data.models.Motorista::class.java)
            
            if (motoristaAtual?.papel == "superadmin") {
                println("❌ MotoristaRepository: Não é possível alterar superadmin")
                return false
            }
            
            val updates = hashMapOf<String, Any>(
                "nome" to nome,
                "telefone" to telefone,
                "modalidade" to modalidade
            )
            
            // ✅ Se função foi fornecida e não é superadmin, atualizar o papel
            if (funcao != null && funcao.isNotBlank() && funcao != "superadmin") {
                updates["papel"] = funcao
                println("✅ MotoristaRepository: Função será atualizada para: $funcao")
            }
            
            // ✅ Se PIN foi fornecido, fazer hash e adicionar aos updates
            if (pin != null && pin.isNotBlank()) {
                val authRepository = com.controleescalas.app.data.repositories.AuthRepository()
                val pinHash = authRepository.hashPin(pin)
                updates["pinHash"] = pinHash
                println("✅ MotoristaRepository: PIN será atualizado (hashado)")
            }
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(motoristaId)
                .update(updates)
                .await()
            
            println("✅ MotoristaRepository: Motorista atualizado")
            true
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao atualizar motorista: ${e.message}")
            false
        }
    }
    
    /**
     * Buscar status atual de um motorista
     */
    suspend fun getStatusMotorista(motoristaId: String, baseId: String): DriverStatusInfo? {
        if (motoristaId.isBlank() || baseId.isBlank()) {
            println("❌ MotoristaRepository.getStatusMotorista: motoristaId ou baseId está vazio!")
            return null
        }
        
        return try {
            ensureAuth()
            val motoristaIdTrimmed = motoristaId.trim()
            println("🔍 MotoristaRepository.getStatusMotorista: Buscando status para motorista '$motoristaIdTrimmed' na base $baseId")
            
            val docRef = firestore
                .collection("bases")
                .document(baseId)
                .collection("status_motoristas")
                .document(motoristaIdTrimmed)
            
            val snapshot = docRef.get().await()
            
            if (snapshot.exists()) {
                val status = snapshot.toObject(StatusMotorista::class.java)
                val statusInfo = status?.toDriverStatusInfo()
                println("✅ MotoristaRepository.getStatusMotorista: Status encontrado - estado=${status?.estado}, mensagem=${status?.mensagem}")
                statusInfo
            } else {
                println("⚠️ MotoristaRepository.getStatusMotorista: Documento não existe")
                null
            }
        } catch (e: Exception) {
            println("❌ MotoristaRepository.getStatusMotorista: Erro: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Atualizar status de um motorista
     */
    suspend fun updateStatusMotorista(
        motoristaId: String,
        baseId: String,
        estado: String,
        mensagem: String = "",
        vagaAtual: String? = null,
        rotaAtual: String? = null,
        inicioCarregamento: Long? = null,
        fimCarregamento: Long? = null
    ): Boolean {
        println("💾 MotoristaRepository.updateStatusMotorista: CHAMADO")
        println("   📋 Parâmetros recebidos:")
        println("      👤 Motorista ID: $motoristaId")
        println("      🏢 Base ID: $baseId")
        println("      📍 Estado: $estado")
        println("      💬 Mensagem: $mensagem")
        println("      🅿️ Vaga: $vagaAtual")
        println("      🗺️ Rota: $rotaAtual")
        
        return try {
            ensureAuth()
            
            // Se mudando para CARREGANDO, registrar timestamp de início
            val inicio = if (estado == "CARREGANDO" && inicioCarregamento == null) {
                System.currentTimeMillis()
            } else {
                inicioCarregamento
            }
            
            // Se mudando para CONCLUIDO, registrar timestamp de fim
            val fim = if (estado == "CONCLUIDO" && fimCarregamento == null) {
                System.currentTimeMillis()
            } else {
                fimCarregamento
            }
            
            println("   🔧 Preparando objeto StatusMotorista:")
            println("      📍 Estado final: $estado")
            println("      💬 Mensagem final: $mensagem")
            
            val status = StatusMotorista(
                motoristaId = motoristaId,
                baseId = baseId,
                estado = estado,
                mensagem = mensagem,
                vagaAtual = vagaAtual,
                rotaAtual = rotaAtual,
                inicioCarregamento = inicio,
                fimCarregamento = fim
            )
            
            println("   ✅ Objeto StatusMotorista criado:")
            println("      📍 status.estado = ${status.estado}")
            println("      💬 status.mensagem = ${status.mensagem}")
            
            val docRef = firestore
                .collection("bases")
                .document(baseId)
                .collection("status_motoristas")
                .document(motoristaId)
            
            println("💾 MotoristaRepository.updateStatusMotorista: Salvando status no Firestore...")
            println("   📍 Caminho: bases/$baseId/status_motoristas/$motoristaId")
            println("   📋 Estado: $estado")
            println("   💬 Mensagem: $mensagem")
            println("   🅿️ Vaga: $vagaAtual")
            println("   🗺️ Rota: $rotaAtual")
            
            docRef.set(status).await()
            
            // Verificar se foi salvo corretamente
            val verificado = docRef.get().await()
            val statusVerificado = verificado.toObject(StatusMotorista::class.java)
            println("✅ MotoristaRepository: Status salvo no Firestore")
            println("   ✅ Verificação: estado=${statusVerificado?.estado}, mensagem=${statusVerificado?.mensagem}")
            
            if (statusVerificado?.estado != estado) {
                println("⚠️ MotoristaRepository: ATENÇÃO! Estado salvo (${statusVerificado?.estado}) é diferente do estado solicitado ($estado)")
            }
            
            true
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao atualizar status: ${e.message}")
            false
        }
    }

    /**
     * Confirmar que o motorista entendeu a chamada
     */
    suspend fun confirmarChamada(motoristaId: String, baseId: String): Boolean {
        println("✅ MotoristaRepository.confirmarChamada: CHAMADO")
        println("   👤 Motorista ID: $motoristaId")
        println("   🏢 Base ID: $baseId")
        
        return try {
            ensureAuth()
            
            val docRef = firestore
                .collection("bases")
                .document(baseId)
                .collection("status_motoristas")
                .document(motoristaId)
            
            val confirmadoEm = System.currentTimeMillis()
            docRef.update("confirmadoEm", confirmadoEm).await()
            
            println("✅ MotoristaRepository: Confirmação salva no Firestore - timestamp: $confirmadoEm")
            true
        } catch (e: Exception) {
            println("❌ MotoristaRepository: Erro ao confirmar chamada: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Inscrever-se para atualizações de status de motoristas em tempo real
     */
    fun subscribeToDriverStatus(
        baseId: String,
        onUpdate: (Map<String, StatusMotorista>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return firestore
            .collection("bases")
            .document(baseId)
            .collection("status_motoristas")
            .addSnapshotListener(
                com.google.firebase.firestore.MetadataChanges.INCLUDE
            ) { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                
                // ✅ Detectar se está usando cache
                val isFromCache = snapshot?.metadata?.isFromCache == true
                
                if (isFromCache) {
                    android.util.Log.d("MotoristaRepository", "📦 Status usando cache local - sincronizando em background")
                }
                
                val statusMap = mutableMapOf<String, StatusMotorista>()
                snapshot?.documents?.forEach { doc ->
                    val status = doc.toObject(StatusMotorista::class.java)
                    status?.let {
                        statusMap[it.motoristaId] = it
                    }
                }
                onUpdate(statusMap)
            }
    }
    
    /**
     * Observar mudanças no status do motorista em tempo real
     */
    fun observeStatusMotorista(
        motoristaId: String,
        baseId: String,
        onUpdate: (DriverStatusInfo?) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        if (motoristaId.isBlank() || baseId.isBlank()) {
            println("❌ MotoristaRepository.observeStatusMotorista: motoristaId ou baseId está vazio!")
            onError(IllegalArgumentException("motoristaId ou baseId não pode estar vazio"))
            return object : com.google.firebase.firestore.ListenerRegistration {
                override fun remove() {}
            }
        }
        
        val motoristaIdTrimmed = motoristaId.trim()
        println("🔍 MotoristaRepository.observeStatusMotorista: Iniciando listener para motorista '$motoristaIdTrimmed' na base $baseId")
        
        val docRef = firestore
            .collection("bases")
            .document(baseId)
            .collection("status_motoristas")
            .document(motoristaIdTrimmed)
        
        return docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("❌ MotoristaRepository.observeStatusMotorista: Erro no listener: ${error.message}")
                onError(error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                println("📥 MotoristaRepository.observeStatusMotorista: Snapshot recebido")
                println("   📄 Existe: ${snapshot.exists()}")
                println("   📄 ID: ${snapshot.id}")
                
                if (snapshot.exists()) {
                    try {
                        val status = snapshot.toObject(StatusMotorista::class.java)
                        val statusInfo = status?.toDriverStatusInfo()
                        println("✅ MotoristaRepository.observeStatusMotorista: Status recebido:")
                        println("   📋 Estado: ${status?.estado}")
                        println("   💬 Mensagem: ${status?.mensagem}")
                        println("   🅿️ Vaga: ${status?.vagaAtual}")
                        println("   🗺️ Rota: ${status?.rotaAtual}")
                        println("   📊 StatusInfo: estado=${statusInfo?.estado}, mensagem=${statusInfo?.mensagem}")
                        onUpdate(statusInfo)
                    } catch (e: Exception) {
                        println("❌ MotoristaRepository.observeStatusMotorista: Erro ao converter: ${e.message}")
                        e.printStackTrace()
                        onError(e)
                    }
                } else {
                    println("⚠️ MotoristaRepository.observeStatusMotorista: Documento não existe")
                    onUpdate(null)
                }
            } else {
                println("⚠️ MotoristaRepository.observeStatusMotorista: Snapshot é null")
                onUpdate(null)
            }
        }
    }
    
    /**
     * Resetar status de todos os motoristas de uma base para A_CAMINHO
     */
    suspend fun resetarTodosStatusDaBase(baseId: String): Boolean {
        return try {
            ensureAuth()
            println("🔄 MotoristaRepository.resetarTodosStatusDaBase: Resetando status de todos os motoristas da base $baseId")
            
            // Buscar todos os status da base
            val statusSnapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("status_motoristas")
                .get()
                .await()
            
            var resetados = 0
            for (doc in statusSnapshot.documents) {
                val motoristaId = doc.id
                val statusRef = doc.reference
                
                val status = StatusMotorista(
                    id = motoristaId,
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "A_CAMINHO",
                    mensagem = "Aguardando instruções",
                    vagaAtual = null,
                    rotaAtual = null,
                    inicioCarregamento = null,
                    fimCarregamento = null,
                    confirmadoEm = null,
                    atualizadoEm = System.currentTimeMillis()
                )
                
                statusRef.set(status).await()
                resetados++
                println("✅ MotoristaRepository: Status resetado para motorista $motoristaId")
            }
            
            println("✅ MotoristaRepository.resetarTodosStatusDaBase: $resetados status resetados")
            true
        } catch (e: Exception) {
            println("❌ MotoristaRepository.resetarTodosStatusDaBase: Erro - ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Resetar status de todos os motoristas de todas as bases
     */
    suspend fun resetarTodosStatusDeTodasBases(): Boolean {
        return try {
            ensureAuth()
            println("🔄 MotoristaRepository.resetarTodosStatusDeTodasBases: Iniciando reset de todas as bases")
            
            // Buscar todas as bases
            val basesSnapshot = firestore.collection("bases").get().await()
            var basesProcessadas = 0
            
            for (baseDoc in basesSnapshot.documents) {
                val baseId = baseDoc.id
                resetarTodosStatusDaBase(baseId)
                basesProcessadas++
            }
            
            println("✅ MotoristaRepository.resetarTodosStatusDeTodasBases: $basesProcessadas bases processadas")
            true
        } catch (e: Exception) {
            println("❌ MotoristaRepository.resetarTodosStatusDeTodasBases: Erro - ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
