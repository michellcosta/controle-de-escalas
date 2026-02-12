package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.NotificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar notificações
 */
class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val notificationService = NotificationService(application.applicationContext)
    
    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()
    
    private val _isTokenLoading = MutableStateFlow(false)
    val isTokenLoading: StateFlow<Boolean> = _isTokenLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadFcmToken()
    }

    /**
     * Carregar token FCM
     */
    private fun loadFcmToken() {
        viewModelScope.launch {
            _isTokenLoading.value = true
            _error.value = null
            
            try {
                val token = notificationService.getFcmToken()
                _fcmToken.value = token
            } catch (e: Exception) {
                _error.value = "Erro ao obter token FCM: ${e.message}"
            } finally {
                _isTokenLoading.value = false
            }
        }
    }

    /**
     * Enviar notificação de chamada para motorista
     */
    fun sendMotoristaChamada(
        motoristaNome: String,
        vaga: String,
        rota: String
    ) {
        notificationService.sendMotoristaChamadaNotification(motoristaNome, vaga, rota)
    }

    /**
     * Enviar notificação de atualização de status
     */
    fun sendStatusUpdate(status: String, mensagem: String) {
        notificationService.sendStatusUpdateNotification(status, mensagem)
    }

    /**
     * Enviar notificação de atualização de escala
     */
    fun sendEscalaUpdate(
        motoristaId: String,
        motoristaNome: String,
        onda: String,
        vaga: String,
        rota: String,
        sacas: Int? = null
    ) {
        notificationService.sendEscalaUpdateNotification(motoristaId, motoristaNome, onda, vaga, rota, sacas)
    }

    /**
     * Enviar notificação customizada
     */
    fun sendCustomNotification(title: String, message: String) {
        notificationService.sendLocalNotification(title, message)
    }

    /**
     * Limpar erro
     */
    fun clearError() {
        _error.value = null
    }
}



