package com.controleescalas.app.data

import com.controleescalas.app.data.models.Motorista
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class AccountInfo(
    val nome: String,
    val telefone: String,
    val papel: String,
    val baseId: String
)

/**
 * AuthRepository - Gerenciamento de autenticação
 * 
 * Implementa login com telefone + PIN usando Firebase Cloud Functions
 */
class AuthRepository {
    private val repository = Repository()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseManager.firestore
    
    /**
     * Fazer login com telefone e PIN (Modo Gratuito / Local)
     * 
     * 1. Garante login anônimo no Firebase Auth (para ter permissão de leitura básica)
     * 2. Busca usuário no Firestore pelo telefone
     * 3. Verifica hash do PIN localmente
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
            
            // 2. Buscar usuário globalmente (Collection Group)
            // Nota: Requer índice composto (já criado)
            println("🔍 AuthRepository: Buscando usuário no Firestore (motoristas)...")
            val snapshot = firestore.collectionGroup("motoristas")
                .whereEqualTo("telefone", telefoneNormalizado)
                .whereEqualTo("ativo", true)
                .limit(1)
                .get()
                .await()
            
            val userDoc = snapshot.documents.firstOrNull()
            
            if (userDoc == null) {
                println("❌ AuthRepository: Usuário não encontrado")
                return LoginResult.Error("Usuário não encontrado")
            }
            
            val motorista = userDoc.toObject(Motorista::class.java)
            if (motorista == null) return LoginResult.Error("Erro nos dados do usuário")
            
            // Recuperar baseId do caminho do documento
            // bases/{baseId}/usuarios/{userId}
            val baseId = userDoc.reference.parent.parent?.id
            
            if (baseId == null) {
                return LoginResult.Error("Erro ao identificar base")
            }
            
            println("✅ AuthRepository: Usuário encontrado: ${motorista.nome} na base $baseId")
            
            // 3. Verificar PIN
            // O PIN no banco já deve estar hasheado (SHA-256 neste MVP)
            val inputHash = hashPin(pin)
            val storedHash = motorista.pinHash
            
            // Suporte a legado (se não tiver hash, compara direto - perigoso, mas útil pra migração)
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
            
            LoginResult.Success(
                motoristaId = userDoc.id,
                baseId = baseId,
                papel = motorista.papel,
                nome = motorista.nome
            )
        } catch (e: Exception) {
            println("❌ AuthRepository: Erro: ${e.message}")
            LoginResult.Error("Erro: ${e.message}")
        }
    }
    
    /**
     * Admin define PIN (Modo Local)
     */
    suspend fun adminSetPin(targetUid: String, baseId: String, newPin: String): Boolean {
        return try {
            val pinHash = hashPin(newPin)
            
            firestore.collection("bases")
                .document(baseId)
                .collection("usuarios")
                .document(targetUid)
                .update("pinHash", pinHash)
                .await()
                
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Criar usuário (Modo Local)
     */
    suspend fun createUser(
        baseId: String,
        nome: String,
        telefone: String,
        pin: String,
        papel: String = "motorista",
        criadoPor: String? = null
    ): Boolean {
        return try {
            val pinHash = hashPin(pin)
            repository.createMotorista(baseId, nome, telefone, pinHash, papel, criadoPor) != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Buscar todas as contas disponíveis (para mostrar na tela de login)
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
    
    private fun normalizeTelefone(telefone: String): String {
        return telefone.replace(Regex("[^0-9]"), "")
    }
    
    suspend fun telefoneExists(telefone: String): Boolean {
        return repository.getMotoristaByTelefone(telefone) != null
    }
}
