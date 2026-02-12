package com.controleescalas.app.data

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador centralizado de notificações para evitar duplicatas
 * Usa sistema de debounce baseado em chave única por tipo de notificação
 */
class NotificationManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationManager"
        private const val DEBOUNCE_TIME_MS = 5000L // 5 segundos
        
        @Volatile
        private var INSTANCE: NotificationManager? = null
        
        fun getInstance(context: Context): NotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val notificationService = NotificationService(context)
    
    // Mapa para rastrear última notificação enviada por chave
    // Chave: tipo_notificacao + dados_relevantes (ex: "CARREGANDO_123_vaga5_rotaA")
    // Valor: timestamp da última notificação
    private val lastNotificationTime = ConcurrentHashMap<String, Long>()
    
    /**
     * Enviar notificação de chamada para carregamento com debounce
     */
    fun sendMotoristaChamadaNotification(
        motoristaNome: String,
        vaga: String,
        rota: String,
        force: Boolean = false
    ) {
        val key = "CARREGANDO_${vaga}_${rota}"
        
        if (!force && shouldSkipNotification(key)) {
            Log.d(TAG, "⏭️ Notificação de carregamento ignorada (duplicata recente): vaga=$vaga, rota=$rota")
            return
        }
        
        notificationService.sendMotoristaChamadaNotification(motoristaNome, vaga, rota)
        lastNotificationTime[key] = System.currentTimeMillis()
        Log.d(TAG, "✅ Notificação de carregamento enviada: vaga=$vaga, rota=$rota")
    }
    
    /**
     * Enviar notificação de chamada para estacionamento com debounce
     */
    fun sendMotoristaEstacionamentoNotification(
        motoristaNome: String,
        force: Boolean = false
    ) {
        val key = "IR_ESTACIONAMENTO"
        
        if (!force && shouldSkipNotification(key)) {
            Log.d(TAG, "⏭️ Notificação de estacionamento ignorada (duplicata recente)")
            return
        }
        
        notificationService.sendMotoristaEstacionamentoNotification(motoristaNome)
        lastNotificationTime[key] = System.currentTimeMillis()
        Log.d(TAG, "✅ Notificação de estacionamento enviada")
    }
    
    /**
     * Enviar notificação de conclusão com debounce
     */
    fun sendConclusaoNotification(
        mensagem: String,
        force: Boolean = false
    ) {
        val key = "CONCLUIDO"
        
        if (!force && shouldSkipNotification(key)) {
            Log.d(TAG, "⏭️ Notificação de conclusão ignorada (duplicata recente)")
            return
        }
        
        notificationService.sendStatusUpdateNotification(
            status = "Carregamento Concluído",
            mensagem = mensagem
        )
        lastNotificationTime[key] = System.currentTimeMillis()
        Log.d(TAG, "✅ Notificação de conclusão enviada")
    }
    
    /**
     * Enviar notificação de atualização de status genérica com debounce
     */
    fun sendStatusUpdateNotification(
        status: String,
        mensagem: String,
        force: Boolean = false
    ) {
        val key = "STATUS_${status}"
        
        if (!force && shouldSkipNotification(key)) {
            Log.d(TAG, "⏭️ Notificação de status ignorada (duplicata recente): status=$status")
            return
        }
        
        notificationService.sendStatusUpdateNotification(status, mensagem)
        lastNotificationTime[key] = System.currentTimeMillis()
        Log.d(TAG, "✅ Notificação de status enviada: status=$status")
    }
    
    /**
     * Verificar se deve pular notificação (debounce)
     */
    private fun shouldSkipNotification(key: String): Boolean {
        val lastTime = lastNotificationTime[key] ?: return false
        val timeSinceLastNotification = System.currentTimeMillis() - lastTime
        return timeSinceLastNotification < DEBOUNCE_TIME_MS
    }
    
    /**
     * Limpar histórico de notificações (útil quando status muda significativamente)
     */
    fun clearNotificationHistory() {
        lastNotificationTime.clear()
        Log.d(TAG, "🧹 Histórico de notificações limpo")
    }
    
    /**
     * Limpar histórico de uma chave específica
     */
    fun clearNotificationHistory(key: String) {
        lastNotificationTime.remove(key)
        Log.d(TAG, "🧹 Histórico de notificação limpo para chave: $key")
    }
}

