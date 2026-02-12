package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Devolucao
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository para operações de Devolução
 */
class DevolucaoRepository {
    private val firestore = FirebaseManager.firestore
    
    /**
     * Garante que o usuário esteja autenticado (anônimo ou não)
     */
    private suspend fun ensureAuth() {
        if (FirebaseManager.auth.currentUser == null) {
            println("👤 DevolucaoRepository: Usuário não autenticado. Tentando login anônimo...")
            try {
                FirebaseManager.auth.signInAnonymously().await()
                println("✅ DevolucaoRepository: Login anônimo realizado com sucesso: ${FirebaseManager.auth.currentUser?.uid}")
            } catch (e: Exception) {
                println("❌ DevolucaoRepository: Falha no login anônimo: ${e.message}")
            }
        }
    }
    
    /**
     * Verificar se um ID de pacote já foi registrado (globalmente)
     * Verifica tanto no formato antigo (idPacote) quanto no novo (idsPacotes)
     */
    suspend fun verificarIdPacoteExiste(baseId: String, idPacote: String): Boolean {
        return try {
            ensureAuth()
            
            // Buscar todas as devoluções e verificar se o ID está em alguma delas
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .get()
                .await()
            
            // Verificar em cada documento
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                
                // Verificar formato novo (idsPacotes)
                val idsPacotes = data["idsPacotes"] as? List<*>
                if (idsPacotes != null && idsPacotes.contains(idPacote)) {
                    return true
                }
                
                // Verificar formato antigo (idPacote) para compatibilidade
                val idPacoteAntigo = data["idPacote"] as? String
                if (idPacoteAntigo == idPacote) {
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao verificar ID do pacote: ${e.message}")
            false
        }
    }
    
    /**
     * Registrar uma nova devolução (usa o novo formato com lista de IDs)
     */
    suspend fun registrarDevolucao(
        baseId: String,
        idPacote: String,
        motoristaId: String,
        motoristaNome: String,
        data: String,
        hora: String
    ): Boolean {
        // Usar o método de lote com um único ID
        return try {
            val (sucessos, erros) = registrarDevolucoesEmLote(
                baseId = baseId,
                idPacotes = listOf(idPacote),
                motoristaId = motoristaId,
                motoristaNome = motoristaNome,
                data = data,
                hora = hora
            )
            sucessos > 0 && erros.isEmpty()
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao registrar devolução: ${e.message}")
            throw e
        }
    }
    
    /**
     * Registrar múltiplas devoluções em lote (cria um único documento com todos os IDs)
     */
    suspend fun registrarDevolucoesEmLote(
        baseId: String,
        idPacotes: List<String>,
        motoristaId: String,
        motoristaNome: String,
        data: String,
        hora: String
    ): Pair<Int, List<String>> {
        return try {
            ensureAuth()
            
            val erros = mutableListOf<String>()
            val idsValidos = mutableListOf<String>()
            
            // Verificar quais IDs já existem e quais são válidos
            for (idPacote in idPacotes) {
                val idExiste = verificarIdPacoteExiste(baseId, idPacote)
                if (idExiste) {
                    erros.add("ID $idPacote já registrado")
                } else {
                    idsValidos.add(idPacote)
                }
            }
            
            if (idsValidos.isEmpty()) {
                println("⚠️ DevolucaoRepository: Nenhum ID válido para registrar")
                return Pair(0, erros)
            }
            
            // Criar timestamp
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateTimeString = "$data $hora"
            val date = dateFormat.parse(dateTimeString)
            val timestamp = date?.time ?: System.currentTimeMillis()
            
            // Criar um único documento com todos os IDs
            val devolucaoData = hashMapOf(
                "idsPacotes" to idsValidos,
                "quantidade" to idsValidos.size,
                "motoristaId" to motoristaId,
                "motoristaNome" to motoristaNome,
                "data" to data,
                "hora" to hora,
                "timestamp" to timestamp,
                "baseId" to baseId
            )
            
            // Usar timestamp + primeiro ID para criar um ID único para a devolução
            val devolucaoId = "devolucao_${timestamp}_${idsValidos.first()}"
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .document(devolucaoId)
                .set(devolucaoData)
                .await()
            
            println("✅ DevolucaoRepository: Devolução registrada com sucesso - ${idsValidos.size} pacote(s): ${idsValidos.joinToString(", ")}")
            Pair(idsValidos.size, erros)
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao processar lote: ${e.message}")
            throw e
        }
    }
    
    /**
     * Buscar devoluções de um motorista específico
     */
    suspend fun buscarDevolucoesMotorista(
        baseId: String,
        motoristaId: String
    ): List<Map<String, Any>> {
        return try {
            ensureAuth()
            
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .whereEqualTo("motoristaId", motoristaId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                data
            }
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao buscar devoluções do motorista: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Buscar todas as devoluções (para admin)
     */
    suspend fun buscarTodasDevolucoes(baseId: String): List<Map<String, Any>> {
        return try {
            ensureAuth()
            
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                data
            }
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao buscar todas as devoluções: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Buscar devoluções de um motorista com filtros (para admin)
     */
    suspend fun buscarDevolucoesMotoristaComFiltros(
        baseId: String,
        motoristaId: String,
        dataInicio: Long? = null,
        dataFim: Long? = null,
        idPacoteFiltro: String? = null
    ): List<Map<String, Any>> {
        return try {
            ensureAuth()
            
            var query: Query = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .whereEqualTo("motoristaId", motoristaId)
            
            // Aplicar filtro de data se fornecido
            if (dataInicio != null) {
                query = query.whereGreaterThanOrEqualTo("timestamp", dataInicio)
            }
            if (dataFim != null) {
                query = query.whereLessThanOrEqualTo("timestamp", dataFim)
            }
            
            val snapshot = query
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            var resultados = snapshot.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                data
            }
            
            // Aplicar filtro de ID do pacote se fornecido (filtro em memória)
            if (idPacoteFiltro != null && idPacoteFiltro.isNotBlank()) {
                resultados = resultados.filter { data ->
                    // Verificar formato novo (idsPacotes)
                    val idsPacotes = data["idsPacotes"] as? List<*>
                    if (idsPacotes != null) {
                        val idsPacotesStr = idsPacotes.mapNotNull { it as? String }
                        idsPacotesStr.any { it.contains(idPacoteFiltro, ignoreCase = true) }
                    } else {
                        // Verificar formato antigo (idPacote) para compatibilidade
                        (data["idPacote"] as? String)?.contains(idPacoteFiltro, ignoreCase = true) == true
                    }
                }
            }
            
            resultados
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao buscar devoluções com filtros: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Buscar motoristas que têm devoluções (para admin)
     */
    suspend fun buscarMotoristasComDevolucoes(baseId: String, nomeFiltro: String? = null): List<Pair<String, String>> {
        return try {
            ensureAuth()
            
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .get()
                .await()
            
            // Agrupar por motorista
            val motoristasMap = mutableMapOf<String, String>()
            snapshot.documents.forEach { doc ->
                val motoristaId = doc.getString("motoristaId") ?: return@forEach
                val motoristaNome = doc.getString("motoristaNome") ?: return@forEach
                motoristasMap[motoristaId] = motoristaNome
            }
            
            var motoristas = motoristasMap.map { (id, nome) -> id to nome }
            
            // Aplicar filtro de nome do motorista ou ID do pacote se fornecido
            if (nomeFiltro != null && nomeFiltro.isNotBlank()) {
                // Se o filtro corresponder a um idPacote, buscar motoristas que têm devoluções com esse idPacote
                val motoristasComIdPacote = mutableSetOf<String>()
                snapshot.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val motoristaId = data["motoristaId"] as? String ?: ""
                    
                    // Verificar formato novo (idsPacotes)
                    val idsPacotes = data["idsPacotes"] as? List<*>
                    if (idsPacotes != null) {
                        val idsPacotesStr = idsPacotes.mapNotNull { it as? String }
                        if (idsPacotesStr.any { it.contains(nomeFiltro, ignoreCase = true) }) {
                            motoristasComIdPacote.add(motoristaId)
                        }
                    } else {
                        // Verificar formato antigo (idPacote) para compatibilidade
                        val idPacote = data["idPacote"] as? String ?: ""
                        if (idPacote.contains(nomeFiltro, ignoreCase = true)) {
                            motoristasComIdPacote.add(motoristaId)
                        }
                    }
                }
                
                motoristas = motoristas.filter { (id, nome) ->
                    nome.contains(nomeFiltro, ignoreCase = true) || 
                    motoristasComIdPacote.contains(id)
                }
            }
            
            motoristas
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao buscar motoristas com devoluções: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Contar devoluções de um motorista
     */
    suspend fun contarDevolucoesMotorista(baseId: String, motoristaId: String): Int {
        return try {
            ensureAuth()
            
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .whereEqualTo("motoristaId", motoristaId)
                .get()
                .await()
            
            snapshot.documents.size
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao contar devoluções: ${e.message}")
            0
        }
    }
    
    /**
     * Contar devoluções por dia de um motorista
     */
    suspend fun contarDevolucoesPorDia(baseId: String, motoristaId: String): Map<String, Int> {
        return try {
            ensureAuth()
            
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .whereEqualTo("motoristaId", motoristaId)
                .get()
                .await()
            
            val contagemPorDia = mutableMapOf<String, Int>()
            snapshot.documents.forEach { doc ->
                val data = doc.getString("data") ?: return@forEach
                contagemPorDia[data] = (contagemPorDia[data] ?: 0) + 1
            }
            
            contagemPorDia
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao contar devoluções por dia: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Excluir uma devolução
     */
    suspend fun excluirDevolucao(
        baseId: String,
        devolucaoId: String,
        motoristaId: String // Para validar que apenas o próprio motorista pode excluir
    ): Boolean {
        return try {
            ensureAuth()
            
            // Verificar se a devolução pertence ao motorista
            val doc = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .document(devolucaoId)
                .get()
                .await()
            
            if (!doc.exists()) {
                throw Exception("Devolução não encontrada.")
            }
            
            val devolucaoMotoristaId = doc.getString("motoristaId")
            if (devolucaoMotoristaId != motoristaId) {
                throw Exception("Você não tem permissão para excluir esta devolução.")
            }
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .document(devolucaoId)
                .delete()
                .await()
            
            println("✅ DevolucaoRepository: Devolução excluída com sucesso: $devolucaoId")
            true
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao excluir devolução: ${e.message}")
            throw e
        }
    }
    
    /**
     * Buscar ID da devolução pelo ID do pacote e motorista (para exclusão)
     */
    suspend fun buscarDevolucaoIdPorPacote(
        baseId: String,
        idPacote: String,
        motoristaId: String
    ): String? {
        return try {
            ensureAuth()
            
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("devolucoes")
                .whereEqualTo("idPacote", idPacote)
                .whereEqualTo("motoristaId", motoristaId)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.id
        } catch (e: Exception) {
            println("❌ DevolucaoRepository: Erro ao buscar ID da devolução: ${e.message}")
            null
        }
    }
}

