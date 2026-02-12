package com.controleescalas.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.repositories.AuthRepository
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.ui.screens.CreateBaseData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CreateBaseViewModel - Gerencia estado da tela de criação de base
 */
class CreateBaseViewModel : ViewModel() {
    private val baseRepository = BaseRepository()
    private val motoristaRepository = MotoristaRepository()
    private val authRepository = AuthRepository()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()
    
    /**
     * Criar nova base
     */
    fun createBase(baseData: CreateBaseData) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _success.value = null
            
            try {
                // Verificar se telefone já existe
                if (authRepository.telefoneExists(baseData.telefoneAdmin)) {
                    _error.value = "Telefone já cadastrado em outra base"
                    return@launch
                }
                
                // Criar base
                val baseId = baseRepository.createBase(baseData)
                
                if (baseId != null) {
                    // Criar admin da base
                    val adminId = motoristaRepository.createMotorista(
                        baseId = baseId,
                        nome = "Admin",
                        telefone = baseData.telefoneAdmin,
                        pin = authRepository.hashPin(baseData.pinAdmin),
                        papel = "admin"
                    )
                    
                    if (adminId != null) {
                        _success.value = "SUCCESS" // Flag para mostrar diálogo de aprovação pendente
                    } else {
                        _error.value = "Base criada, mas erro ao criar admin"
                    }
                } else {
                    _error.value = "Erro ao criar base"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao criar base: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Limpar mensagens
     */
    fun clearMessages() {
        _error.value = null
        _success.value = null
    }
}



