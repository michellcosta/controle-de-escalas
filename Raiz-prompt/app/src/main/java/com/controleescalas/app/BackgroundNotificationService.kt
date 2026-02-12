package com.controleescalas.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.controleescalas.app.data.NotificationService
import kotlinx.coroutines.*

/**
 * Serviço em background para gerenciar notificações e monitoramento
 */
class BackgroundNotificationService : Service() {
    
    companion object {
        private const val TAG = "BackgroundNotificationService"
        const val ACTION_START_SERVICE = "com.controleescalas.app.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.controleescalas.app.STOP_SERVICE"

        /**
         * Método estático para iniciar o serviço
         */
        fun startService(context: android.content.Context) {
            val intent = Intent(context, BackgroundNotificationService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: android.content.Context) {
            val intent = Intent(context, BackgroundNotificationService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationService: NotificationService
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        notificationService = NotificationService(this)
        Log.d(TAG, "BackgroundNotificationService criado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundService()
            }
            ACTION_STOP_SERVICE -> {
                stopService()
            }
            else -> {
                startForegroundService()
            }
        }
        
        return START_STICKY // Serviço será reiniciado se for morto
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Serviço não vinculado
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isServiceRunning = false
        Log.d(TAG, "BackgroundNotificationService destruído")
    }

    /**
     * Iniciar serviço em foreground
     */
    private fun startForegroundService() {
        if (isServiceRunning) return
        
        isServiceRunning = true
        
        // Criar notificação permanente para manter o serviço em foreground
        val notification = notificationService.createForegroundNotification()
        
        // Iniciar como foreground service
        startForeground(1, notification)
        
        // Iniciar monitoramento em background
        serviceScope.launch {
            startBackgroundMonitoring()
        }
        
        Log.d(TAG, "Serviço iniciado em foreground")
    }

    /**
     * Parar serviço
     */
    private fun stopService() {
        isServiceRunning = false
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "Serviço parado")
    }

    /**
     * Iniciar monitoramento em background
     */
    private suspend fun startBackgroundMonitoring() {
        while (isServiceRunning) {
            try {
                // Aqui você pode implementar lógica de monitoramento
                // Por exemplo:
                // - Verificar status de motoristas
                // - Monitorar geofences
                // - Enviar notificações periódicas
                // - Sincronizar dados com Firebase
                
                monitorMotoristaStatus()
                
                // Aguardar antes da próxima verificação
                delay(30000) // 30 segundos
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro no monitoramento: ${e.message}")
                delay(60000) // Aguardar mais tempo em caso de erro
            }
        }
    }

    /**
     * Monitorar status dos motoristas
     */
    private suspend fun monitorMotoristaStatus() {
        // TODO: Implementar lógica de monitoramento
        // Exemplo:
        // 1. Buscar motoristas com status "A CAMINHO" há muito tempo
        // 2. Verificar se estão próximos ao galpão
        // 3. Enviar notificações de lembrete se necessário
        
        Log.d(TAG, "Monitorando status dos motoristas...")
    }
}