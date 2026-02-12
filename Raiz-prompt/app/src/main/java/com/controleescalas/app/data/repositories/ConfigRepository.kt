package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.ConfiguracaoBase
import com.controleescalas.app.data.models.GeofenceConfig
import kotlinx.coroutines.tasks.await

/**
 * Repository para operações de Configuração
 */
class ConfigRepository {
    private val firestore = FirebaseManager.firestore
    
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
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("configuracao")
                .document("principal")
                .set(config)
                .await()
            
            true
        } catch (e: Exception) {
            false
        }
    }
}
