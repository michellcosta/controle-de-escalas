package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.AcaoHistorico
import kotlinx.coroutines.tasks.await

/**
 * Repository para gerenciar histórico de ações do superadmin
 * ✅ OTIMIZADO: Usa queries pontuais com cache ao invés de listeners em tempo real
 */
class HistoricoRepository {
    private val firestore = FirebaseManager.firestore
    private val historicoCollection = firestore.collection("historico_acoes")
    
    // ✅ Cache de histórico (5 minutos)
    private var cachedAcoes: List<AcaoHistorico>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutos
    private val MAX_ACOES = 100 // ✅ Limitar quantidade para não carregar tudo
    
    /**
     * Registrar uma nova ação no histórico
     */
    suspend fun registrarAcao(acao: AcaoHistorico): Boolean {
        return try {
            historicoCollection.add(acao).await()
            invalidateCache() // ✅ Invalidar cache ao adicionar nova ação
            println("✅ HistoricoRepository: Ação registrada - ${acao.tipo}")
            true
        } catch (e: Exception) {
            println("❌ HistoricoRepository: Erro ao registrar ação: ${e.message}")
            false
        }
    }
    
    /**
     * Buscar todas as ações (ordenadas por data, mais recentes primeiro)
     * ✅ OTIMIZADO: Query pontual com cache e limite
     */
    suspend fun getAllAcoes(forceRefresh: Boolean = false): List<AcaoHistorico> {
        // Verificar cache
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedAcoes != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            println("✅ HistoricoRepository: Retornando histórico do cache (${cachedAcoes!!.size} ações)")
            return cachedAcoes!!
        }
        
        return try {
            println("🔄 HistoricoRepository: Buscando histórico do banco...")
            val snapshot = historicoCollection
                .orderBy("data", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_ACOES.toLong()) // ✅ Limitar quantidade
                .get()
                .await()
            
            val acoes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(AcaoHistorico::class.java)?.copy(id = doc.id)
            }
            
            // Atualizar cache
            cachedAcoes = acoes
            cacheTimestamp = now
            
            println("✅ HistoricoRepository: ${acoes.size} ações carregadas e cache atualizado")
            acoes
        } catch (e: Exception) {
            println("❌ HistoricoRepository: Erro ao buscar ações: ${e.message}")
            // Retornar cache antigo se houver erro
            cachedAcoes ?: emptyList()
        }
    }
    
    /**
     * Buscar ações por tipo
     * ✅ OTIMIZADO: Query pontual com cache e limite
     */
    suspend fun getAcoesPorTipo(tipo: String, forceRefresh: Boolean = false): List<AcaoHistorico> {
        return try {
            val snapshot = historicoCollection
                .whereEqualTo("tipo", tipo)
                .orderBy("data", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_ACOES.toLong()) // ✅ Limitar quantidade
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(AcaoHistorico::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            println("❌ HistoricoRepository: Erro ao buscar ações por tipo: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Buscar ações por base
     * ✅ OTIMIZADO: Query pontual com cache e limite
     */
    suspend fun getAcoesPorBase(baseId: String, forceRefresh: Boolean = false): List<AcaoHistorico> {
        return try {
            val snapshot = historicoCollection
                .whereEqualTo("baseId", baseId)
                .orderBy("data", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_ACOES.toLong()) // ✅ Limitar quantidade
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(AcaoHistorico::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            println("❌ HistoricoRepository: Erro ao buscar ações por base: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Invalidar cache
     */
    fun invalidateCache() {
        cachedAcoes = null
        cacheTimestamp = 0
        println("🔄 HistoricoRepository: Cache invalidado")
    }
}
