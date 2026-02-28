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
                }
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
}
