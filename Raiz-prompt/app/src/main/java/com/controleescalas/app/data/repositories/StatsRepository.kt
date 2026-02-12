package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.DashboardStats
import com.controleescalas.app.data.models.Base
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class StatsRepository {
    private val firestore = FirebaseManager.firestore
    
    // ✅ Cache de estatísticas (5 minutos)
    private var cachedStats: DashboardStats? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutos
    
    /**
     * Buscar estatísticas do dashboard (com cache)
     */
    suspend fun getDashboardStats(forceRefresh: Boolean = false): DashboardStats {
        // Verificar cache
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedStats != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            println("✅ StatsRepository: Retornando estatísticas do cache")
            return cachedStats!!
        }
        
        return try {
            println("🔄 StatsRepository: Buscando estatísticas do banco...")
            
            // 1. Buscar todas as bases (1 query)
            val basesSnapshot = firestore.collection("bases").get().await()
            val bases = basesSnapshot.documents.mapNotNull { doc ->
                val base = doc.toObject(Base::class.java)
                base?.copy(id = doc.id)
            }.filter { it.id != "super_admin_base" }
            
            val transportadorasAtivas = bases.count { it.statusAprovacao == "ativa" }
            val transportadorasPendentes = bases.count { it.statusAprovacao == "pendente" }
            val transportadorasRejeitadas = bases.count { it.statusAprovacao == "rejeitada" }
            
            // 2. ✅ OTIMIZADO: Usar collectionGroup para buscar TODOS os motoristas de uma vez
            // Isso reduz de N*2 queries para apenas 2 queries (motorista + auxiliar)
            var totalMotoristas = 0
            try {
                // Buscar todos os motoristas ativos de uma vez
                val motoristasSnapshot = firestore
                    .collectionGroup("motoristas")
                    .whereEqualTo("ativo", true)
                    .whereIn("papel", listOf("motorista", "auxiliar"))
                    .get()
                    .await()
                
                // Filtrar apenas das bases válidas (não super_admin_base)
                totalMotoristas = motoristasSnapshot.documents.count { doc ->
                    val path = doc.reference.path
                    val baseId = path.split("/")[1] // bases/{baseId}/motoristas/{motoristaId}
                    baseId != "super_admin_base" && bases.any { it.id == baseId }
                }
                
                println("✅ StatsRepository: Motoristas encontrados via collectionGroup: $totalMotoristas")
            } catch (e: Exception) {
                println("⚠️ StatsRepository: CollectionGroup não disponível, usando método alternativo")
                // Fallback: buscar apenas das bases ativas (reduz queries)
                val basesAtivas = bases.filter { it.statusAprovacao == "ativa" }
                for (base in basesAtivas) {
                    val motoristasSnapshot = firestore
                        .collection("bases")
                        .document(base.id)
                        .collection("motoristas")
                        .whereEqualTo("ativo", true)
                        .whereIn("papel", listOf("motorista", "auxiliar"))
                        .get()
                        .await()
                    totalMotoristas += motoristasSnapshot.size()
                }
            }
            
            // 3. ✅ OTIMIZADO: Buscar escalas apenas das bases ativas
            val hoje = Calendar.getInstance()
            val dataHoje = String.format("%04d-%02d-%02d", hoje.get(Calendar.YEAR), hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.DAY_OF_MONTH))
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val inicioSemana = String.format("%04d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
            
            var escalasHoje = 0
            var escalasEstaSemana = 0
            
            // ✅ OTIMIZADO: Buscar apenas das bases ativas (reduz queries)
            val basesAtivas = bases.filter { it.statusAprovacao == "ativa" }
            
            // Usar collectionGroup para escalas se possível
            try {
                val escalasHojeSnapshot = firestore
                    .collectionGroup("escalas")
                    .whereEqualTo("data", dataHoje)
                    .get()
                    .await()
                
                escalasHoje = escalasHojeSnapshot.documents.count { doc ->
                    val path = doc.reference.path
                    val baseId = path.split("/")[1]
                    basesAtivas.any { it.id == baseId }
                }
                
                val escalasSemanaSnapshot = firestore
                    .collectionGroup("escalas")
                    .whereGreaterThanOrEqualTo("data", inicioSemana)
                    .get()
                    .await()
                
                escalasEstaSemana = escalasSemanaSnapshot.documents.count { doc ->
                    val path = doc.reference.path
                    val baseId = path.split("/")[1]
                    basesAtivas.any { it.id == baseId }
                }
                
                println("✅ StatsRepository: Escalas encontradas via collectionGroup")
            } catch (e: Exception) {
                println("⚠️ StatsRepository: CollectionGroup para escalas não disponível, usando método alternativo")
                // Fallback: buscar por base (mas apenas bases ativas)
                for (base in basesAtivas) {
                    val escalasSnapshot = firestore
                        .collection("bases")
                        .document(base.id)
                        .collection("escalas")
                        .whereEqualTo("data", dataHoje)
                        .get()
                        .await()
                    escalasHoje += escalasSnapshot.size()
                    
                    val escalasSemanaSnapshot = firestore
                        .collection("bases")
                        .document(base.id)
                        .collection("escalas")
                        .whereGreaterThanOrEqualTo("data", inicioSemana)
                        .get()
                        .await()
                    escalasEstaSemana += escalasSemanaSnapshot.size()
                }
            }
            
            // 4. Calcular receita (sem queries adicionais, usa dados já carregados)
            val sistemaConfig = SistemaRepository().getConfiguracao()
            var receitaMensal = 0.0
            var basesPremium = 0
            var basesGratuitas = 0
            var basesEmTrial = 0
            
            if (sistemaConfig.monetizacaoAtiva) {
                for (base in bases) {
                    if (base.statusAprovacao == "ativa") {
                        val plano = base.plano ?: "gratuito"
                        if (plano == "premium") {
                            basesPremium++
                            receitaMensal += sistemaConfig.precoPremiumMensal
                        } else if (base.estaEmTrial()) {
                            basesEmTrial++
                        } else {
                            basesGratuitas++
                        }
                    }
                }
            }
            
            val stats = DashboardStats(
                totalTransportadoras = bases.size,
                transportadorasAtivas = transportadorasAtivas,
                transportadorasPendentes = transportadorasPendentes,
                transportadorasRejeitadas = transportadorasRejeitadas,
                totalMotoristas = totalMotoristas,
                escalasHoje = escalasHoje,
                escalasEstaSemana = escalasEstaSemana,
                receitaMensal = receitaMensal,
                basesPremium = basesPremium,
                basesGratuitas = basesGratuitas,
                basesEmTrial = basesEmTrial
            )
            
            // Atualizar cache
            cachedStats = stats
            cacheTimestamp = now
            
            println("✅ StatsRepository: Estatísticas calculadas e cache atualizado")
            stats
        } catch (e: Exception) {
            println("❌ StatsRepository: Erro ao buscar estatísticas: ${e.message}")
            e.printStackTrace()
            // Retornar cache antigo se houver erro
            cachedStats ?: DashboardStats()
        }
    }
    
    /**
     * Invalidar cache (chamar quando dados importantes mudarem)
     */
    fun invalidateCache() {
        cachedStats = null
        cacheTimestamp = 0
        println("🔄 StatsRepository: Cache invalidado")
    }
}
