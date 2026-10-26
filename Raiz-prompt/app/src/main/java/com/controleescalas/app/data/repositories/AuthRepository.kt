package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.LoginResult
import com.controleescalas.app.data.models.Motorista
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class AccountInfo(
    val nome: String,
    val telefone: String,
    val papel: String,
    val baseId: String
)

/**
 * Repository para operações de Autenticação
 */
class AuthRepository {
    private val firestore = FirebaseManager.firestore
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Normalizar telefone removendo caracteres especiais
     */
    private fun normalizeTelefone(telefone: String): String {
        return telefone.replace(Regex("[^0-9]"), "")
    }

    /**
     * Buscar motorista por telefone
     * Usa busca em todas as bases para encontrar o motorista
     */
    suspend fun getMotoristaByTelefone(telefone: String): Motorista? {
        return try {
            val telefoneNormalizado = normalizeTelefone(telefone)
            println("🔍 AuthRepository: Buscando telefone original: '$telefone' -> Normalizado: '$telefoneNormalizado'")
            
            // Primeiro, tentar usar collectionGroup (mais eficiente se configurado)
            try {
                val snapshot = firestore
                    .collectionGroup("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                
                val motorista = snapshot.documents.firstOrNull()?.toObject(Motorista::class.java)
                if (motorista != null) {
                    println("✅ AuthRepository: Motorista encontrado via collectionGroup: ${motorista.nome}")
                    return motorista
                }
            } catch (e: Exception) {
                // Se collectionGroup falhar, usar busca manual
                println("⚠️ AuthRepository: CollectionGroup falhou, usando busca manual: ${e.message}")
            }
            
            // Busca manual em todas as bases
            println("🔍 AuthRepository: Iniciando busca manual em todas as bases...")
            val basesSnapshot = firestore.collection("bases").get().await()
            println("📊 AuthRepository: Encontradas ${basesSnapshot.documents.size} bases para buscar")
            
            for (baseDoc in basesSnapshot.documents) {
                val motoristasSnapshot = baseDoc.reference
                    .collection("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                
                val motorista = motoristasSnapshot.documents.firstOrNull()?.toObject(Motorista::class.java)
                if (motorista != null) {
                    println("✅ AuthRepository: Motorista encontrado via busca manual: ${motorista.nome}")
                    return motorista.copy(id = motoristasSnapshot.documents.firstOrNull()?.id ?: "")
                }
            }
            
            println("❌ AuthRepository: Nenhum motorista encontrado com telefone: '$telefoneNormalizado'")
            null
        } catch (e: Exception) {
            println("❌ AuthRepository: Erro ao buscar motorista por telefone: ${e.message}")
            null
        }
    }

    /**
     * Fazer login com telefone e PIN
     */
    suspend fun login(telefone: String, pin: String): LoginResult {
        return try {
            val telefoneNormalizado = normalizeTelefone(telefone)
            println("🔍 AuthRepository: Tentando login local com: $telefoneNormalizado")
            
            // 1. Garantir Login Anônimo (necessário para ler Firestore)
            if (auth.currentUser == null) {
                println("👤 AuthRepository: Fazendo login anônimo inicial...")
                auth.signInAnonymously().await()
            }
            
            println("👤 AuthRepository: Logado como ${auth.currentUser?.uid} (Anon: ${auth.currentUser?.isAnonymous})")
            
            // ✅ NOVO: Primeiro, verificar se é superadmin (prioridade)
            println("🔍 AuthRepository: Verificando se é superadmin...")
            val superAdminSnapshot = try {
                firestore
                    .collection("bases")
                    .document("super_admin_base")
                    .collection("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("papel", "superadmin")
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
            } catch (e: Exception) {
                println("⚠️ AuthRepository: Erro ao buscar superadmin (pode não existir ainda): ${e.message}")
                null
            }
            
            val userDoc = if (superAdminSnapshot != null && superAdminSnapshot.documents.isNotEmpty() && 
                superAdminSnapshot.documents.first().reference.path.contains("super_admin_base")) {
                println("✅ AuthRepository: Superadmin encontrado!")
                superAdminSnapshot.documents.first()
            } else {
                // 2. Buscar usuário globalmente (Collection Group) - usuários normais
                println("🔍 AuthRepository: Buscando usuário no Firestore (motoristas)...")
                val snapshot = firestore.collectionGroup("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                
                snapshot.documents.firstOrNull()
            }
            
            if (userDoc == null) {
                println("❌ AuthRepository: Usuário não encontrado")
                return LoginResult.Error("Usuário não encontrado")
            }
            
            val motorista = userDoc.toObject(Motorista::class.java)
            if (motorista == null) return LoginResult.Error("Erro nos dados do usuário")
            
            // Recuperar baseId do caminho do documento
            val baseId = userDoc.reference.parent.parent?.id
            
            if (baseId == null) {
                return LoginResult.Error("Erro ao identificar base")
            }
            
            println("✅ AuthRepository: Usuário encontrado: ${motorista.nome} na base $baseId")
            
            // 3. Verificar PIN
            val inputHash = hashPin(pin)
            val storedHash = motorista.pinHash
            
            // Suporte a legado (se não tiver hash, compara direto)
            val isMatch = if (storedHash.length < 10) {
                storedHash == pin
            } else {
                storedHash == inputHash
            }
            
            if (!isMatch) {
                println("❌ AuthRepository: PIN incorreto")
                return LoginResult.Error("PIN incorreto")
            }
            
            println("✅ AuthRepository: PIN confirmado. Login OK.")
            
            // Log detalhado do ID do documento
            println("📄 AuthRepository: userDoc.id='${userDoc.id}'")
            println("📄 AuthRepository: userDoc.reference.path='${userDoc.reference.path}'")
            println("📄 AuthRepository: motorista.nome='${motorista.nome}'")
            
            // 4. Buscar nome da base E verificar status de aprovação
            val baseDoc = try {
                firestore.collection("bases").document(baseId).get().await()
            } catch (e: Exception) {
                println("❌ AuthRepository: Erro ao buscar base: ${e.message}")
                return LoginResult.Error("Erro ao verificar status da transportadora")
            }
            
            val baseName = baseDoc.getString("nome") ?: "Transportadora"
            val statusAprovacao = baseDoc.getString("statusAprovacao") ?: "pendente"
            
            // ✅ NOVO: Superadmin sempre pode fazer login, independente do status da base
            if (motorista.papel != "superadmin" && statusAprovacao != "ativa") {
                when (statusAprovacao) {
                    "pendente" -> {
                        println("⚠️ AuthRepository: Base $baseId está pendente de aprovação")
                        return LoginResult.Error("Sua transportadora está aguardando aprovação. Você receberá uma notificação quando ela for aprovada.")
                    }
                    "rejeitada" -> {
                        println("⚠️ AuthRepository: Base $baseId foi rejeitada")
                        return LoginResult.Error("Sua transportadora foi rejeitada.")
                    }
                    else -> {
                        println("⚠️ AuthRepository: Base $baseId tem status inválido: $statusAprovacao")
                        return LoginResult.Error("Sua transportadora não está disponível para uso.")
                    }
                }
            }
            
            println("✅ AuthRepository: Base $baseId está ativa, login permitido")

            val motoristaId = userDoc.id.trim()
            val authUid = auth.currentUser?.uid
            val documento = userDoc.reference
            CoroutineScope(Dispatchers.IO).launch {
                if (!authUid.isNullOrBlank()) {
                    try {
                        documento.update(mapOf("authUid" to authUid)).await()
                        println("📱 authUid gravado no documento para backend")
                    } catch (e: Exception) {
                        println("⚠️ Erro ao gravar authUid (não crítico): ${e.message}")
                    }
                }
                try {
                    val notificationService = com.controleescalas.app.data.NotificationService(
                        com.controleescalas.app.MainApp.instance.applicationContext
                    )
                    notificationService.saveFcmTokenToFirestore(motoristaId, baseId)
                    notificationService.subscribeToBaseTopic(baseId)
                    println("📱 FCM token e tópicos configurados")
                } catch (e: Exception) {
                    println("⚠️ Erro ao salvar FCM token (não crítico): ${e.message}")
                }
            }

            val loginResult = LoginResult.Success(
                motoristaId = motoristaId,
                baseId = baseId,
                baseName = baseName,
                papel = motorista.papel,
                nome = motorista.nome
            )
            
            println("✅ AuthRepository: LoginResult criado com motoristaId='${loginResult.motoristaId}', nome='${loginResult.nome}'")
            println("✅ AuthRepository: Caminho completo: bases/$baseId/motoristas/${loginResult.motoristaId}")
            
            loginResult
        } catch (e: Exception) {
            println("❌ AuthRepository: Erro: ${e.message}")
            LoginResult.Error("Erro: ${e.message}")
        }
    }

    /**
     * Buscar todas as contas disponíveis
     */
    suspend fun getAllAccounts(): List<AccountInfo> {
        return try {
            val accounts = mutableListOf<AccountInfo>()
            
            // Buscar em todas as bases
            val basesSnapshot = firestore.collection("bases").get().await()
            
            for (baseDoc in basesSnapshot.documents) {
                val baseId = baseDoc.id
                
                // Buscar motoristas dessa base
                val motoristasSnapshot = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereEqualTo("ativo", true)
                    .get()
                    .await()
                
                for (motoristaDoc in motoristasSnapshot.documents) {
                    val motorista = motoristaDoc.toObject(Motorista::class.java)
                    if (motorista != null) {
                        accounts.add(
                            AccountInfo(
                                nome = motorista.nome,
                                telefone = motorista.telefone,
                                papel = motorista.papel,
                                baseId = baseId
                            )
                        )
                    }
                }
            }
            
            println("✅ AuthRepository: ${accounts.size} contas encontradas")
            accounts
        } catch (e: Exception) {
            println("❌ Erro ao buscar contas: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gerar hash do PIN
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    suspend fun telefoneExists(telefone: String): Boolean {
        return getMotoristaByTelefone(telefone) != null
    }
}
