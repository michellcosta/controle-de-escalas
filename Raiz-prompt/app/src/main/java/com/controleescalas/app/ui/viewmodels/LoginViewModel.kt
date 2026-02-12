package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.data.models.SavedSession
import com.controleescalas.app.data.repositories.AccountInfo
import com.controleescalas.app.data.repositories.AuthRepository
import com.controleescalas.app.data.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * LoginViewModel - Gerencia estado da tela de login e salva sessões
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository()
    private val sessionManager = SessionManager(application.applicationContext)
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _loginResult = MutableStateFlow<LoginResult?>(null)
    val loginResult: StateFlow<LoginResult?> = _loginResult.asStateFlow()
    
    private val _availableAccounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    val availableAccounts: StateFlow<List<AccountInfo>> = _availableAccounts.asStateFlow()

    /**
     * Carregar contas disponíveis
     */
    fun loadAvailableAccounts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val accounts = authRepository.getAllAccounts()
                println("✅ LoginViewModel: ${accounts.size} contas encontradas")
                _availableAccounts.value = accounts
            } catch (e: Exception) {
                println("❌ Erro ao carregar contas: ${e.message}")
                _error.value = "Erro ao carregar contas disponíveis"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fazer login e salvar sessão
     */
    fun login(telefone: String, pin: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _loginResult.value = null
            
            try {
                val result = authRepository.login(telefone, pin)
                _loginResult.value = result
                
                if (result is LoginResult.Success) {
                    // Salvar sessão no SessionManager
                    // IMPORTANTE: Usar motoristaId (ID do documento), não telefone
                    val session = SavedSession(
                        userId = result.motoristaId, // ID do documento do motorista/admin
                        baseId = result.baseId,
                        baseName = result.baseName.ifBlank { "Transportadora" },
                        userName = result.nome,
                        userRole = result.papel
                    )
                    sessionManager.saveSession(session)
                    println("✅ LoginViewModel: Sessão salva - ${session.baseName} (${session.userName})")
                } else if (result is LoginResult.Error) {
                    _error.value = result.message
                }
            } catch (e: Exception) {
                _error.value = "Erro de conexão: ${e.message}"
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
        _loginResult.value = null
    }
}
