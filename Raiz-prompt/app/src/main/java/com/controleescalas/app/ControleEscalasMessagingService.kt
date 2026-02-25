package com.controleescalas.app

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.NotificationApiService
import com.controleescalas.app.data.NotificationService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

        // Só exibir via notification payload quando NÃO há data (evita duplicação).
        // Backend Python envia data-only; quando tem data, handleDataMessage já exibiu 1 notificação.
        if (remoteMessage.data.isEmpty() && remoteMessage.notification != null) {
            Log.d(TAG, "Message Notification Body: ${remoteMessage.notification!!.body}")
            handleNotificationMessage(
                remoteMessage.notification!!.title ?: "Controle de Escalas",
                remoteMessage.notification!!.body ?: ""
            )
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
            "request_location" -> {
                val baseId = data["baseId"] ?: return
                val motoristaId = data["motoristaId"] ?: return
                handleRequestLocation(baseId, motoristaId)
            }
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
     * Push silenciosa: admin pediu localização. Obter GPS e enviar ao backend.
     * Motorista não vê nada.
     */
    private fun handleRequestLocation(baseId: String, motoristaId: String) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (ContextCompat.checkSelfPermission(
                        this@ControleEscalasMessagingService,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Sem permissão de localização para request_location")
                    return@launch
                }
                val fusedClient: FusedLocationProviderClient =
                    LocationServices.getFusedLocationProviderClient(applicationContext)
                val cts = CancellationTokenSource()
                val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                if (location == null) {
                    Log.e(TAG, "Não foi possível obter localização para request_location")
                    return@launch
                }
                val lat = location.latitude
                val lng = location.longitude
                Log.d(TAG, "📍 Localização obtida para request_location: $lat, $lng")
                val user = FirebaseManager.auth.currentUser
                if (user == null) {
                    Log.e(TAG, "Usuário não autenticado para enviar localização")
                    return@launch
                }
                val tokenResult = user.getIdToken(true).await()
                val idToken = tokenResult?.token
                if (idToken == null) {
                    Log.e(TAG, "Falha ao obter token para enviar localização")
                    return@launch
                }
                val apiService = NotificationApiService()
                val (success, error) = apiService.receiveDriverLocation(baseId, motoristaId, lat, lng, idToken)
                if (success) {
                    Log.d(TAG, "✅ Localização enviada ao backend com sucesso")
                } else {
                    Log.e(TAG, "❌ Erro ao enviar localização: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro em handleRequestLocation: ${e.message}", e)
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



