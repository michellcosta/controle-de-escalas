package com.controleescalas.app

import android.app.NotificationManager
import android.util.Log
import com.controleescalas.app.data.NotificationService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Serviço para receber mensagens do Firebase Cloud Messaging
 */
class ControleEscalasMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "ControleEscalasFCM"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Verificar se a mensagem contém dados
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Verificar se a mensagem contém notificação
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            handleNotificationMessage(it.title ?: "Controle de Escalas", it.body ?: "")
        }
    }

    /**
     * Lidar com mensagens de dados
     */
    private fun handleDataMessage(data: Map<String, String>) {
        // Suportar "tipo" (API Python) e "type" (legado)
        val type = data["tipo"] ?: data["type"] ?: return
        val title = data["title"] ?: "Controle de Escalas"
        val message = data["message"] ?: ""
        
        when (type) {
            "chamada", "chamada_motorista" -> {
                val motoristaNome = data["motorista_nome"] ?: "Motorista"
                val vaga = data["vaga"] ?: ""
                val rota = data["rota"] ?: ""
                NotificationService(this).sendMotoristaChamadaNotification(
                    motoristaNome, vaga, rota
                )
            }
            "chamada_estacionamento" -> {
                NotificationService(this).sendLocalNotification(
                    title = "🅿️ Chamada para Estacionamento",
                    message = "Vá para o ESTACIONAMENTO e aguarde",
                    type = NotificationService.TYPE_CHAMADA_MOTORISTA
                )
            }
            "status_update" -> {
                val status = data["status"] ?: ""
                NotificationService(this).sendStatusUpdateNotification(status, message)
            }
            "escala_update" -> {
                val motoristaId = data["motoristaId"] ?: ""
                val motoristaNome = data["motoristaNome"] ?: ""
                val onda = data["onda"] ?: ""
                val vaga = data["vaga"] ?: ""
                val rota = data["rota"] ?: ""
                val sacas = data["sacas"]?.toIntOrNull()
                NotificationService(this).sendEscalaUpdateNotification(motoristaId, motoristaNome, onda, vaga, rota, sacas)
            }
            else -> {
                NotificationService(this).sendLocalNotification(title, message)
            }
        }
    }

    /**
     * Lidar com mensagens de notificação
     */
    private fun handleNotificationMessage(title: String, body: String) {
        NotificationService(this).sendLocalNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔄 Novo FCM token recebido: ${token.take(20)}...")
        
        // ✅ CORREÇÃO: Salvar token automaticamente quando renovado
        // Buscar sessão ativa e salvar o token para todos os usuários logados
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val sessionManager = com.controleescalas.app.data.SessionManager(applicationContext)
                val sessions = sessionManager.getUserSessions()
                
                // Salvar token para todas as sessões ativas (multi-usuário)
                sessions.sessions.forEach { session ->
                    try {
                        val notificationService = com.controleescalas.app.data.NotificationService(applicationContext)
                        notificationService.saveFcmTokenToFirestore(session.userId, session.baseId)
                        Log.d(TAG, "✅ FCM token salvo automaticamente para ${session.userName} (${session.baseName})")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao salvar token para ${session.userName}: ${e.message}", e)
                    }
                }
                
                if (sessions.sessions.isEmpty()) {
                    Log.d(TAG, "ℹ️ Nenhuma sessão ativa encontrada, token será salvo no próximo login")
                } else {
                    // Não precisa fazer nada se houver sessões
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao salvar FCM token automaticamente: ${e.message}", e)
            }
        }
    }
}



