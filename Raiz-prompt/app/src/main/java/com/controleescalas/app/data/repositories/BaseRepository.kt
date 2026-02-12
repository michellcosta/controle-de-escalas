package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.ui.screens.CreateBaseData
import kotlinx.coroutines.tasks.await

/**
 * Repository para operações de Base
 */
class BaseRepository {
    private val firestore = FirebaseManager.firestore
    
    // ✅ Cache de bases (10 minutos)
    private var cachedBases: List<Base>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutos

    /**
     * Criar nova base
     */
    suspend fun createBase(baseData: CreateBaseData): String? {
        return try {
            println("🏗️ BaseRepository: Criando base: ${baseData.nomeBase}")
            
            val base = Base(
                nome = baseData.nomeBase,
                transportadora = baseData.nomeTransportadora,
                corTema = baseData.corTema,
                statusAprovacao = "pendente" // Nova base começa como pendente
            )
            
            val docRef = firestore.collection("bases").add(base).await()
            val baseId = docRef.id
            
            println("✅ BaseRepository: Base criada com ID: $baseId")
            
            // Nota: A criação do admin será delegada ao AuthRepository ou MotoristaRepository
            // para manter a separação de responsabilidades.
            // Por enquanto, retornamos apenas o ID da base.
            
            baseId
        } catch (e: Exception) {
            println("❌ BaseRepository: Erro ao criar base: ${e.message}")
            null
        }
    }
    
    /**
     * Buscar base por ID
     */
    suspend fun getBase(baseId: String): Base? {
        return try {
            val doc = firestore.collection("bases").document(baseId).get().await()
            doc.toObject(Base::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Buscar todas as bases (para Super Admin) - com cache
     */
    suspend fun getAllBases(forceRefresh: Boolean = false): List<Base> {
        // Verificar cache
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedBases != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            println("✅ BaseRepository: Retornando bases do cache (${cachedBases!!.size} bases)")
            return cachedBases!!
        }
        
        return try {
            println("🔄 BaseRepository: Buscando bases do banco...")
            val snapshot = firestore.collection("bases").get().await()
            val bases = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Base::class.java)?.copy(id = doc.id)
            }
            
            // Atualizar cache
            cachedBases = bases
            cacheTimestamp = now
            
            println("✅ BaseRepository: ${bases.size} bases carregadas e cache atualizado")
            bases
        } catch (e: Exception) {
            println("❌ BaseRepository: Erro ao buscar todas as bases: ${e.message}")
            // Retornar cache antigo se houver erro
            cachedBases ?: emptyList()
        }
    }
    
    /**
     * Invalidar cache de bases
     */
    fun invalidateBasesCache() {
        cachedBases = null
        cacheTimestamp = 0
        println("🔄 BaseRepository: Cache de bases invalidado")
    }
    
