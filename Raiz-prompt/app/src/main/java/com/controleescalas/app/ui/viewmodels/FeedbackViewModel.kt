package com.controleescalas.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.models.Feedback
import com.controleescalas.app.data.repositories.FeedbackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar feedbacks
 */
class FeedbackViewModel : ViewModel() {
    private val repository = FeedbackRepository()

    // States
    private val _feedbacks = MutableStateFlow<List<Feedback>>(emptyList())
    val feedbacks: StateFlow<List<Feedback>> = _feedbacks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Carregar feedbacks de um admin
     */
    fun carregarMeusFeedbacks(adminId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val feedbacks = repository.getMeusFeedbacks(adminId)
                _feedbacks.value = feedbacks
            } catch (e: Exception) {
                _error.value = "Erro ao carregar feedbacks: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Carregar todos os feedbacks (super admin)
     */
    fun carregarTodosFeedbacks() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val feedbacks = repository.getAllFeedbacks()
                _feedbacks.value = feedbacks
            } catch (e: Exception) {
                _error.value = "Erro ao carregar feedbacks: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Criar novo feedback
     */
    fun criarFeedback(
        baseId: String,
        adminId: String,
        adminNome: String,
        baseNome: String,
        mensagem: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                if (mensagem.isBlank()) {
                    _error.value = "A mensagem não pode estar vazia"
                    return@launch
                }

                val feedbackId = repository.criarFeedback(
                    baseId = baseId,
                    adminId = adminId,
                    adminNome = adminNome,
                    baseNome = baseNome,
                    mensagem = mensagem.trim()
                )

                if (feedbackId != null) {
                    _message.value = "Feedback enviado com sucesso!"
                    // Recarregar lista
                    carregarMeusFeedbacks(adminId)
                } else {
                    _error.value = "Erro ao enviar feedback"
                }
            } catch (e: Exception) {
                _error.value = "Erro: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Curtir feedback (super admin)
     */
    fun curtirFeedback(feedbackId: String, superAdminId: String) {
        viewModelScope.launch {
            try {
                val sucesso = repository.curtirFeedback(feedbackId, superAdminId)
                if (sucesso) {
                    _message.value = "Feedback curtido!"
                    // Recarregar lista
                    carregarTodosFeedbacks()
                } else {
                    _error.value = "Erro ao curtir feedback"
                }
            } catch (e: Exception) {
                _error.value = "Erro: ${e.message}"
            }
        }
    }

    /**
     * Marcar feedback como lido (quando super admin abre)
     */
    fun marcarComoLido(feedbackId: String) {
        viewModelScope.launch {
            try {
                repository.marcarComoLido(feedbackId)
            } catch (e: Exception) {
                // Silencioso, não precisa mostrar erro
            }
        }
    }

    /**
     * Editar feedback
     */
    fun editarFeedback(feedbackId: String, novaMensagem: String, adminId: String, isSuperAdmin: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _message.value = null

                if (novaMensagem.isBlank()) {
                    _error.value = "A mensagem não pode estar vazia"
                    return@launch
                }

                val sucesso = repository.editarFeedback(feedbackId, novaMensagem)
                if (sucesso) {
                    _message.value = "Feedback editado com sucesso!"
                    // Recarregar lista
                    if (isSuperAdmin) {
                        carregarTodosFeedbacks()
                    } else {
                        carregarMeusFeedbacks(adminId)
                    }
                } else {
                    _error.value = "Erro ao editar feedback"
                }
            } catch (e: Exception) {
                _error.value = "Erro: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Excluir feedback
     */
    fun excluirFeedback(feedbackId: String, adminId: String, isSuperAdmin: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _message.value = null

                val sucesso = repository.excluirFeedback(feedbackId)
                if (sucesso) {
                    _message.value = "Feedback excluído com sucesso!"
                    // Recarregar lista
                    if (isSuperAdmin) {
                        carregarTodosFeedbacks()
                    } else {
                        carregarMeusFeedbacks(adminId)
                    }
                } else {
                    _error.value = "Erro ao excluir feedback"
                }
            } catch (e: Exception) {
                _error.value = "Erro: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Limpar mensagens
     */
    fun clearMessages() {
        _message.value = null
        _error.value = null
    }
}

