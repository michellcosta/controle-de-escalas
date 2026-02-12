package com.controleescalas.app.data.repositories

import android.util.Log
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.data.repositories.AuthRepository
import kotlinx.coroutines.tasks.await

/**
 * Repository para gerenciar o Super Admin único do sistema
 */
class SuperAdminRepository {
    private val firestore = FirebaseManager.firestore
    private val authRepository = AuthRepository()
    
    companion object {
        // Credenciais do Super Admin único
        private const val SUPER_ADMIN_TELEFONE = "21972739827"
        private const val SUPER_ADMIN_PIN = "886460"
        private const val SUPER_ADMIN_NOME = "Super Admin"
        private const val SUPER_ADMIN_BASE_ID = "super_admin_base" // Base especial para o super admin
        private const val TAG = "SuperAdminRepository"
    }
    
    /**
     * Garantir que o Super Admin existe e é único
     */
    suspend fun ensureSuperAdminExists() {
        try {
            Log.d(TAG, "🔐 Verificando existência do Super Admin...")
            println("🔐 SuperAdminRepository: Verificando existência do Super Admin...")
            
            // Verificar se já existe algum super admin
            val basesSnapshot = firestore.collection("bases").get().await()
            var superAdminFound = false
            var superAdminBaseId: String? = null
            var superAdminId: String? = null
            
            // Buscar em todas as bases
            for (baseDoc in basesSnapshot.documents) {
                val motoristasSnapshot = firestore
                    .collection("bases")
                    .document(baseDoc.id)
                    .collection("motoristas")
                    .whereEqualTo("papel", "superadmin")
                    .whereEqualTo("ativo", true)
                    .get()
                    .await()
                
                for (motoristaDoc in motoristasSnapshot.documents) {
                    val motorista = motoristaDoc.toObject(Motorista::class.java)
                    val telefoneNormalizado = normalizeTelefone(SUPER_ADMIN_TELEFONE)
                    
                    if (motorista?.telefone == telefoneNormalizado) {
                        // Super admin correto encontrado
                        superAdminFound = true
                        superAdminBaseId = baseDoc.id
                        superAdminId = motoristaDoc.id
                        Log.d(TAG, "✅ Super Admin encontrado na base ${baseDoc.id}")
                        println("✅ SuperAdminRepository: Super Admin encontrado na base ${baseDoc.id}")
                        break
                    } else {
                        // Encontrou outro super admin - REMOVER (não deveria existir)
                        Log.w(TAG, "⚠️ Super admin não autorizado encontrado, removendo...")
                        println("⚠️ SuperAdminRepository: Super admin não autorizado encontrado, removendo...")
                        motoristaDoc.reference.update("ativo", false).await()
                        Log.d(TAG, "✅ Super admin não autorizado removido")
                        println("✅ SuperAdminRepository: Super admin não autorizado removido")
                    }
                }
                if (superAdminFound) break
            }
            
            if (!superAdminFound) {
                // Criar base especial para o super admin se não existir
                val baseRef = firestore.collection("bases").document(SUPER_ADMIN_BASE_ID)
                val baseDoc = baseRef.get().await()
                
                if (!baseDoc.exists()) {
                    baseRef.set(
                        mapOf(
                            "nome" to "Sistema",
                            "transportadora" to "Sistema",
                            "corTema" to "#16A34A",
                            "statusAprovacao" to "ativa",
                            "ativo" to true,
                            "criadoEm" to System.currentTimeMillis()
                        )
                    ).await()
                    Log.d(TAG, "✅ Base especial criada para Super Admin")
                    println("✅ SuperAdminRepository: Base especial criada para Super Admin")
                } else {
                    // ✅ NOVO: Garantir que a base existente está ativa
                    val statusAtual = baseDoc.getString("statusAprovacao")
                    if (statusAtual != "ativa") {
                        baseRef.update("statusAprovacao", "ativa").await()
                        Log.d(TAG, "✅ Base do Super Admin atualizada para ativa")
                        println("✅ SuperAdminRepository: Base do Super Admin atualizada para ativa")
                    }
                }
                
                // Criar o Super Admin
                val telefoneNormalizado = normalizeTelefone(SUPER_ADMIN_TELEFONE)
                val pinHash = authRepository.hashPin(SUPER_ADMIN_PIN)
                
                val superAdminRef = baseRef.collection("motoristas").document()
                superAdminRef.set(
                    Motorista(
                        id = superAdminRef.id,
                        nome = SUPER_ADMIN_NOME,
                        telefone = telefoneNormalizado,
                        pinHash = pinHash,
                        papel = "superadmin",
                        modalidade = "FROTA",
                        baseId = SUPER_ADMIN_BASE_ID,
                        ativo = true,
                        criadoEm = System.currentTimeMillis()
                    )
                ).await()
                
                Log.d(TAG, "✅ Super Admin criado com sucesso!")
                println("✅ SuperAdminRepository: Super Admin criado com sucesso!")
                println("   Telefone: $telefoneNormalizado")
                println("   Base ID: $SUPER_ADMIN_BASE_ID")
            } else {
                // Verificar se o super admin está na base correta
                if (superAdminBaseId != SUPER_ADMIN_BASE_ID) {
                    Log.w(TAG, "⚠️ Super Admin está em base diferente, mas mantendo onde está")
                    println("⚠️ SuperAdminRepository: Super Admin está em base diferente, mas mantendo onde está")
                } else {
                    // ✅ NOVO: Garantir que a base do superadmin está ativa
                    val baseRef = firestore.collection("bases").document(SUPER_ADMIN_BASE_ID)
                    val baseDoc = baseRef.get().await()
                    if (baseDoc.exists()) {
                        val statusAtual = baseDoc.getString("statusAprovacao")
                        if (statusAtual != "ativa") {
                            baseRef.update("statusAprovacao", "ativa").await()
                            Log.d(TAG, "✅ Base do Super Admin atualizada para ativa")
                            println("✅ SuperAdminRepository: Base do Super Admin atualizada para ativa")
                        }
                    }
                }
                
                // Garantir que os dados estão corretos
                val superAdminDoc = firestore
                    .collection("bases")
                    .document(superAdminBaseId!!)
                    .collection("motoristas")
                    .document(superAdminId!!)
                    .get()
                    .await()
                
                val motorista = superAdminDoc.toObject(Motorista::class.java)
                val telefoneNormalizado = normalizeTelefone(SUPER_ADMIN_TELEFONE)
                val pinHash = authRepository.hashPin(SUPER_ADMIN_PIN)
                
                // Atualizar se necessário
                if (motorista?.telefone != telefoneNormalizado || motorista.pinHash != pinHash || motorista.nome != SUPER_ADMIN_NOME) {
                    superAdminDoc.reference.update(
                        mapOf(
                            "telefone" to telefoneNormalizado,
                            "pinHash" to pinHash,
                            "nome" to SUPER_ADMIN_NOME,
                            "papel" to "superadmin",
                            "ativo" to true
                        )
                    ).await()
                    Log.d(TAG, "✅ Dados do Super Admin atualizados")
                    println("✅ SuperAdminRepository: Dados do Super Admin atualizados")
                }
            }
            
            Log.d(TAG, "✅ Super Admin único garantido")
            println("✅ SuperAdminRepository: Super Admin único garantido")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao garantir Super Admin: ${e.message}", e)
            println("❌ SuperAdminRepository: Erro ao garantir Super Admin: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Verificar se um telefone é o Super Admin
     */
    fun isSuperAdminTelefone(telefone: String): Boolean {
        return normalizeTelefone(telefone) == normalizeTelefone(SUPER_ADMIN_TELEFONE)
    }
    
    /**
     * Normalizar telefone
     */
    private fun normalizeTelefone(telefone: String): String {
        return telefone.replace(Regex("[^0-9]"), "")
    }
}

