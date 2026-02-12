package com.controleescalas.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_settings")

/**
 * Gerenciador de preferências de notificações
 */
class NotificationSettingsManager(private val context: Context) {
    
    companion object {
        // Chaves das preferências
        private val NOTIFICATION_MOTORISTA_CONCLUIDO_KEY = booleanPreferencesKey("notif_motorista_concluido")
        private val NOTIFICATION_CHAMADA_MOTORISTA_KEY = booleanPreferencesKey("notif_chamada_motorista")
        private val NOTIFICATION_STATUS_UPDATE_KEY = booleanPreferencesKey("notif_status_update")
        private val NOTIFICATION_ESCALA_UPDATE_KEY = booleanPreferencesKey("notif_escala_update")
        private val NOTIFICATION_GENERIC_KEY = booleanPreferencesKey("notif_generic")
        private val XIAOMI_PROMPT_SHOWN_KEY = booleanPreferencesKey("xiaomi_notification_prompt_shown")
        
        // Valores padrão (todas habilitadas por padrão)
        private const val DEFAULT_ENABLED = true
    }
    
    suspend fun wasXiaomiPromptShown(): Boolean {
        return context.notificationSettingsDataStore.data.first()[XIAOMI_PROMPT_SHOWN_KEY] ?: false
    }
    
    suspend fun setXiaomiPromptShown() {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[XIAOMI_PROMPT_SHOWN_KEY] = true
        }
    }
    
    // Flow para notificação de conclusão de motorista
    val notificacaoMotoristaConcluido: Flow<Boolean> = context.notificationSettingsDataStore.data.map { preferences ->
        preferences[NOTIFICATION_MOTORISTA_CONCLUIDO_KEY] ?: DEFAULT_ENABLED
    }
    
    // Flow para notificação de chamada de motorista (habilitada por padrão - crítico!)
    val notificacaoChamadaMotorista: Flow<Boolean> = context.notificationSettingsDataStore.data.map { preferences ->
        preferences[NOTIFICATION_CHAMADA_MOTORISTA_KEY] ?: DEFAULT_ENABLED
    }
    
    // Flow para notificação de atualização de status
    val notificacaoStatusUpdate: Flow<Boolean> = context.notificationSettingsDataStore.data.map { preferences ->
        preferences[NOTIFICATION_STATUS_UPDATE_KEY] ?: DEFAULT_ENABLED
    }
    
    // Flow para notificação de atualização de escala
    val notificacaoEscalaUpdate: Flow<Boolean> = context.notificationSettingsDataStore.data.map { preferences ->
        preferences[NOTIFICATION_ESCALA_UPDATE_KEY] ?: DEFAULT_ENABLED
    }
    
    // Flow para notificações genéricas
    val notificacaoGeneric: Flow<Boolean> = context.notificationSettingsDataStore.data.map { preferences ->
        preferences[NOTIFICATION_GENERIC_KEY] ?: DEFAULT_ENABLED
    }
    
    /**
     * Atualizar preferência de notificação de conclusão de motorista
     */
    suspend fun setNotificacaoMotoristaConcluido(enabled: Boolean) {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[NOTIFICATION_MOTORISTA_CONCLUIDO_KEY] = enabled
        }
    }
    
    /**
     * Atualizar preferência de notificação de chamada de motorista
     */
    suspend fun setNotificacaoChamadaMotorista(enabled: Boolean) {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[NOTIFICATION_CHAMADA_MOTORISTA_KEY] = enabled
        }
    }
    
    /**
     * Atualizar preferência de notificação de atualização de status
     */
    suspend fun setNotificacaoStatusUpdate(enabled: Boolean) {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[NOTIFICATION_STATUS_UPDATE_KEY] = enabled
        }
    }
    
    /**
     * Atualizar preferência de notificação de atualização de escala
     */
    suspend fun setNotificacaoEscalaUpdate(enabled: Boolean) {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[NOTIFICATION_ESCALA_UPDATE_KEY] = enabled
        }
    }
    
    /**
     * Atualizar preferência de notificações genéricas
     */
    suspend fun setNotificacaoGeneric(enabled: Boolean) {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[NOTIFICATION_GENERIC_KEY] = enabled
        }
    }
    
    /**
     * Obter estado atual de uma preferência específica (sem Flow)
     */
    suspend fun isNotificacaoMotoristaConcluidoEnabled(): Boolean {
        return context.notificationSettingsDataStore.data.first()[NOTIFICATION_MOTORISTA_CONCLUIDO_KEY] ?: DEFAULT_ENABLED
    }
    
    suspend fun isNotificacaoChamadaMotoristaEnabled(): Boolean {
        return context.notificationSettingsDataStore.data.first()[NOTIFICATION_CHAMADA_MOTORISTA_KEY] ?: DEFAULT_ENABLED
    }
    
    suspend fun isNotificacaoStatusUpdateEnabled(): Boolean {
        return context.notificationSettingsDataStore.data.first()[NOTIFICATION_STATUS_UPDATE_KEY] ?: DEFAULT_ENABLED
    }
    
    suspend fun isNotificacaoEscalaUpdateEnabled(): Boolean {
        return context.notificationSettingsDataStore.data.first()[NOTIFICATION_ESCALA_UPDATE_KEY] ?: DEFAULT_ENABLED
    }
    
    suspend fun isNotificacaoGenericEnabled(): Boolean {
        return context.notificationSettingsDataStore.data.first()[NOTIFICATION_GENERIC_KEY] ?: DEFAULT_ENABLED
    }
}
