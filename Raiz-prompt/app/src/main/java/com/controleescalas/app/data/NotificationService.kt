package com.controleescalas.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.controleescalas.app.MainActivity
import com.controleescalas.app.R
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Serviço para gerenciar notificações push e FCM tokens
 */
class NotificationService(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "controle_escalas_channel"
        const val CHANNEL_NAME = "Controle de Escalas"
        const val CHANNEL_DESCRIPTION = "Notificações do sistema de controle de escalas"
        
        // Tipos de notificação
        const val TYPE_CHAMADA_MOTORISTA = "chamada_motorista"
        const val TYPE_CHAMADA_CARREGAMENTO = "chamada_carregamento"
        const val TYPE_STATUS_UPDATE = "status_update"
        const val TYPE_ESCALA_UPDATE = "escala_update"
        const val TYPE_MOTORISTA_UPDATE = "motorista_update"
        const val TYPE_DISPONIBILIDADE_UPDATE = "disponibilidade_update"
        const val TYPE_SUPERADMIN_NOVA_BASE = "superadmin_nova_base"
        const val TYPE_SUPERADMIN_NOVO_FEEDBACK = "superadmin_novo_feedback"
        
        private const val TAG = "NotificationService"
    }

    private val settingsManager = NotificationSettingsManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        createNotificationChannel()
    }

    /**
     * Chamar no startup do app para garantir que o canal exista antes de qualquer FCM.
     * Necessário para notificações quando o app está fechado (Huawei, etc).
     */
    fun createNotificationChannelAtStartup() {
        createNotificationChannel()
    }
    
    /**
     * Criar canal de notificação
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500) // Padrão de vibração
                enableLights(true)
                lightColor = Color.GREEN // Verde
                setSound(soundUri, audioAttributes) // Som com atributos de áudio
                setShowBadge(true) // Mostrar badge
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Visível na tela de bloqueio
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Canal de notificação criado: $CHANNEL_ID com som e vibração habilitados")
        }
    }

    /**
     * Verificar se uma notificação está habilitada (versão síncrona usando runBlocking)
     */
    private fun isNotificationEnabled(type: String): Boolean {
        return runBlocking {
            when (type) {
                TYPE_MOTORISTA_UPDATE -> settingsManager.isNotificacaoMotoristaConcluidoEnabled()
                TYPE_CHAMADA_MOTORISTA -> settingsManager.isNotificacaoChamadaMotoristaEnabled()
                TYPE_STATUS_UPDATE -> settingsManager.isNotificacaoStatusUpdateEnabled()
                TYPE_ESCALA_UPDATE -> settingsManager.isNotificacaoEscalaUpdateEnabled()
                TYPE_SUPERADMIN_NOVA_BASE, TYPE_SUPERADMIN_NOVO_FEEDBACK -> true // Sempre habilitado para super admin
                else -> settingsManager.isNotificacaoGenericEnabled()
            }
        }
    }
    
    /**
     * Enviar notificação local (verifica preferências antes de enviar)
     */
    fun sendLocalNotification(
        title: String,
        message: String,
        type: String = TYPE_STATUS_UPDATE,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        // Verificar se notificação está habilitada
        if (!isNotificationEnabled(type)) {
            Log.d(TAG, "⏭️ Notificação desabilitada pelo usuário: type=$type, title=$title")
            return
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_type", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Usar notificationId único para cada notificação
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Som padrão do sistema
        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Você precisa criar este ícone
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Som, vibração e luz
            .setSound(soundUri) // Garantir som
            .setVibrate(longArrayOf(0, 500, 250, 500)) // Padrão de vibração: espera, vibra, espera, vibra
            .setLights(Color.GREEN, 1000, 1000) // LED verde, 1s ligado, 1s desligado
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Texto expandido
            .build()

        with(NotificationManagerCompat.from(context)) {
            if (areNotificationsEnabled()) {
                notify(notificationId, notification)
                Log.d(TAG, "✅ Notificação local enviada: $title - $message (ID: $notificationId)")
                println("✅ NotificationService: Notificação enviada - ID=$notificationId, Title=$title")
            } else {
                Log.w(TAG, "⚠️ Notificações desabilitadas pelo usuário - não foi possível enviar: $title")
                println("⚠️ NotificationService: Notificações desabilitadas pelo usuário")
            }
        }
    }

    /**
     * Enviar notificação de chamada para motorista
     */
    fun sendMotoristaChamadaNotification(
        motoristaNome: String,
        vaga: String,
        rota: String
    ) {
        val title = "🚚 Chamada para Carregamento"
        val message = "Olá $motoristaNome! Subir agora para a vaga $vaga com rota $rota"
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_CHAMADA_MOTORISTA
        )
    }
    
    /**
     * Enviar notificação de chamada para estacionamento
     */
    fun sendMotoristaEstacionamentoNotification(
        motoristaNome: String
    ) {
        val title = "🅿️ Chamada para Estacionamento"
        val message = "Olá $motoristaNome! Vá para o ESTACIONAMENTO e aguarde"
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_CHAMADA_MOTORISTA
        )
    }
    
    /**
     * Notificar motorista quando for adicionado à onda
     */
    fun sendMotoristaAdicionadoNotification(
        motoristaNome: String,
        turno: String,
        nomeOnda: String,
        horario: String
    ) {
        val title = "🚨 Você foi escalado!"
        val message = "Turno $turno - $nomeOnda às $horario"
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_ESCALA_UPDATE
        )
    }
    
    /**
     * Notificar motorista quando escala for alterada
     */
    fun sendEscalaAlteradaNotification(
        motoristaNome: String,
        mudancas: String
    ) {
        val title = "⚠️ Escala Alterada"
        val message = mudancas
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_ESCALA_UPDATE
        )
    }
    
    /**
     * Notificar admin sobre ação do motorista
     */
    fun sendAdminNotification(
        motoristaNome: String,
        acao: String
    ) {
        val title = "📢 Atualização de Motorista"
        val message = "$motoristaNome $acao"
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_MOTORISTA_UPDATE
        )
    }

    /**
     * Enviar notificação de atualização de status
     */
    fun sendStatusUpdateNotification(
        status: String,
        mensagem: String
    ) {
        val title = "Status Atualizado: $status"
        
        sendLocalNotification(
            title = title,
            message = mensagem,
            type = TYPE_STATUS_UPDATE
        )
    }

    /**
     * Enviar notificação de atualização de escala
     */
    fun sendEscalaUpdateNotification(
        motoristaId: String,
        motoristaNome: String,
        onda: String,
        vaga: String,
        rota: String,
        sacas: Int? = null
    ) {
        val title = "🚛 Você foi escalado!"
        val message = "Você está escalado! Siga para o galpão e aguarde instruções."
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_ESCALA_UPDATE
        )
        
        Log.d(TAG, "✅ Notificação de escala enviada para $motoristaNome")
    }

    /**
     * Notificar super admin sobre nova transportadora cadastrada
     */
    fun sendSuperAdminNovaBaseNotification(
        transportadoraNome: String,
        baseNome: String
    ) {
        val title = "🏢 Nova Transportadora Cadastrada"
        val message = "$transportadoraNome ($baseNome) está aguardando aprovação"
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_SUPERADMIN_NOVA_BASE
        )
    }

    /**
     * Notificar super admin sobre novo feedback recebido
     */
    fun sendSuperAdminNovoFeedbackNotification(
        adminNome: String,
        baseNome: String,
        mensagem: String
    ) {
        val title = "💬 Novo Feedback Recebido"
        val messagePreview = if (mensagem.length > 50) {
            mensagem.take(47) + "..."
        } else {
            mensagem
        }
        val message = "$adminNome ($baseNome): $messagePreview"
        
        sendLocalNotification(
            title = title,
            message = message,
            type = TYPE_SUPERADMIN_NOVO_FEEDBACK
        )
    }

    /**
     * Obter token FCM do dispositivo
     */
    suspend fun getFcmToken(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "✅ FCM Token obtido: ${token.take(20)}...")
            token
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter FCM token", e)
            null
        }
    }
    
    /**
     * Salvar FCM token no Firestore para o usuário atual
     */
    suspend fun saveFcmTokenToFirestore(userId: String, baseId: String) {
        try {
            val token = getFcmToken()
            if (token != null) {
                // Tentar salvar em motoristas primeiro (sistema atual)
                val motoristaRef = FirebaseManager.firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .document(userId)
                
                try {
                    motoristaRef.update("fcmToken", token).await()
                    Log.d(TAG, "✅ FCM Token salvo no Firestore (motoristas) para usuário $userId")
                } catch (e: Exception) {
                    // Se falhar, tentar criar o documento se não existir
                    try {
                        motoristaRef.set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge()).await()
                        Log.d(TAG, "✅ FCM Token salvo no Firestore (motoristas, criado) para usuário $userId")
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro ao salvar FCM token em motoristas", e2)
                        // Tentar em usuarios como fallback (legado)
                        try {
                            val usuarioRef = FirebaseManager.firestore
                                .collection("bases")
                                .document(baseId)
                                .collection("usuarios")
                                .document(userId)
                            usuarioRef.update("fcmToken", token).await()
                            Log.d(TAG, "✅ FCM Token salvo no Firestore (usuarios, fallback) para usuário $userId")
                        } catch (e3: Exception) {
                            Log.e(TAG, "❌ Erro ao salvar FCM token em usuarios também", e3)
                        }
                    }
                }
            } else {
                Log.w(TAG, "⚠️ FCM Token é nulo, não foi possível salvar")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao salvar FCM token no Firestore", e)
        }
    }
    
    /**
     * Remover FCM token do Firestore (útil no logout)
     */
    suspend fun removeFcmTokenFromFirestore(userId: String, baseId: String) {
        try {
            val userRef = FirebaseManager.firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                
            userRef.update("fcmToken", null).await()
            Log.d(TAG, "✅ FCM Token removido do Firestore para usuário $userId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao remover FCM token do Firestore", e)
        }
    }
    
    /**
     * Inscrever em tópico específico da base (para notificações broadcast)
     */
    suspend fun subscribeToBaseTopic(baseId: String) {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("base_$baseId").await()
            Log.d(TAG, "✅ Inscrito no tópico base_$baseId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao se inscrever no tópico da base", e)
        }
    }
    
    /**
     * Cancelar inscrição em tópico da base
     */
    suspend fun unsubscribeFromBaseTopic(baseId: String) {
        try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic("base_$baseId").await()
            Log.d(TAG, "✅ Desinscrição do tópico base_$baseId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao cancelar inscrição no tópico da base", e)
        }
    }

    /**
     * Criar notificação para foreground service
     */
    fun createForegroundNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Você precisa criar este ícone
            .setContentTitle("Controle de Escalas")
            .setContentText("Monitorando escalas em background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Notificação permanente
            .setContentIntent(pendingIntent)
            .build()
    }
}
