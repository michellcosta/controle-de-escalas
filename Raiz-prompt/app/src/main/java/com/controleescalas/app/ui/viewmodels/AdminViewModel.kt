package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.repositories.AuthRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.data.NotificationService
import com.controleescalas.app.data.NotificationApiService
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.data.models.toAdminMotoristaCardData
import com.controleescalas.app.data.FirebaseManager
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * AdminViewModel - Gerencia estado da tela do admin
 */
class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val motoristaRepository = MotoristaRepository()
    private val authRepository = AuthRepository()
    private val notificationService = NotificationService(application.applicationContext)
    private val notificationApiService = NotificationApiService()
    
    private val _motoristas = MutableStateFlow<List<AdminMotoristaCardData>>(emptyList())
    val motoristas: StateFlow<List<AdminMotoristaCardData>> = _motoristas.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    private var motoristasListener: ListenerRegistration? = null
    
    /**
     * Carregar motoristas da base com listener em tempo real
     */
    fun loadMotoristas(baseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Remover listener anterior se existir
                motoristasListener?.remove()
                
                // Configurar listener em tempo real
                motoristasListener = FirebaseManager.firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereEqualTo("ativo", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            _error.value = "Erro ao monitorar motoristas: ${error.message}"
                            return@addSnapshotListener
                        }
                        
                        if (snapshot != null) {
                            val motoristas = snapshot.documents.mapNotNull { doc ->
                                val motorista = doc.toObject(Motorista::class.java)
                                motorista?.copy(id = doc.id)?.toAdminMotoristaCardData()
                            }
                            // Filtrar super admins - eles não aparecem na gestão
                            val motoristasFiltrados = motoristas.filter { it.papel != "superadmin" }
                            _motoristas.value = motoristasFiltrados
                        }
                    }
                    
            } catch (e: Exception) {
                _error.value = "Erro ao carregar motoristas: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Criar novo motorista
     */
    fun createMotorista(
        baseId: String,
        nome: String,
        telefone: String,
        pin: String,
        papel: String,
        modalidade: String = "FROTA",
        criadoPor: String? = null
    ) {
        println("🔵 AdminViewModel.createMotorista chamado: nome=$nome, telefone=$telefone, papel=$papel, modalidade=$modalidade")
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Verificar se telefone já existe
                if (authRepository.telefoneExists(telefone)) {
                    _error.value = "Telefone já cadastrado"
                    return@launch
                }
                
                println("🔵 Chamando motoristaRepository.createMotorista...")
                val motoristaId = motoristaRepository.createMotorista(
                    baseId = baseId,
                    nome = nome,
                    telefone = telefone,
                    pin = authRepository.hashPin(pin), // Hash PIN before sending
                    papel = papel,
                    modalidade = modalidade,
                    criadoPor = criadoPor
                )
                
                if (motoristaId != null) {
                    println("✅ Motorista criado com sucesso!")
                    _message.value = "Motorista criado com sucesso!"
                    // Listener em tempo real atualizará automaticamente
                } else {
                    _error.value = "Erro ao criar motorista"
                }
            } catch (e: Exception) {
                println("❌ Erro ao criar motorista: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao criar motorista: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Chamar motorista para carregar
     */
    fun chamarMotorista(
        motoristaId: String,
        baseId: String,
        vagaAtual: String,
        rotaAtual: String,
        motoristaNome: String
    ) {
        viewModelScope.launch {
            try {
                // ✅ USAR API PYTHON para enviar notificação push
                // Isso garante que a notificação funcione mesmo com o app fechado
                val title = "🚚 Chamada para Carregamento"
                val body = "Subir agora para a VAGA $vagaAtual${if (rotaAtual.isNotBlank()) " com rota $rotaAtual" else ""}"
                
                val data = mapOf(
                    "tipo" to "chamada",
                    "vaga" to vagaAtual,
                    "rota" to rotaAtual
                )
                
                // Chamar API Python para enviar notificação push
                val (success, error) = notificationApiService.notifyMotorista(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    title = title,
                    body = body,
                    data = data
                )
                
                if (success) {
                    // Atualizar status no Firestore também
                    val updateSuccess = motoristaRepository.updateStatusMotorista(
                        motoristaId = motoristaId,
                        baseId = baseId,
                        estado = "CARREGANDO",
                        mensagem = "Subir agora para a vaga $vagaAtual com rota $rotaAtual",
                        vagaAtual = vagaAtual,
                        rotaAtual = rotaAtual
                    )
                    
                    if (updateSuccess) {
                        Log.d("AdminViewModel", "✅ Motorista chamado via API Python: $motoristaNome")
                        _message.value = "Motorista chamado com sucesso"
                    } else {
                        Log.w("AdminViewModel", "⚠️ Notificação enviada, mas falhou ao atualizar status no Firestore")
                        _message.value = "Motorista chamado com sucesso"
                    }
                } else {
                    // Fallback: tentar atualizar diretamente no Firestore (sem notificação push)
                    Log.w("AdminViewModel", "⚠️ API Python falhou, usando fallback: $error")
                    try {
                        val fallbackSuccess = motoristaRepository.updateStatusMotorista(
                            motoristaId = motoristaId,
                            baseId = baseId,
                            estado = "CARREGANDO",
                            mensagem = "Subir agora para a vaga $vagaAtual com rota $rotaAtual",
                            vagaAtual = vagaAtual,
                            rotaAtual = rotaAtual
                        )
                        if (fallbackSuccess) {
                            _message.value = "Motorista chamado (sem notificação push)"
                            Log.w("AdminViewModel", "⚠️ Usado fallback: atualização direta sem notificação push")
                        } else {
                            _error.value = "Erro ao chamar motorista"
                        }
                    } catch (fallbackError: Exception) {
                        _error.value = "Erro ao chamar motorista: ${error ?: fallbackError.message}"
                    }
                }
                
            } catch (e: Exception) {
                Log.e("AdminViewModel", "❌ Erro ao chamar motorista via API Python: ${e.message}", e)
                // Fallback: tentar atualizar diretamente no Firestore (sem notificação push)
                try {
                    val fallbackSuccess = motoristaRepository.updateStatusMotorista(
                        motoristaId = motoristaId,
                        baseId = baseId,
                        estado = "CARREGANDO",
                        mensagem = "Subir agora para a vaga $vagaAtual com rota $rotaAtual",
                        vagaAtual = vagaAtual,
                        rotaAtual = rotaAtual
                    )
                    if (fallbackSuccess) {
                        _message.value = "Motorista chamado (sem notificação push)"
                        Log.w("AdminViewModel", "⚠️ Usado fallback: atualização direta sem notificação push")
                    } else {
                        _error.value = "Erro ao chamar motorista"
                    }
                } catch (fallbackError: Exception) {
                    _error.value = "Erro ao chamar motorista: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Marcar motorista como concluído
     */
    fun concluirMotorista(motoristaId: String, baseId: String) {
        viewModelScope.launch {
            try {
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "CONCLUIDO",
                    mensagem = "Carregamento concluído"
                )
                
                if (success) {
                    _message.value = "Status atualizado para concluído"
                    // Listener em tempo real atualizará automaticamente
                } else {
                    _error.value = "Erro ao atualizar status"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar status: ${e.message}"
            }
        }
    }
    
    /**
     * Promover usuário para auxiliar
     */
    fun promoteToAuxiliar(userId: String, baseId: String) {
        viewModelScope.launch {
            try {
                motoristaRepository.updateUserRole(userId, baseId, "auxiliar")
                _message.value = "Usuário promovido a auxiliar"
                // Listener em tempo real atualizará automaticamente
            } catch (e: Exception) {
                _error.value = "Erro ao promover usuário: ${e.message}"
            }
        }
    }
    
    /**
     * Promover usuário para admin
     */
    fun promoteToAdmin(userId: String, baseId: String) {
        viewModelScope.launch {
            try {
                motoristaRepository.updateUserRole(userId, baseId, "admin")
                _message.value = "Usuário promovido a admin"
                // Listener em tempo real atualizará automaticamente
            } catch (e: Exception) {
                _error.value = "Erro ao promover usuário: ${e.message}"
            }
        }
    }
    
    /**
     * Rebaixar usuário para motorista
     */
    fun demoteToMotorista(userId: String, baseId: String) {
        viewModelScope.launch {
            try {
                motoristaRepository.updateUserRole(userId, baseId, "motorista")
                _message.value = "Usuário rebaixado a motorista"
                // Listener em tempo real atualizará automaticamente
            } catch (e: Exception) {
                _error.value = "Erro ao rebaixar usuário: ${e.message}"
            }
        }
    }
    
    /**
     * Rebaixar admin para auxiliar
     */
    fun demoteToAuxiliar(userId: String, baseId: String) {
        viewModelScope.launch {
            try {
                motoristaRepository.updateUserRole(userId, baseId, "auxiliar")
                _message.value = "Admin rebaixado a auxiliar"
                // Listener em tempo real atualizará automaticamente
            } catch (e: Exception) {
                _error.value = "Erro ao rebaixar usuário: ${e.message}"
            }
        }
    }
    
    /**
     * Remover usuário
     */
    fun removeUser(userId: String, baseId: String) {
        viewModelScope.launch {
            try {
                // Verificar se é super admin ANTES de qualquer outra validação
                val userRole = motoristaRepository.getUserRole(userId, baseId)
                
                if (userRole == "superadmin") {
                    _error.value = "O Super Admin não pode ser excluído."
                    return@launch
                }
                
                if (userRole == "admin") {
                    // Contar quantos admins ativos existem
                    val adminCount = motoristaRepository.countActiveAdmins(baseId)
                    
                    if (adminCount <= 1) {
                        _error.value = "Não é possível excluir o último admin. Adicione outro admin antes de excluir este."
                        return@launch
                    }
                    
                    // Se houver 2 ou mais admins, verificar se após a exclusão ainda haverá pelo menos 1
                    if (adminCount - 1 < 1) {
                        _error.value = "Deve haver pelo menos 1 admin ativo na base."
                        return@launch
                    }
                }
                
                // Se passou nas validações, remover o usuário
                motoristaRepository.removeUser(userId, baseId)
                _message.value = "Usuário removido"
                // Listener em tempo real atualizará automaticamente
            } catch (e: Exception) {
                _error.value = "Erro ao remover usuário: ${e.message}"
            }
        }
    }
    
    /**
     * Atualizar dados do motorista
     */
    fun updateMotorista(
        motoristaId: String,
        baseId: String,
        nome: String,
        telefone: String,
        modalidade: String,
        pin: String? = null, // PIN opcional
        funcao: String? = null // ✅ NOVO: Função (papel) opcional
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val success = motoristaRepository.updateMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    nome = nome,
                    telefone = telefone,
                    modalidade = modalidade,
                    pin = pin, // Passar PIN se fornecido
                    funcao = funcao // ✅ NOVO: Passar função se fornecido
                )
                
                if (success) {
                    _message.value = "Motorista atualizado com sucesso!"
                    // Listener em tempo real atualizará automaticamente
                } else {
                    _error.value = "Erro ao atualizar motorista"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar motorista: ${e.message}"
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
        _message.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        motoristasListener?.remove()
    }
}
