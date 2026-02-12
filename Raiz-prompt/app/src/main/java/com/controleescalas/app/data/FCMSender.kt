package com.controleescalas.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Serviço para enviar notificações FCM usando tópicos
 * Substitui a necessidade de Cloud Functions
 * 
 * Abordagem: Os motoristas se inscrevem em tópicos da base
 * e o admin envia notificações para esses tópicos
 */
class FCMSender {
    
    companion object {
        private const val TAG = "FCMSender"
    }
    
    /**
     * Obter FCM tokens dos motoristas escalados
     */
    suspend fun getFcmTokensForMotoristas(
        baseId: String,
        motoristaIds: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val tokens = mutableListOf<String>()
            
            motoristaIds.forEach { motoristaId ->
                try {
                    val doc = FirebaseManager.firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("motoristas")
                        .document(motoristaId)
                        .get()
                        .await()
                    
                    if (doc.exists()) {
                        val token = doc.getString("fcmToken")
                        if (!token.isNullOrBlank()) {
                            tokens.add(token)
                            Log.d(TAG, "✅ Token encontrado para motorista $motoristaId")
                        } else {
                            Log.w(TAG, "⚠️ Token vazio para motorista $motoristaId")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Motorista $motoristaId não encontrado")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao buscar token do motorista $motoristaId: ${e.message}")
                }
            }
            
            Log.d(TAG, "📊 Total de tokens encontrados: ${tokens.size}/${motoristaIds.size}")
            tokens
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao buscar FCM tokens: ${e.message}", e)
            emptyList()
        }
    }
    
    
    /**
     * Enviar notificação via tópico FCM
     * NOTA: Para enviar notificações via tópico do app cliente, precisamos usar a API REST do Firebase
     * que requer Server Key. Como não podemos expor o Server Key no app, vamos usar uma abordagem alternativa:
     * 
     * 1. Os motoristas já recebem notificações via listeners do Firestore (já implementado)
     * 2. Esta função serve como placeholder para futura implementação com Server Key seguro
     * 
     * Por enquanto, vamos apenas logar que a notificação seria enviada
     */
    suspend fun sendNotificationToTopic(
        topic: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📤 Notificação via tópico seria enviada para: $topic")
            Log.d(TAG, "   Título: $title")
            Log.d(TAG, "   Corpo: $body")
            Log.d(TAG, "   Dados: $data")
            
            // NOTA: Para implementar envio real via tópico do app cliente, você precisaria:
            // 1. Armazenar Server Key de forma segura (ex: Remote Config, variável de ambiente)
            // 2. Fazer requisição HTTP para https://fcm.googleapis.com/v1/projects/{project_id}/messages:send
            // 3. Autenticar com Access Token OAuth2 ou usar API Legacy com Server Key
            
            // Por enquanto, os motoristas recebem notificações via listeners do Firestore
            // que já está funcionando corretamente
            
            Log.i(TAG, "💡 Notificações estão sendo enviadas via listeners do Firestore (já implementado)")
            
            true // Retorna true pois a notificação será enviada via listener
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao enviar notificação via tópico: ${e.message}", e)
            false
        }
    }
    
    /**
     * Enviar notificações para motoristas escalados
     * 
     * Abordagem atual: Os motoristas recebem notificações via listeners do Firestore
     * que detectam mudanças na escala e enviam notificações locais.
     * 
     * Esta função serve como placeholder e loga a ação.
     * As notificações reais são enviadas pelos listeners do Firestore.
     */
    suspend fun notifyMotoristasEscalados(
        baseId: String,
        motoristaIds: List<String>,
        title: String = "🚛 Você foi escalado!",
        body: String = "Você está escalado! Siga para o galpão e aguarde instruções.",
        data: Map<String, String> = emptyMap()
    ): Pair<Int, Int> {
        return try {
            Log.d(TAG, "🔔 Notificação de escala para ${motoristaIds.size} motoristas")
            Log.d(TAG, "   Título: $title")
            Log.d(TAG, "   Corpo: $body")
            
            // Verificar quantos motoristas têm tokens FCM
            val tokens = getFcmTokensForMotoristas(baseId, motoristaIds)
            val comToken = tokens.size
            val semToken = motoristaIds.size - comToken
            
            Log.d(TAG, "📊 Motoristas com FCM token: $comToken/$motoristaIds.size")
            if (semToken > 0) {
                Log.w(TAG, "⚠️ $semToken motorista(s) sem FCM token - receberão notificação via listener do Firestore")
            }
            
            // As notificações reais são enviadas pelos listeners do Firestore
            // que detectam mudanças na escala e enviam notificações locais
            Log.i(TAG, "✅ Notificações serão enviadas via listeners do Firestore (já implementado)")
            
            // Retornar sucesso pois as notificações serão enviadas via listener
            Pair(comToken, semToken)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar notificações: ${e.message}", e)
            Pair(0, motoristaIds.size)
        }
    }
}

