package com.controleescalas.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.models.Devolucao
import com.controleescalas.app.data.repositories.DevolucaoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class MotoristaComDevolucoes(
    val motoristaId: String,
    val motoristaNome: String,
    val totalDevolucoes: Int
)

class DevolucaoViewModel : ViewModel() {
    private val repository = DevolucaoRepository()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    private val _minhasDevolucoes = MutableStateFlow<List<Devolucao>>(emptyList())
    val minhasDevolucoes: StateFlow<List<Devolucao>> = _minhasDevolucoes.asStateFlow()
    
    private val _motoristasComDevolucoes = MutableStateFlow<List<MotoristaComDevolucoes>>(emptyList())
    val motoristasComDevolucoes: StateFlow<List<MotoristaComDevolucoes>> = _motoristasComDevolucoes.asStateFlow()
    
    private val _devolucoesMotorista = MutableStateFlow<List<Devolucao>>(emptyList())
    val devolucoesMotorista: StateFlow<List<Devolucao>> = _devolucoesMotorista.asStateFlow()
    
    private val _contagemPorDia = MutableStateFlow<Map<String, Int>>(emptyMap())
    val contagemPorDia: StateFlow<Map<String, Int>> = _contagemPorDia.asStateFlow()
    
    /**
     * Validar formato do ID do pacote (11 dígitos numéricos)
     */
    fun validarIdPacote(idPacote: String): Pair<Boolean, String?> {
        if (idPacote.isBlank()) {
            return false to "ID do pacote é obrigatório"
        }
        
        if (!idPacote.matches(Regex("^\\d{11}$"))) {
            return false to "ID do pacote deve ter exatamente 11 dígitos numéricos"
        }
        
        return true to null
    }
    
