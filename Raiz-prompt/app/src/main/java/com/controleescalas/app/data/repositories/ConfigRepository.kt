package com.controleescalas.app.data.repositories

import android.util.Log
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.ConfiguracaoBase
import com.controleescalas.app.data.models.GeofenceConfig
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Repository para operações de Configuração
 */
class ConfigRepository {
    private val firestore = FirebaseManager.firestore
    
    /**
     * Observar mudanças na configuração da base em tempo real.
     * Útil para quando o admin altera o raio do galpão - o motorista recebe a nova config imediatamente.
     */
    fun observeConfiguracaoBase(
        baseId: String,
        onUpdate: (ConfiguracaoBase?) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return firestore
            .collection("bases")
            .document(baseId)
            .collection("configuracao")
            .document("principal")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(Exception(error.message))
                    return@addSnapshotListener
                }
                val config = snapshot?.toObject(ConfiguracaoBase::class.java)
                onUpdate(config)
            }
    }
    
    /**
     * Buscar configuração de uma base
     */
    suspend fun getConfiguracaoBase(baseId: String): ConfiguracaoBase? {
        return try {
            val doc = firestore
                .collection("bases")
                .document(baseId)
                .collection("configuracao")
                .document("principal")
                .get()
                .await()
            
            doc.toObject(ConfiguracaoBase::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Salvar configuração de geofence
     */
    suspend fun saveGeofenceConfig(
        baseId: String,
        tipo: String, // "galpao" ou "estacionamento"
        lat: Double,
        lng: Double,
        raio: Int
    ): Boolean {
        return try {
            // Buscar configuração atual para preservar os outros campos
            val configAtual = getConfiguracaoBase(baseId)
            
            // Criar nova configuração preservando os valores existentes
            val novoGeofence = GeofenceConfig(lat = lat, lng = lng, raio = raio, ativo = true)
            val config = ConfiguracaoBase(
                baseId = baseId,
                galpao = if (tipo == "galpao") {
                    novoGeofence
                } else {
                    // Preservar o galpão existente ou criar vazio se não existir
                    configAtual?.galpao ?: GeofenceConfig()
                },
                estacionamento = if (tipo == "estacionamento") {
                    novoGeofence
                } else {
                    // Preservar o estacionamento existente ou criar vazio se não existir
                    configAtual?.estacionamento ?: GeofenceConfig()
                },
                // Preservar os turnos cadastrados (não resetar para o padrão)
                turnos = configAtual?.turnos ?: ConfiguracaoBase().turnos
            )
            
            val path = "bases/$baseId/configuracao/principal"
            Log.d("ConfigRepository", "💾 Salvando $tipo em $path - lat=$lat, lng=$lng, raio=$raio")
            firestore
                .collection("bases")
                .document(baseId)
                .collection("configuracao")
                .document("principal")
                .set(config)
                .await()
            Log.d("ConfigRepository", "✅ Config salva com sucesso em $path")
            true
        } catch (e: Exception) {
            Log.e("ConfigRepository", "❌ Erro ao salvar config: ${e.message}", e)
            false
        }
    }

    /**
     * Turnos padrão quando a base ainda não cadastrou a própria lista.
     */
    val turnosPadrao: List<String> = listOf("AM0", "AM", "PM", "SD", "SN")

    /**
     * Buscar os turnos cadastrados da base (one-shot).
     * Base sem cadastro ou erro de leitura -> lista padrão.
     */
    suspend fun getTurnosBase(baseId: String): List<String> {
        return try {
            val config = getConfiguracaoBase(baseId)
            val turnos = config?.turnos?.filter { it.isNotBlank() } ?: emptyList()
            if (turnos.isEmpty()) turnosPadrao else turnos
        } catch (e: Exception) {
            Log.e("ConfigRepository", "❌ Erro ao ler turnos da base: ${e.message}", e)
            turnosPadrao
        }
    }

    /**
     * Observar os turnos da base em tempo real (realtime para Operação/Disponibilidade).
     */
    fun observeTurnosBase(
        baseId: String,
        onUpdate: (List<String>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return observeConfiguracaoBase(baseId, onUpdate = { config ->
            val turnos = config?.turnos?.filter { it.isNotBlank() } ?: emptyList()
            onUpdate(if (turnos.isEmpty()) turnosPadrao else turnos)
        }, onError = onError)
    }

    /**
     * Salvar a lista de turnos da base (admin edita).
     * Preserva geofence; normaliza (trim/uppercase), remove duplicatas e vazios.
     */
    suspend fun salvarTurnosBase(baseId: String, turnos: List<String>): Boolean {
        return try {
            val limpos = turnos
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
            if (limpos.isEmpty()) {
                Log.e("ConfigRepository", "⚠️ Lista de turnos vazia — ignorado")
                return false
            }

            val configAtual = getConfiguracaoBase(baseId)
            val config = ConfiguracaoBase(
                baseId = baseId,
                galpao = configAtual?.galpao ?: GeofenceConfig(),
                estacionamento = configAtual?.estacionamento ?: GeofenceConfig(),
                turnos = limpos
            )

            val path = "bases/$baseId/configuracao/principal"
            firestore
                .collection("bases")
                .document(baseId)
                .collection("configuracao")
                .document("principal")
                .set(config)
                .await()
            Log.d("ConfigRepository", "✅ Turnos salvos em $path: $limpos")
            true
        } catch (e: Exception) {
            Log.e("ConfigRepository", "❌ Erro ao salvar turnos: ${e.message}", e)
            false
        }
    }
}
