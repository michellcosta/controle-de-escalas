package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.ModoAtivacao
import com.controleescalas.app.data.models.SistemaConfig
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Repository para operações de configuração do sistema
 */
class SistemaRepository {
    private val firestore = FirebaseManager.firestore
    private val sistemaRef = firestore.collection("sistema").document("config")
    
    // ✅ Cache de configuração (10 minutos - configuração muda raramente)
    private var cachedConfig: SistemaConfig? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutos

    /**
     * Buscar configuração atual do sistema (com cache)
     */
    suspend fun getConfiguracao(forceRefresh: Boolean = false): SistemaConfig {
        // Verificar cache
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedConfig != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            println("✅ SistemaRepository: Retornando configuração do cache")
            return cachedConfig!!
        }
        return try {
            println("🔄 SistemaRepository: Buscando configuração do banco...")
            val doc = sistemaRef.get().await()
            val config = if (doc.exists()) {
                SistemaConfig(
                    monetizacaoAtiva = doc.getBoolean("monetizacaoAtiva") ?: false,
                    planosHabilitados = doc.getBoolean("planosHabilitados") ?: false,
                    modoAtivacao = doc.getString("modoAtivacao")?.let {
                        try {
                            ModoAtivacao.valueOf(it)
                        } catch (e: Exception) {
                            null
                        }
                    },
                    dataAtivacaoAutomatica = extrairTimestamp(doc, "dataAtivacaoAutomatica"),
                    dataAtivacaoManual = extrairTimestamp(doc, "dataAtivacaoManual"),
                    ativadoPor = doc.getString("ativadoPor"),
                    ultimaModificacao = doc.getLong("ultimaModificacao") ?: System.currentTimeMillis(),
                    // ✅ NOVO: Campos adicionais
                    periodoTrialDias = doc.getLong("periodoTrialDias")?.toInt() ?: 30,
                    limiteMotoristasGratuito = doc.getLong("limiteMotoristasGratuito")?.toInt() ?: 5,
                    precoPremiumMensal = doc.getDouble("precoPremiumMensal") ?: 249.90,
                    descontoMultiplasBases = doc.getDouble("descontoMultiplasBases") ?: 0.10,
                    minimoBasesParaDesconto = doc.getLong("minimoBasesParaDesconto")?.toInt() ?: 5
                )
            } else {
                // Retornar configuração padrão (desativada)
                SistemaConfig()
            }
            
            // Atualizar cache
            cachedConfig = config
            cacheTimestamp = now
            
            println("✅ SistemaRepository: Configuração carregada e cache atualizado")
            config
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao buscar configuração: ${e.message}")
            // Retornar cache antigo se houver erro
            cachedConfig ?: SistemaConfig()
        }
    }
    
    /**
     * Habilitar ou desabilitar exibição da tela de planos para clientes.
     * Ative quando configurar produtos no Play Console.
     */
    suspend fun setPlanosHabilitados(habilitado: Boolean): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            sistemaRef.set(
                mapOf(
                    "planosHabilitados" to habilitado,
                    "ultimaModificacao" to agora
                ),
                SetOptions.merge()
            ).await()
            invalidateCache()
            println("✅ SistemaRepository: Planos habilitados = $habilitado")
            true
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao setar planosHabilitados: ${e.message}")
            false
        }
    }

    /**
     * Registra o UID do superadmin em sistema/config (campo superadminUids).
     * O backend usa essa lista para permitir solicitar localização no Assistente.
     */
    suspend fun registerSuperAdminUid(uid: String): Result<Unit> {
        if (uid.isBlank()) return Result.failure(IllegalArgumentException("UID vazio"))
        return try {
            sistemaRef.set(
                mapOf("superadminUids" to FieldValue.arrayUnion(uid)),
                SetOptions.merge()
            ).await()
            println("✅ SistemaRepository: UID $uid registrado em sistema/config (superadminUids)")
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao registrar superadmin UID: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Invalidar cache de configuração
     */
    fun invalidateCache() {
        cachedConfig = null
        cacheTimestamp = 0
        println("🔄 SistemaRepository: Cache invalidado")
    }
    
    /**
     * Extrair timestamp do documento (pode ser Timestamp do Firestore ou Long)
     */
    private fun extrairTimestamp(doc: DocumentSnapshot, campo: String): Long? {
        return try {
            val valor = doc.get(campo)
            when (valor) {
                is Timestamp -> valor.toDate().time
                is Long -> valor
                is Number -> valor.toLong()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verificar se monetização está ativa (com verificação de data agendada)
     */
    suspend fun verificarMonetizacaoAtiva(): Boolean {
        val config = getConfiguracao()
        
        // Se já está ativo, retorna true
        if (config.monetizacaoAtiva) {
            return true
        }
        
        // Se tem data agendada, verifica se já passou
        if (config.dataAtivacaoAutomatica != null) {
            val agora = System.currentTimeMillis()
            val dataAgendada = config.dataAtivacaoAutomatica
            
            if (agora >= dataAgendada) {
                // Data passou, mas ainda não foi ativado pela Cloud Function
                // Ativar agora (fallback)
                println("⚠️ SistemaRepository: Data agendada passou, ativando monetização automaticamente")
                ativarMonetizacao(ModoAtivacao.AUTOMATICA, null)
                return true
            }
        }
        
        return false
    }

    /**
     * Ativar monetização manualmente
     */
    suspend fun ativarMonetizacaoManual(superAdminId: String): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            sistemaRef.set(
                mapOf(
                    "monetizacaoAtiva" to true,
                    "modoAtivacao" to ModoAtivacao.MANUAL.name,
                    "dataAtivacaoManual" to agora,
                    "dataAtivacaoAutomatica" to null, // Cancelar agendamento se existir
                    "ativadoPor" to superAdminId,
                    "ultimaModificacao" to agora
                )
            ).await()
            invalidateCache() // ✅ Invalidar cache ao modificar
            println("✅ SistemaRepository: Monetização ativada manualmente por $superAdminId")
            true
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao ativar monetização: ${e.message}")
            false
        }
    }

    /**
     * Ativar monetização (usado internamente e pela Cloud Function)
     */
    suspend fun ativarMonetizacao(modo: ModoAtivacao, superAdminId: String?): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            val updateData = mutableMapOf<String, Any>(
                "monetizacaoAtiva" to true,
                "modoAtivacao" to modo.name,
                "ultimaModificacao" to agora
            )
            
            if (modo == ModoAtivacao.MANUAL && superAdminId != null) {
                updateData["dataAtivacaoManual"] = agora
                updateData["ativadoPor"] = superAdminId
            } else if (modo == ModoAtivacao.AUTOMATICA) {
                // Manter dataAtivacaoAutomatica se já existir
                val configAtual = getConfiguracao()
                if (configAtual.dataAtivacaoAutomatica != null) {
                    updateData["dataAtivacaoAutomatica"] = configAtual.dataAtivacaoAutomatica
                }
            }
            
            sistemaRef.update(updateData).await()
            invalidateCache() // ✅ Invalidar cache ao modificar
            println("✅ SistemaRepository: Monetização ativada (modo: ${modo.name})")
            true
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao ativar monetização: ${e.message}")
            false
        }
    }

    /**
     * Desativar monetização
     */
    suspend fun desativarMonetizacao(superAdminId: String): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            sistemaRef.update(
                mapOf(
                    "monetizacaoAtiva" to false,
                    "modoAtivacao" to null,
                    "dataAtivacaoAutomatica" to null,
                    "dataAtivacaoManual" to null,
                    "ativadoPor" to superAdminId,
                    "ultimaModificacao" to agora
                )
            ).await()
            invalidateCache() // ✅ Invalidar cache ao modificar
            println("✅ SistemaRepository: Monetização desativada por $superAdminId")
            true
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao desativar monetização: ${e.message}")
            false
        }
    }

    /**
     * Agendar ativação automática
     */
    suspend fun agendarAtivacaoAutomatica(dataAgendada: Long, superAdminId: String): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            sistemaRef.set(
                mapOf(
                    "monetizacaoAtiva" to false, // Ainda não está ativo
                    "modoAtivacao" to ModoAtivacao.AUTOMATICA.name,
                    "dataAtivacaoAutomatica" to dataAgendada,
                    "dataAtivacaoManual" to null,
                    "ativadoPor" to superAdminId,
                    "ultimaModificacao" to agora
                )
            ).await()
            invalidateCache() // ✅ Invalidar cache ao modificar
            println("✅ SistemaRepository: Ativação automática agendada para ${java.util.Date(dataAgendada)}")
            true
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao agendar ativação: ${e.message}")
            false
        }
    }

    /**
     * Cancelar agendamento
     */
    suspend fun cancelarAgendamento(superAdminId: String): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            sistemaRef.update(
                mapOf(
                    "modoAtivacao" to null,
                    "dataAtivacaoAutomatica" to null,
                    "ativadoPor" to superAdminId,
                    "ultimaModificacao" to agora
                )
            ).await()
            invalidateCache() // ✅ Invalidar cache ao modificar
            println("✅ SistemaRepository: Agendamento cancelado por $superAdminId")
            true
        } catch (e: Exception) {
            println("❌ SistemaRepository: Erro ao cancelar agendamento: ${e.message}")
            false
        }
    }
}

