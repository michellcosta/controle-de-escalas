package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.HistoricoRepository
import com.controleescalas.app.data.repositories.StatsRepository
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.data.models.AcaoHistorico
import com.controleescalas.app.data.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para aprovação de transportadoras (Super Admin)
 */
class BaseApprovalViewModel(application: Application) : AndroidViewModel(application) {
    private val baseRepository = BaseRepository()
    private val historicoRepository = HistoricoRepository()
    private val statsRepository = StatsRepository()
    
    private val _bases = MutableStateFlow<List<Base>>(emptyList())
    val bases: StateFlow<List<Base>> = _bases.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    private val _filtroStatus = MutableStateFlow<String?>(null)
    val filtroStatus: StateFlow<String?> = _filtroStatus.asStateFlow()
    
    /**
     * Carregar todas as bases
     */
    fun loadBases() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val todasBases = baseRepository.getAllBases()
                _bases.value = todasBases
                println("✅ BaseApprovalViewModel: ${todasBases.size} bases carregadas")
            } catch (e: Exception) {
                _error.value = "Erro ao carregar bases: ${e.message}"
                println("❌ BaseApprovalViewModel: Erro - ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carregar bases por status
     */
    fun loadBasesPorStatus(status: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _filtroStatus.value = status
            
            try {
                val bases = if (status != null) {
                    baseRepository.getBasesPorStatus(status)
                } else {
                    baseRepository.getAllBases()
                }
                _bases.value = bases
                println("✅ BaseApprovalViewModel: ${bases.size} bases carregadas (status: $status)")
            } catch (e: Exception) {
                _error.value = "Erro ao carregar bases: ${e.message}"
                println("❌ BaseApprovalViewModel: Erro - ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Aprovar base
     */
    fun aprovarBase(baseId: String, superAdminId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val base = baseRepository.getBase(baseId)
                val success = baseRepository.aprovarBase(baseId, superAdminId)
                if (success) {
                    // ✅ Registrar ação no histórico
                    historicoRepository.registrarAcao(
                        AcaoHistorico(
                            tipo = "aprovacao",
                            baseId = baseId,
                            baseNome = base?.nome,
                            superAdminId = superAdminId,
                            descricao = "Transportadora '${base?.nome ?: baseId}' aprovada",
                            data = System.currentTimeMillis()
                        )
                    )
                    
                    // ✅ Invalidar cache de estatísticas
                    statsRepository.invalidateCache()
                    
                    _message.value = "Transportadora aprovada com sucesso"
                    // Recarregar bases
                    loadBasesPorStatus(_filtroStatus.value)
                } else {
                    _error.value = "Erro ao aprovar transportadora"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao aprovar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Rejeitar base (exclui completamente)
     */
    fun rejeitarBase(baseId: String, superAdminId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // ✅ PROTEÇÃO: Não permitir rejeitar a base do superadmin
                if (baseId == "super_admin_base") {
                    _error.value = "Não é possível rejeitar a base do sistema"
                    return@launch
                }
                
                val base = baseRepository.getBase(baseId)
                val baseNome = base?.nome
                val success = baseRepository.rejeitarBase(baseId, superAdminId)
                if (success) {
                    // ✅ Registrar ação no histórico
                    historicoRepository.registrarAcao(
                        AcaoHistorico(
                            tipo = "rejeicao",
                            baseId = baseId,
                            baseNome = baseNome,
                            superAdminId = superAdminId,
                            descricao = "Transportadora '${baseNome ?: baseId}' rejeitada e excluída",
                            data = System.currentTimeMillis()
                        )
                    )
                    
                    // ✅ Invalidar cache de estatísticas
                    statsRepository.invalidateCache()
                    
                    // Remover sessões locais relacionadas à base excluída
                    val context = getApplication<Application>().applicationContext
                    val sessionManager = SessionManager(context)
                    sessionManager.removeSessionsByBaseId(baseId)
                    println("✅ BaseApprovalViewModel: Sessões locais removidas para base $baseId")
                    
                    _message.value = "Transportadora rejeitada e excluída"
                    // Recarregar bases
                    loadBasesPorStatus(_filtroStatus.value)
                } else {
                    _error.value = "Erro ao rejeitar transportadora"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao rejeitar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Deletar base
     */
    fun deletarBase(baseId: String, superAdminId: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // ✅ PROTEÇÃO: Não permitir deletar a base do superadmin
                if (baseId == "super_admin_base") {
                    _error.value = "Não é possível excluir a base do sistema"
                    return@launch
                }
                
                val base = baseRepository.getBase(baseId)
                val baseNome = base?.nome
                val success = baseRepository.deleteBase(baseId)
                if (success) {
                    // ✅ Registrar ação no histórico
                    historicoRepository.registrarAcao(
                        AcaoHistorico(
                            tipo = "rejeicao", // Deletar é similar a rejeitar
                            baseId = baseId,
                            baseNome = baseNome,
                            superAdminId = superAdminId,
                            descricao = "Transportadora '${baseNome ?: baseId}' excluída",
                            data = System.currentTimeMillis()
                        )
                    )
                    
                    // ✅ Invalidar cache de estatísticas
                    statsRepository.invalidateCache()
                    
                    // Remover sessões locais relacionadas à base excluída
                    val context = getApplication<Application>().applicationContext
                    val sessionManager = SessionManager(context)
                    sessionManager.removeSessionsByBaseId(baseId)
                    println("✅ BaseApprovalViewModel: Sessões locais removidas para base $baseId")
                    
                    _message.value = "Transportadora excluída com sucesso"
                    // Recarregar bases
                    loadBases()
                } else {
                    _error.value = "Erro ao excluir transportadora"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao excluir: ${e.message}"
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
}