    /**
     * Buscar bases por status de aprovação
     */
    suspend fun getBasesPorStatus(status: String): List<Base> {
        return try {
            val snapshot = firestore.collection("bases")
                .whereEqualTo("statusAprovacao", status)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Base::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            println("❌ BaseRepository: Erro ao buscar bases por status: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Aprovar base (Super Admin)
     */
    suspend fun aprovarBase(baseId: String, superAdminId: String): Boolean {
        invalidateBasesCache() // ✅ Invalidar cache ao modificar
        return try {
            firestore.collection("bases").document(baseId)
                .update(
                    mapOf(
                        "statusAprovacao" to "ativa",
                        "aprovadoPor" to superAdminId,
                        "aprovadoEm" to System.currentTimeMillis()
                    )
                )
                .await()
            println("✅ BaseRepository: Base $baseId aprovada por super admin $superAdminId")
            true
        } catch (e: Exception) {
            println("❌ BaseRepository: Erro ao aprovar base: ${e.message}")
            false
        }
    }
    
    /**
     * Rejeitar base (Super Admin)
     * ATENÇÃO: Rejeitar = Excluir completamente a base e todos os dados relacionados
     */
    suspend fun rejeitarBase(baseId: String, superAdminId: String): Boolean {
        // ✅ PROTEÇÃO: Não permitir rejeitar a base do superadmin
        if (baseId == "super_admin_base") {
            println("🚫 BaseRepository: Não é possível rejeitar a base do superadmin!")
            return false
        }
        
        // Rejeitar = Excluir completamente
        invalidateBasesCache() // ✅ Invalidar cache ao modificar
        println("🗑️ BaseRepository: Rejeitando base $baseId (excluindo completamente)")
        return deleteBase(baseId)
    }
    
    /**
     * Buscar bases disponíveis para um usuário
     * - Admin normal: apenas bases ativas onde ele é admin
     * - Super Admin: todas as bases (ativas, pendentes, rejeitadas)
     */
    suspend fun getBasesDisponiveisParaUsuario(motoristaId: String, papel: String): List<Base> {
        return try {
            if (papel == "superadmin") {
                // Super Admin vê todas as bases
                getAllBases()
            } else {
                // Admin normal vê apenas bases ativas onde ele é admin
                val basesSnapshot = firestore.collection("bases")
                    .whereEqualTo("statusAprovacao", "ativa")
                    .get()
                    .await()
                
                val bases = basesSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Base::class.java)?.copy(id = doc.id)
                }
                
                // Filtrar apenas bases onde o usuário é admin
                val basesDoUsuario = mutableListOf<Base>()
                for (base in bases) {
                    val motoristaDoc = firestore
                        .collection("bases")
                        .document(base.id)
                        .collection("motoristas")
                        .document(motoristaId)
                        .get()
                        .await()
                    
                    if (motoristaDoc.exists()) {
                        val motorista = motoristaDoc.toObject(com.controleescalas.app.data.models.Motorista::class.java)
                        if (motorista != null && motorista.papel == "admin" && motorista.ativo) {
                            basesDoUsuario.add(base)
                        }
                    }
                }
                
                basesDoUsuario
            }
        } catch (e: Exception) {
            println("❌ BaseRepository: Erro ao buscar bases disponíveis: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Deletar base (Super Admin)
     * ATENÇÃO: Isso deleta a base e todas as subcoleções (motoristas, escalas, status, etc.)
     */
    suspend fun deleteBase(baseId: String): Boolean {
        invalidateBasesCache() // ✅ Invalidar cache ao modificar
        return try {
            // ✅ PROTEÇÃO: Não permitir deletar a base do superadmin
            if (baseId == "super_admin_base") {
                println("🚫 BaseRepository: Não é possível deletar a base do superadmin!")
                return false
            }
            
            println("🗑️ BaseRepository: Iniciando exclusão completa da base $baseId")
            
            val baseRef = firestore.collection("bases").document(baseId)
            
            // 1. Deletar todos os motoristas
            println("🗑️ BaseRepository: Deletando motoristas...")
            val motoristasSnapshot = baseRef.collection("motoristas").get().await()
            var motoristasDeletados = 0
            for (doc in motoristasSnapshot.documents) {
                doc.reference.delete().await()
                motoristasDeletados++
            }
            println("✅ BaseRepository: $motoristasDeletados motoristas deletados")
            
            // 2. Deletar todos os status de motoristas
            println("🗑️ BaseRepository: Deletando status de motoristas...")
            val statusSnapshot = baseRef.collection("status_motoristas").get().await()
            var statusDeletados = 0
            for (doc in statusSnapshot.documents) {
                doc.reference.delete().await()
                statusDeletados++
            }
            println("✅ BaseRepository: $statusDeletados status deletados")
            
            // 3. Deletar todas as escalas
            println("🗑️ BaseRepository: Deletando escalas...")
            val escalasSnapshot = baseRef.collection("escalas").get().await()
            var escalasDeletadas = 0
            for (doc in escalasSnapshot.documents) {
                doc.reference.delete().await()
                escalasDeletadas++
            }
            println("✅ BaseRepository: $escalasDeletadas escalas deletadas")
            
            // 4. Deletar configuração (se existir)
            println("🗑️ BaseRepository: Deletando configuração...")
            val configSnapshot = baseRef.collection("configuracao").get().await()
            var configsDeletadas = 0
            for (doc in configSnapshot.documents) {
                doc.reference.delete().await()
                configsDeletadas++
            }
            println("✅ BaseRepository: $configsDeletadas configurações deletadas")
            
            // 5. Deletar disponibilidades relacionadas à base
            println("🗑️ BaseRepository: Deletando disponibilidades...")
            val disponibilidadesSnapshot = firestore.collection("disponibilidades")
                .whereEqualTo("baseId", baseId)
                .get()
                .await()
            var disponibilidadesDeletadas = 0
            for (doc in disponibilidadesSnapshot.documents) {
                doc.reference.delete().await()
                disponibilidadesDeletadas++
            }
            println("✅ BaseRepository: $disponibilidadesDeletadas disponibilidades deletadas")
            
            // 6. Deletar quinzenas relacionadas à base
            println("🗑️ BaseRepository: Deletando quinzenas...")
            val quinzenasSnapshot = firestore.collection("quinzenas")
                .whereEqualTo("baseId", baseId)
                .get()
                .await()
            var quinzenasDeletadas = 0
            for (doc in quinzenasSnapshot.documents) {
                doc.reference.delete().await()
                quinzenasDeletadas++
            }
            println("✅ BaseRepository: $quinzenasDeletadas quinzenas deletadas")
            
            // 7. Finalmente, deletar o documento principal da base
            println("🗑️ BaseRepository: Deletando documento principal da base...")
            baseRef.delete().await()
            
            println("✅ BaseRepository: Base $baseId deletada completamente!")
            println("   - Motoristas: $motoristasDeletados")
            println("   - Status: $statusDeletados")
            println("   - Escalas: $escalasDeletadas")
            println("   - Configurações: $configsDeletadas")
            println("   - Disponibilidades: $disponibilidadesDeletadas")
            println("   - Quinzenas: $quinzenasDeletadas")
            
            true
        } catch (e: Exception) {
            println("❌ BaseRepository: Erro ao deletar base: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