    /**
     * Registrar uma nova devolução
     */
    fun registrarDevolucao(
        baseId: String,
        idPacote: String,
        motoristaId: String,
        motoristaNome: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Validar formato do ID
                val (valido, mensagemErro) = validarIdPacote(idPacote)
                if (!valido) {
                    _error.value = mensagemErro
                    return@launch
                }
                
                // Obter data/hora atual
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val agora = Date()
                val data = dateFormat.format(agora)
                val hora = timeFormat.format(agora)
                
                // Registrar no Firebase
                val sucesso = repository.registrarDevolucao(
                    baseId = baseId,
                    idPacote = idPacote,
                    motoristaId = motoristaId,
                    motoristaNome = motoristaNome,
                    data = data,
                    hora = hora
                )
                
                if (sucesso) {
                    _message.value = "Devolução registrada com sucesso!"
                    // Recarregar devoluções do motorista (sem filtros para mostrar todas)
                    carregarMinhasDevolucoesComFiltros(baseId, motoristaId, null, null)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erro ao registrar devolução"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Registrar múltiplas devoluções em lote
     */
    fun registrarDevolucoesEmLote(
        baseId: String,
        idPacotes: List<String>,
        motoristaId: String,
        motoristaNome: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Validar todos os IDs
                val idsInvalidos = mutableListOf<String>()
                idPacotes.forEach { idPacote ->
                    val (valido, mensagemErro) = validarIdPacote(idPacote)
                    if (!valido) {
                        idsInvalidos.add("$idPacote: $mensagemErro")
                    }
                }
                
                if (idsInvalidos.isNotEmpty()) {
                    _error.value = "IDs inválidos:\n${idsInvalidos.joinToString("\n")}"
                    return@launch
                }
                
                // Obter data/hora atual
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val agora = Date()
                val data = dateFormat.format(agora)
                val hora = timeFormat.format(agora)
                
                // Registrar no Firebase
                val (sucessos, erros) = repository.registrarDevolucoesEmLote(
                    baseId = baseId,
                    idPacotes = idPacotes,
                    motoristaId = motoristaId,
                    motoristaNome = motoristaNome,
                    data = data,
                    hora = hora
                )
                
                if (sucessos > 0) {
                    val mensagem = if (erros.isEmpty()) {
                        "$sucessos ${if (sucessos == 1) "pacote registrado" else "pacotes registrados"} com sucesso!"
                    } else {
                        "$sucessos ${if (sucessos == 1) "pacote registrado" else "pacotes registrados"}, ${erros.size} ${if (erros.size == 1) "erro" else "erros"}"
                    }
                    _message.value = mensagem
                    // Recarregar devoluções
                    carregarMinhasDevolucoesComFiltros(baseId, motoristaId, null, null)
                } else {
                    _error.value = "Nenhum pacote foi registrado. Erros:\n${erros.joinToString("\n")}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erro ao registrar devoluções"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carregar devoluções do motorista
     */
    fun carregarMinhasDevolucoes(baseId: String, motoristaId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val devolucoes = repository.buscarDevolucoesMotorista(baseId, motoristaId)
                _minhasDevolucoes.value = devolucoes.map { mapToDevolucao(it) }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar devoluções: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carregar devoluções do motorista com filtros
     */
    fun carregarMinhasDevolucoesComFiltros(
        baseId: String,
        motoristaId: String,
        filtroData: String? = null, // "hoje", "semana", "mes"
        idPacoteFiltro: String? = null
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Calcular timestamps para filtro de data
                val calendar = Calendar.getInstance()
                val dataInicio: Long?
                val dataFim: Long?
                
                when (filtroData) {
                    "hoje" -> {
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        dataInicio = calendar.timeInMillis
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        dataFim = calendar.timeInMillis
                    }
                    "semana" -> {
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        dataInicio = calendar.timeInMillis
                        dataFim = System.currentTimeMillis()
                    }
                    "mes" -> {
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        dataInicio = calendar.timeInMillis
                        dataFim = System.currentTimeMillis()
                    }
                    else -> {
                        dataInicio = null
                        dataFim = null
                    }
                }
                
                val devolucoes = repository.buscarDevolucoesMotoristaComFiltros(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    dataInicio = dataInicio,
                    dataFim = dataFim,
                    idPacoteFiltro = idPacoteFiltro
                )
                
                _minhasDevolucoes.value = devolucoes.map { mapToDevolucao(it) }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar devoluções: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carregar motoristas com devoluções (para admin)
     */
    fun carregarMotoristasComDevolucoes(baseId: String, nomeFiltro: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val motoristas = repository.buscarMotoristasComDevolucoes(baseId, nomeFiltro)
                
                // Buscar contagem de devoluções para cada motorista
                val motoristasComContagem = motoristas.map { (motoristaId, motoristaNome) ->
                    val total = repository.contarDevolucoesMotorista(baseId, motoristaId)
                    MotoristaComDevolucoes(motoristaId, motoristaNome, total)
                }
                
                _motoristasComDevolucoes.value = motoristasComContagem.sortedByDescending { it.totalDevolucoes }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar motoristas: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carregar devoluções de um motorista específico (para admin)
     */
    fun carregarDevolucoesMotorista(
        baseId: String,
        motoristaId: String,
        filtroData: String? = null, // "hoje", "semana", "mes"
        idPacoteFiltro: String? = null
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Calcular timestamps para filtro de data
                val calendar = Calendar.getInstance()
                val dataInicio: Long?
                val dataFim: Long?
                
                when (filtroData) {
                    "hoje" -> {
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        dataInicio = calendar.timeInMillis
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        dataFim = calendar.timeInMillis
                    }
                    "semana" -> {
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        dataInicio = calendar.timeInMillis
                        dataFim = System.currentTimeMillis()
                    }
                    "mes" -> {
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        dataInicio = calendar.timeInMillis
                        dataFim = System.currentTimeMillis()
                    }
                    else -> {
                        dataInicio = null
                        dataFim = null
                    }
                }
                
                val devolucoes = repository.buscarDevolucoesMotoristaComFiltros(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    dataInicio = dataInicio,
                    dataFim = dataFim,
                    idPacoteFiltro = idPacoteFiltro
                )
                
                _devolucoesMotorista.value = devolucoes.map { mapToDevolucao(it) }
                
                // Carregar contagem por dia
                val contagem = repository.contarDevolucoesPorDia(baseId, motoristaId)
                _contagemPorDia.value = contagem
            } catch (e: Exception) {
                _error.value = "Erro ao carregar devoluções: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Excluir uma devolução
     */
    fun excluirDevolucao(
        baseId: String,
        devolucaoId: String,
        motoristaId: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                repository.excluirDevolucao(baseId, devolucaoId, motoristaId)
                
                _message.value = "Devolução excluída com sucesso!"
                // Recarregar devoluções (sem filtros para mostrar todas)
                carregarMinhasDevolucoesComFiltros(baseId, motoristaId, null, null)
            } catch (e: Exception) {
                _error.value = e.message ?: "Erro ao excluir devolução"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Limpar mensagens
     */
    fun limparMensagem() {
        _message.value = null
        _error.value = null
    }
    
    /**
     * Converter Map para Devolucao
     * Suporta tanto o formato antigo (idPacote) quanto o novo (idsPacotes)
     */
    private fun mapToDevolucao(map: Map<String, Any>): Devolucao {
        // Tentar obter idsPacotes (formato novo)
        val idsPacotes = when {
            map["idsPacotes"] is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                (map["idsPacotes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }
            else -> emptyList()
        }
        
        // Se não tiver idsPacotes, tentar idPacote (formato antigo para compatibilidade)
        val idsPacotesFinal = if (idsPacotes.isEmpty()) {
            val idPacote = map["idPacote"] as? String
            if (idPacote != null && idPacote.isNotBlank()) {
                listOf(idPacote)
            } else {
                emptyList()
            }
        } else {
            idsPacotes
        }
        
        val devolucao = Devolucao(
            id = map["id"] as? String ?: "",
            idsPacotes = idsPacotesFinal,
            motoristaId = map["motoristaId"] as? String ?: "",
            motoristaNome = map["motoristaNome"] as? String ?: "",
            baseId = map["baseId"] as? String ?: "",
            data = map["data"] as? String ?: "",
            hora = map["hora"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
        
        println("🔍 DevolucaoViewModel: Convertendo Map para Devolucao - ID: ${devolucao.id}, IDs Pacotes: ${devolucao.idsPacotes.size} - ${devolucao.idsPacotes.joinToString(", ")}")
        
        return devolucao
    }
}

