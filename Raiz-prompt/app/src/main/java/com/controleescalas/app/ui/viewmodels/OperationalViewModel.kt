package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.StatusMotorista
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.data.models.toAdminMotoristaCardData
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.controleescalas.app.data.NetworkUtils
import com.controleescalas.app.data.RetryUtils
import com.controleescalas.app.data.NotificationApiService
import com.controleescalas.app.data.NotifyMotoristaWorker
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel para Dashboard Operacional
 * Gerencia operação em tempo real das ondas
 */
class OperationalViewModel(application: Application) : AndroidViewModel(application) {
    private val escalaRepository = EscalaRepository()
    private val motoristaRepository = MotoristaRepository()
    private val notificationService = com.controleescalas.app.data.NotificationService(application)
    private val notificationApiService = NotificationApiService()
    private val quinzenaViewModel = QuinzenaViewModel()
    
    private val _turnoAtual = MutableStateFlow("AM")
    val turnoAtual: StateFlow<String> = _turnoAtual.asStateFlow()
    
    private val _escalaAM = MutableStateFlow<Escala?>(null)
    private val _escalaPM = MutableStateFlow<Escala?>(null)
    
    // Cache local para evitar recarregamentos desnecessários
    private val escalasCache = mutableMapOf<String, Escala>()
    
    private val _ondas = MutableStateFlow<List<Onda>>(emptyList())
    val ondas: StateFlow<List<Onda>> = _ondas.asStateFlow()
    
    // Mapa de status dos motoristas em tempo real
    private val _motoristasStatus = MutableStateFlow<Map<String, StatusMotorista>>(emptyMap())
    val motoristasStatus: StateFlow<Map<String, StatusMotorista>> = _motoristasStatus.asStateFlow()
    
    // Lista de motoristas disponíveis para adicionar
    private val _motoristasDisponiveis = MutableStateFlow<List<com.controleescalas.app.data.models.AdminMotoristaCardData>>(emptyList())
    val motoristasDisponiveis: StateFlow<List<com.controleescalas.app.data.models.AdminMotoristaCardData>> = _motoristasDisponiveis.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    private var statusListener: ListenerRegistration? = null
    private var motoristasListener: ListenerRegistration? = null
    private var escalaListener: ListenerRegistration? = null
    private var currentBaseId: String = ""
    
    // Mapa de motoristas em tempo real
    private val _motoristasMap = MutableStateFlow<Map<String, com.controleescalas.app.data.models.Motorista>>(emptyMap())
    private val motoristasMap: StateFlow<Map<String, com.controleescalas.app.data.models.Motorista>> = _motoristasMap.asStateFlow()
    
    // Tempo médio de carregamento
    private val _averageLoadingTime = MutableStateFlow<Long?>(null)
    val averageLoadingTime: StateFlow<Long?> = _averageLoadingTime.asStateFlow()
    
    // Estado de conexão e cache
    private val _connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState(
            isOnline = true,
            isUsingCache = false,
            connectionType = NetworkUtils.ConnectionType.UNKNOWN,
            connectionMessage = "Verificando conexão...",
            lastSyncTime = null
        )
    )
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Última atualização de status
    private val _lastStatusUpdate = MutableStateFlow<Long?>(null)
    val lastStatusUpdate: StateFlow<Long?> = _lastStatusUpdate.asStateFlow()
    
    /**
     * Estado da conexão para feedback visual
     */
    data class ConnectionState(
        val isOnline: Boolean = true,
        val isUsingCache: Boolean = false,
        val connectionType: NetworkUtils.ConnectionType = NetworkUtils.ConnectionType.UNKNOWN,
        val connectionMessage: String = "",
        val lastSyncTime: Long? = null
    )
    
    fun loadData(baseId: String) {
        currentBaseId = baseId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                
                // Carregar ambas as escalas
                val escalaAM = escalaRepository.getEscalaByDateAndTurno(baseId, hoje, "AM")
                    ?: Escala(baseId = baseId, data = hoje, turno = "AM", ondas = emptyList())
                _escalaAM.value = escalaAM
                
                val escalaPM = escalaRepository.getEscalaByDateAndTurno(baseId, hoje, "PM")
                    ?: Escala(baseId = baseId, data = hoje, turno = "PM", ondas = emptyList())
                _escalaPM.value = escalaPM
                
                // Mostrar ondas do turno atual
                updateOndasForCurrentTurno()
                
                // Carregar motoristas disponíveis
                val motoristas = motoristaRepository.getMotoristas(baseId)
                _motoristasDisponiveis.value = motoristas
                
                // Iniciar listeners de tempo real
                startStatusListener(baseId)
                startMotoristasListener(baseId)
                startEscalaListener(baseId, hoje)
                
            } catch (e: Exception) {
                _error.value = "Erro ao carregar dados: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun changeTurno(novoTurno: String) {
        _turnoAtual.value = novoTurno
        updateOndasForCurrentTurno()
    }
    
    private fun updateOndasForCurrentTurno() {
        val ondasOriginais = when (_turnoAtual.value) {
            "AM" -> _escalaAM.value?.ondas ?: emptyList()
            "PM" -> _escalaPM.value?.ondas ?: emptyList()
            else -> emptyList()
        }
        
        // Aplicar ordenação inteligente:
        // 1. Separar ondas normais e dedicadas
        val (ondasNormais, ondasDedicadas) = ondasOriginais.partition { it.tipo != "DEDICADO" }
        
        // 2. Ordenar itens dentro de cada onda por vaga (01, 02, 03...)
        val ondasNormaisOrdenadas = ondasNormais.map { onda ->
            onda.copy(
                itens = onda.itens.sortedWith(compareBy(
                    { it.vaga.toIntOrNull() ?: Int.MAX_VALUE } // Ordenar numericamente por vaga (01, 02, 03...)
                ))
            )
        }
        
        val ondasDedicadasOrdenadas = ondasDedicadas.map { onda ->
            onda.copy(
                itens = onda.itens.sortedWith(compareBy(
                    { it.vaga.toIntOrNull() ?: Int.MAX_VALUE } // Ordenar numericamente por vaga (01, 02, 03...)
                ))
            )
        }
        
        // 3. Ondas normais primeiro, depois dedicadas
        _ondas.value = ondasNormaisOrdenadas + ondasDedicadasOrdenadas
    }
    
    /**
     * Chamar motorista para vaga
     * @param motorista O motorista a ser chamado
     * @param vaga Vaga atualizada (opcional). Se não fornecida, usa motorista.vaga
     * @param rota Rota atualizada (opcional). Se não fornecida, usa motorista.rota
     */
    fun callDriverToVaga(motorista: OndaItem, vaga: String? = null, rota: String? = null) {
        viewModelScope.launch {
            try {
                val vagaParaUsar = vaga ?: motorista.vaga
                val rotaParaUsar = rota ?: motorista.rota
                
                // 1. Atualizar Firestore primeiro (feedback imediato)
                val updateSuccess = motoristaRepository.updateStatusMotorista(
                    motoristaId = motorista.motoristaId,
                    baseId = currentBaseId,
                    estado = "CARREGANDO",
                    mensagem = "Vá para VAGA $vagaParaUsar",
                    vagaAtual = vagaParaUsar,
                    rotaAtual = rotaParaUsar
                )
                
                if (updateSuccess) {
                    _message.value = "${motorista.nome} chamado para vaga $vagaParaUsar"
                    Log.d("OperationalViewModel", "✅ Status atualizado para ${motorista.nome}")
                } else {
                    _error.value = "Erro ao chamar motorista"
                    return@launch
                }
                
                // 2. Enviar push na hora (backend Python) para funcionar mesmo com app do motorista fechado
                val title = "🚚 Chamada para Carregamento"
                val body = "Subir agora para a VAGA $vagaParaUsar${if (rotaParaUsar.isNotBlank()) " com rota $rotaParaUsar" else ""}"
                val data = mapOf(
                    "tipo" to "chamada",
                    "vaga" to vagaParaUsar,
                    "rota" to rotaParaUsar
                )
                val (sent, _) = notificationApiService.notifyMotorista(
                    baseId = currentBaseId,
                    motoristaId = motorista.motoristaId,
                    title = title,
                    body = body,
                    data = data
                )
                if (!sent) {
                    NotifyMotoristaWorker.enqueue(
                        context = getApplication(),
                        baseId = currentBaseId,
                        motoristaId = motorista.motoristaId,
                        title = title,
                        body = body,
                        data = data
                    )
                    Log.d("OperationalViewModel", "Notificação enfileirada para retry em background")
                }
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "❌ Erro ao chamar motorista: ${e.message}", e)
                _error.value = "Erro ao chamar motorista: ${e.message}"
            }
        }
    }
    
    /**
     * Marcar carga como concluída
     */
    fun completeDriverLoad(motorista: OndaItem) {
        viewModelScope.launch {
            try {
                // Buscar o status atual para pegar o timestamp de início
                val statusAtual = _motoristasStatus.value[motorista.motoristaId]
                val inicioCarregamento = statusAtual?.inicioCarregamento
                
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motorista.motoristaId,
                    baseId = currentBaseId,
                    estado = "CONCLUIDO",
                    mensagem = "Carga concluída",
                    inicioCarregamento = inicioCarregamento,
                    fimCarregamento = System.currentTimeMillis()
                )
                
                if (success) {
                    // Notificar admin sobre conclusão
                    notificationService.sendAdminNotification(
                        motoristaNome = motorista.nome,
                        acao = "concluiu o carregamento"
                    )
                    
                    // Calcular tempo de carregamento
                    if (inicioCarregamento != null) {
                        val tempoMinutos = (System.currentTimeMillis() - inicioCarregamento) / (1000 * 60)
                        _message.value = "${motorista.nome} concluiu a carga em ${tempoMinutos}min"
                    } else {
                        _message.value = "${motorista.nome} concluiu a carga"
                    }
                } else {
                    _error.value = "Erro ao atualizar status"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar status: ${e.message}"
            }
        }
    }
    
    /**
     * Chamar motorista para estacionamento
     */
    /**
     * Marcar motorista como concluído (admin)
     */
    fun marcarMotoristaComoConcluido(motorista: OndaItem) {
        viewModelScope.launch {
            try {
                val statusAtual = _motoristasStatus.value[motorista.motoristaId]
                val inicioCarregamento = statusAtual?.inicioCarregamento
                
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motorista.motoristaId,
                    baseId = currentBaseId,
                    estado = "CONCLUIDO",
                    mensagem = "Carregamento concluído pelo admin",
                    inicioCarregamento = inicioCarregamento,
                    fimCarregamento = System.currentTimeMillis()
                )
                
                if (success) {
                    _message.value = "${motorista.nome} marcado como concluído"
                    Log.d("OperationalViewModel", "✅ Motorista ${motorista.nome} marcado como concluído pelo admin")
                    
                    // Incrementar dia trabalhado na quinzena
                    val dataAtual = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    try {
                        quinzenaViewModel.incrementarDiaTrabalhado(
                            baseId = currentBaseId,
                            motoristaId = motorista.motoristaId,
                            motoristaNome = motorista.nome,
                            data = dataAtual
                        )
                        Log.d("OperationalViewModel", "✅ Dia trabalhado incrementado na quinzena para ${motorista.nome}")
                    } catch (e: Exception) {
                        Log.e("OperationalViewModel", "❌ Erro ao incrementar quinzena: ${e.message}", e)
                        // Não mostrar erro ao usuário, apenas log
                    }
                } else {
                    _error.value = "Erro ao marcar como concluído"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao marcar como concluído: ${e.message}"
                Log.e("OperationalViewModel", "❌ Erro ao marcar motorista como concluído: ${e.message}", e)
            }
        }
    }
    
    /**
     * Resetar status do motorista para A_CAMINHO (admin)
     */
    fun resetarStatusMotorista(motorista: OndaItem) {
        viewModelScope.launch {
            try {
                // ✅ Usar retry com backoff para operações de escrita
                val success = RetryUtils.retryWithBackoff(maxRetries = 3) {
                    motoristaRepository.updateStatusMotorista(
                        motoristaId = motorista.motoristaId,
                        baseId = currentBaseId,
                        estado = "A_CAMINHO",
                        mensagem = "Status resetado. Continue aguardando instruções."
                    )
                }
                
                if (success) {
                    _message.value = "Status de ${motorista.nome} resetado para A_CAMINHO"
                    Log.d("OperationalViewModel", "✅ Status de ${motorista.nome} resetado pelo admin")
                } else {
                    _error.value = "Erro ao resetar status"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao resetar status: ${e.message}"
                Log.e("OperationalViewModel", "❌ Erro ao resetar status do motorista: ${e.message}", e)
            }
        }
    }
    
    fun callDriverToParking(motorista: OndaItem) {
        viewModelScope.launch {
            try {
                // 1. Atualizar Firestore primeiro
                val updateSuccess = motoristaRepository.updateStatusMotorista(
                    motoristaId = motorista.motoristaId,
                    baseId = currentBaseId,
                    estado = "IR_ESTACIONAMENTO",
                    mensagem = "Vá para o ESTACIONAMENTO e aguarde"
                )
                
                if (updateSuccess) {
                    _message.value = "${motorista.nome} chamado para estacionamento"
                    Log.d("OperationalViewModel", "✅ Status atualizado para ${motorista.nome}")
                } else {
                    _error.value = "Erro ao chamar motorista"
                    return@launch
                }
                
                // 2. Enviar push na hora (backend Python) para funcionar com app do motorista fechado
                val title = "🅿️ Chamada para Estacionamento"
                val body = "Vá para o ESTACIONAMENTO e aguarde"
                val data = mapOf(
                    "tipo" to "chamada_estacionamento",
                    "estado" to "IR_ESTACIONAMENTO"
                )
                val (sent, _) = notificationApiService.notifyMotorista(
                    baseId = currentBaseId,
                    motoristaId = motorista.motoristaId,
                    title = title,
                    body = body,
                    data = data
                )
                if (!sent) {
                    NotifyMotoristaWorker.enqueue(
                        context = getApplication(),
                        baseId = currentBaseId,
                        motoristaId = motorista.motoristaId,
                        title = title,
                        body = body,
                        data = data
                    )
                    Log.d("OperationalViewModel", "Notificação estacionamento enfileirada para retry")
                }
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "❌ Erro ao chamar motorista para estacionamento: ${e.message}", e)
                _error.value = "Erro ao chamar motorista: ${e.message}"
            }
        }
    }
    
    /**
     * Chamar motorista do estacionamento para vaga
     * @param motorista O motorista a ser chamado
     * @param vaga Vaga atualizada (opcional). Se não fornecida, usa motorista.vaga
     * @param rota Rota atualizada (opcional). Se não fornecida, usa motorista.rota
     */
    fun callDriverFromParkingToVaga(motorista: OndaItem, vaga: String? = null, rota: String? = null) {
        viewModelScope.launch {
            try {
                // Usar vaga/rota fornecidas ou valores do objeto motorista
                val vagaParaUsar = vaga ?: motorista.vaga
                val rotaParaUsar = rota ?: motorista.rota
                
                // ✅ CORREÇÃO: Chamar Cloud Function para enviar notificação push
                // Isso garante que a notificação funcione mesmo com o app fechado
                val functions = FirebaseFunctions.getInstance("southamerica-east1")
                val auth = FirebaseAuth.getInstance()
                
                if (auth.currentUser == null) {
                    _error.value = "Usuário não autenticado"
                    return@launch
                }
                
                val data = hashMapOf(
                    "baseId" to currentBaseId,
                    "motoristaId" to motorista.motoristaId,
                    "vaga" to vagaParaUsar,
                    "rota" to rotaParaUsar
                )
                
                // Chamar Cloud Function que atualiza status E envia notificação push
                val result = functions.getHttpsCallable("chamarMotoristaCarregamento")
                    .call(data)
                    .await()
                
                Log.d("OperationalViewModel", "✅ Motorista chamado via Cloud Function: ${motorista.nome}")
                _message.value = "${motorista.nome} chamado para vaga $vagaParaUsar"
                
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "❌ Erro ao chamar motorista via Cloud Function: ${e.message}", e)
                // Fallback: tentar atualizar diretamente no Firestore (sem notificação push)
                try {
                    val success = motoristaRepository.updateStatusMotorista(
                        motoristaId = motorista.motoristaId,
                        baseId = currentBaseId,
                        estado = "CARREGANDO",
                        mensagem = "Vá para VAGA ${vaga ?: motorista.vaga}",
                        vagaAtual = vaga ?: motorista.vaga,
                        rotaAtual = rota ?: motorista.rota
                    )
                    if (success) {
                        _message.value = "${motorista.nome} chamado para vaga ${vaga ?: motorista.vaga} (sem notificação push)"
                        Log.w("OperationalViewModel", "⚠️ Usado fallback: atualização direta sem notificação push")
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
     * Resetar status do motorista para A_CAMINHO
     */
    fun resetDriverStatus(motorista: OndaItem) {
        viewModelScope.launch {
            try {
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motorista.motoristaId,
                    baseId = currentBaseId,
                    estado = "A_CAMINHO",
                    mensagem = "A caminho do galpão"
                )
                
                if (success) {
                    _message.value = "${motorista.nome} voltou para A CAMINHO"
                } else {
                    _error.value = "Erro ao resetar status"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao resetar status: ${e.message}"
            }
        }
    }
    
    /**
     * Atualiza vaga/rota/sacas de um motorista já escalado (por ID). Usado pelo assistente (update_in_scale).
     * Só altera os campos informados (não nulos); mantém os atuais para os não informados.
     */
    fun updateMotoristaInOndaByDetails(ondaIndex: Int, motoristaId: String, novaVaga: String?, novaRota: String?, novasSacas: Int?) {
        viewModelScope.launch {
            val turno = _turnoAtual.value
            val currentEscala = when (turno) {
                "AM" -> _escalaAM.value
                "PM" -> _escalaPM.value
                else -> null
            } ?: return@launch
            val onda = currentEscala.ondas.getOrNull(ondaIndex) ?: return@launch
            val item = onda.itens.find { it.motoristaId == motoristaId } ?: return@launch
            val vaga = novaVaga?.takeIf { it.isNotBlank() } ?: item.vaga
            val rota = novaRota?.takeIf { it.isNotBlank() } ?: item.rota
            val sacas = novasSacas ?: item.sacas
            updateDriverInOnda(ondaIndex, item, vaga, rota, sacas)
        }
    }

    /**
     * Atualizar vaga, rota e sacas de um motorista em uma onda
     */
    fun updateDriverInOnda(ondaIndex: Int, motorista: OndaItem, novaVaga: String, novaRota: String, novasSacas: Int? = null) {
        viewModelScope.launch {
            try {
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                val onda = currentEscala.ondas.getOrNull(ondaIndex) ?: return@launch
                
                // Encontrar o índice do motorista na onda
                val motoristaIndex = onda.itens.indexOfFirst { it.motoristaId == motorista.motoristaId }
                if (motoristaIndex == -1) return@launch
                
                // Atualizar o motorista
                val motoristaAtualizado = onda.itens[motoristaIndex].copy(
                    vaga = novaVaga,
                    rota = novaRota,
                    sacas = novasSacas
                )
                
                // Atualizar a lista de motoristas na onda
                val itensAtualizados = onda.itens.toMutableList()
                itensAtualizados[motoristaIndex] = motoristaAtualizado
                
                val ondaAtualizada = onda.copy(itens = itensAtualizados)
                val ondasAtualizadas = currentEscala.ondas.toMutableList()
                ondasAtualizadas[ondaIndex] = ondaAtualizada
                
                // ✅ CRÍTICO: Garantir que a escala sempre tenha a data atual
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val escalaAtualizada = currentEscala.copy(ondas = ondasAtualizadas, data = hoje)
                
                // Salvar
                when (turno) {
                    "AM" -> {
                        _escalaAM.value = escalaAtualizada
                        escalasCache["${escalaAtualizada.data}_AM"] = escalaAtualizada
                    }
                    "PM" -> {
                        _escalaPM.value = escalaAtualizada
                        escalasCache["${escalaAtualizada.data}_PM"] = escalaAtualizada
                    }
                }
                
                escalaRepository.saveEscala(currentBaseId, escalaAtualizada)
                _message.value = "Dados atualizados!"
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar: ${e.message}"
            }
        }
    }
    
    /**
     * Adicionar nova onda ao turno atual
     */
    fun addOnda() {
        val turno = _turnoAtual.value
        val currentEscala = when (turno) {
            "AM" -> _escalaAM.value
            "PM" -> _escalaPM.value
            else -> null
        } ?: return
        
        val numeroOnda = currentEscala.ondas.size + 1
        
        // ✅ NOVO: Calcular horário automaticamente se houver última onda com horário
        val ultimaOndaComHorario = currentEscala.ondas.lastOrNull { it.horario.isNotBlank() && it.horario.matches(Regex("\\d{2}:\\d{2}")) }
        val horarioPadrao = if (ultimaOndaComHorario != null) {
            // Calcular +20 minutos da última onda com horário
            try {
                val partes = ultimaOndaComHorario.horario.split(":")
                val hora = partes[0].toInt()
                val minuto = partes[1].toInt()
                var minutosTotais = hora * 60 + minuto + 20
                val novaHora = minutosTotais / 60
                val novoMinuto = minutosTotais % 60
                
                if (novaHora >= 24) {
                    "" // Se ultrapassar 23:59, deixar indefinido
                } else {
                    String.format("%02d:%02d", novaHora, novoMinuto)
                }
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro ao calcular horário para nova onda: ${e.message}")
                "" // Em caso de erro, deixar indefinido
            }
        } else {
            "" // Vazio = indefinido (primeira onda ou nenhuma tem horário)
        }
        
        val novaOnda = Onda(
            nome = "${numeroOnda}ª ONDA",
            horario = horarioPadrao, // Calculado automaticamente se possível
            itens = emptyList()
        )
        
        val ondasAtualizadas = (currentEscala.ondas + novaOnda).toMutableList()
        val updatedEscala = currentEscala.copy(
            ondas = ondasAtualizadas
        )
        
        if (horarioPadrao.isNotBlank()) {
            Log.d("OperationalViewModel", "✅ Nova onda criada com horário automático: $horarioPadrao (baseado na última onda: ${ultimaOndaComHorario?.horario})")
        } else {
            Log.d("OperationalViewModel", "✅ Nova onda criada sem horário (indefinido)")
        }
        
        when (turno) {
            "AM" -> _escalaAM.value = updatedEscala
            "PM" -> _escalaPM.value = updatedEscala
        }
        
        updateOndasForCurrentTurno()
        saveCurrentEscala()
        
        Log.d("OperationalViewModel", "✅ Nova onda adicionada. Total de ondas: ${ondasAtualizadas.size}")
    }
    
    /**
     * Atualizar dados de uma onda (nome, horário, tipo)
     */
    fun updateOnda(ondaIndex: Int, novoNome: String, novoHorario: String, novoTipo: String) {
        viewModelScope.launch {
            try {
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                val ondas = currentEscala.ondas.toMutableList()
                if (ondaIndex !in ondas.indices) return@launch
                
                val ondaAtualizada = ondas[ondaIndex].copy(
                    nome = novoNome,
                    horario = novoHorario,
                    tipo = novoTipo
                )
                
                ondas[ondaIndex] = ondaAtualizada
                
                // ✅ NOVA LÓGICA: Se horário foi definido, calcular próximas ondas automaticamente
                if (novoHorario.isNotBlank()) {
                    Log.d("OperationalViewModel", "🕐 updateOnda: Horário '$novoHorario' definido na onda $ondaIndex, calculando próximas ondas...")
                    calcularEAtualizarHorariosSeguintes(ondas, ondaIndex, novoHorario)
                    Log.d("OperationalViewModel", "✅ updateOnda: Cálculo concluído. Total de ondas: ${ondas.size}")
                } else {
                    Log.d("OperationalViewModel", "⚠️ updateOnda: Horário vazio, não calculando próximas ondas")
                }
                
                // ✅ CRÍTICO: Garantir que a escala sempre tenha a data atual
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val escalaAtualizada = currentEscala.copy(ondas = ondas, data = hoje)
                
                when (turno) {
                    "AM" -> {
                        _escalaAM.value = escalaAtualizada
                        escalasCache["${escalaAtualizada.data}_AM"] = escalaAtualizada
                    }
                    "PM" -> {
                        _escalaPM.value = escalaAtualizada
                        escalasCache["${escalaAtualizada.data}_PM"] = escalaAtualizada
                    }
                }
                
                updateOndasForCurrentTurno()
                escalaRepository.saveEscala(currentBaseId, escalaAtualizada)
                _message.value = "Onda atualizada com sucesso!"
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar onda: ${e.message}"
            }
        }
    }
    
    /**
     * Calcular e atualizar horários das ondas seguintes automaticamente
     * Quando um horário é definido, as ondas seguintes recebem +20 minutos
     */
    private fun calcularEAtualizarHorariosSeguintes(
        ondas: MutableList<Onda>,
        ondaIndexComHorario: Int,
        horarioBase: String
    ) {
        try {
            Log.d("OperationalViewModel", "🔍 calcularEAtualizarHorariosSeguintes: Iniciando...")
            Log.d("OperationalViewModel", "   - Onda com horário: índice $ondaIndexComHorario")
            Log.d("OperationalViewModel", "   - Horário base: $horarioBase")
            Log.d("OperationalViewModel", "   - Total de ondas: ${ondas.size}")
            
            // Validar formato do horário (HH:MM)
            if (!horarioBase.matches(Regex("\\d{2}:\\d{2}"))) {
                Log.w("OperationalViewModel", "❌ Formato de horário inválido: $horarioBase")
                return
            }
            
            // Converter horário base para minutos totais
            val partes = horarioBase.split(":")
            val hora = partes[0].toInt()
            val minuto = partes[1].toInt()
            var minutosTotais = hora * 60 + minuto
            
            Log.d("OperationalViewModel", "🕐 Calculando horários a partir de $horarioBase (${minutosTotais} minutos)")
            Log.d("OperationalViewModel", "   - Índices das ondas seguintes: ${(ondaIndexComHorario + 1) until ondas.size}")
            
            // ✅ CORREÇÃO: Recalcular TODAS as ondas seguintes, não apenas as sem horário
            // Isso garante que quando uma onda é editada, todas as seguintes são atualizadas
            var ondasAtualizadas = 0
            for (i in (ondaIndexComHorario + 1) until ondas.size) {
                val horarioAntigo = ondas[i].horario
                minutosTotais += 20 // Adicionar 20 minutos
                val novaHora = minutosTotais / 60
                val novoMinuto = minutosTotais % 60
                
                // Garantir que não ultrapasse 23:59
                if (novaHora >= 24) {
                    Log.w("OperationalViewModel", "⚠️ Horário ultrapassou 23:59, parando cálculo")
                    break
                }
                
                val novoHorarioFormatado = String.format("%02d:%02d", novaHora, novoMinuto)
                ondas[i] = ondas[i].copy(horario = novoHorarioFormatado)
                ondasAtualizadas++
                
                Log.d("OperationalViewModel", "✅ Onda ${i + 1} (${ondas[i].nome}): '$horarioAntigo' → '$novoHorarioFormatado'")
            }
            
            Log.d("OperationalViewModel", "✅ calcularEAtualizarHorariosSeguintes: Concluído! $ondasAtualizadas ondas atualizadas")
        } catch (e: Exception) {
            Log.e("OperationalViewModel", "❌ Erro ao calcular horários seguintes: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Remover uma onda da escala
     */
    fun removeOnda(ondaIndex: Int) {
        viewModelScope.launch {
            try {
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                val ondas = currentEscala.ondas.toMutableList()
                if (ondaIndex !in ondas.indices) return@launch
                
                // Remover a onda da lista
                ondas.removeAt(ondaIndex)
                
                val updatedEscala = currentEscala.copy(ondas = ondas)
                
                when (turno) {
                    "AM" -> {
                        _escalaAM.value = updatedEscala
                        escalasCache["${updatedEscala.data}_AM"] = updatedEscala
                    }
                    "PM" -> {
                        _escalaPM.value = updatedEscala
                        escalasCache["${updatedEscala.data}_PM"] = updatedEscala
                    }
                }
                
                updateOndasForCurrentTurno()
                saveCurrentEscala()
                _message.value = "Onda removida com sucesso"
            } catch (e: Exception) {
                _error.value = "Erro ao remover onda: ${e.message}"
            }
        }
    }
    
    /**
     * Adicionar motorista a uma onda
     */
    fun addMotoristaToOnda(ondaIndex: Int, motorista: com.controleescalas.app.data.models.AdminMotoristaCardData) {
        viewModelScope.launch {
            try {
                // Obter turno e escala atual primeiro
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                // ✅ NOVO: Verificar se o motorista já está em alguma onda do turno ATUAL
                val ondaExistente = currentEscala.ondas.find { onda ->
                    onda.itens.any { it.motoristaId == motorista.id }
                }
                
                if (ondaExistente != null) {
                    val nomeOndaExistente = ondaExistente.nome
                    _error.value = "${motorista.nome} já está escalado na $nomeOndaExistente"
                    Log.w("OperationalViewModel", "Tentativa de escalar motorista ${motorista.nome} que já está na $nomeOndaExistente do turno $turno")
                    return@launch
                }
                
                val ondas = currentEscala.ondas.toMutableList()
                if (ondaIndex !in ondas.indices) return@launch
                
                val onda = ondas[ondaIndex]
                val novoItem = OndaItem(
                    motoristaId = motorista.id, // ✅ CORREÇÃO: Usar ID do documento, não telefone
                    nome = motorista.nome,
                    horario = onda.horario,
                    vaga = "",
                    rota = "",
                    modalidade = motorista.modalidade // Incluir modalidade
                )
                
                // Adicionar e ordenar itens por vaga (01, 02, 03...)
                val itensAtualizados = (onda.itens + novoItem).sortedWith(compareBy(
                    { it.vaga.toIntOrNull() ?: Int.MAX_VALUE } // Ordenar numericamente por vaga (01, 02, 03...)
                ))
                
                ondas[ondaIndex] = onda.copy(itens = itensAtualizados)
                // ✅ CRÍTICO: Garantir que a escala sempre tenha a data atual ao salvar
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val updatedEscala = currentEscala.copy(ondas = ondas, data = hoje)
                
                // ✅ DEBUG: Log para verificar a data antes de salvar
                Log.d("OperationalViewModel", "🔍 DEBUG addMotoristaToOnda: Data atual calculada: $hoje")
                Log.d("OperationalViewModel", "🔍 DEBUG addMotoristaToOnda: Data da escala antes: ${currentEscala.data}")
                Log.d("OperationalViewModel", "🔍 DEBUG addMotoristaToOnda: Data da escala depois: ${updatedEscala.data}")
                Log.d("OperationalViewModel", "🔍 DEBUG addMotoristaToOnda: Turno: ${updatedEscala.turno}")
                
                when (turno) {
                    "AM" -> _escalaAM.value = updatedEscala
                    "PM" -> _escalaPM.value = updatedEscala
                }
                
                // Log para debug
                Log.d("OperationalViewModel", "✅ Motorista ${motorista.nome} adicionado à onda $ondaIndex. Total de itens: ${itensAtualizados.size}")
                
                updateOndasForCurrentTurno()
                
                // ✅ NOVO: Resetar status do motorista ao adicionar à onda (garantir estado inicial)
                try {
                    motoristaRepository.updateStatusMotorista(
                        motoristaId = motorista.id,
                        baseId = currentBaseId,
                        estado = "A_CAMINHO", // Estado inicial quando escalado
                        mensagem = "Você está escalado! Siga para o galpão e aguarde instruções.",
                        vagaAtual = null,
                        rotaAtual = null,
                        inicioCarregamento = null,
                        fimCarregamento = null
                    )
                    Log.d("OperationalViewModel", "✅ Status do motorista ${motorista.nome} resetado para A_CAMINHO ao adicionar à onda")
                } catch (e: Exception) {
                    Log.e("OperationalViewModel", "❌ Erro ao resetar status do motorista: ${e.message}", e)
                    // Não bloquear a adição se houver erro ao resetar status
                }
                
                // ✅ CORREÇÃO: Salvar diretamente updatedEscala ao invés de reler o estado
                try {
                    // ✅ DEBUG: Log final antes de salvar
                    Log.d("OperationalViewModel", "💾 DEBUG addMotoristaToOnda: Salvando escala com data: ${updatedEscala.data}, turno: ${updatedEscala.turno}")
                    escalaRepository.saveEscala(currentBaseId, updatedEscala)
                    _message.value = "Motorista adicionado com sucesso"
                    Log.d("OperationalViewModel", "✅ Escala salva com ${updatedEscala.ondas[ondaIndex].itens.size} motoristas na onda $ondaIndex")
                } catch (saveException: Exception) {
                    _error.value = "Erro ao salvar: ${saveException.message}"
                    Log.e("OperationalViewModel", "❌ Erro ao salvar escala", saveException)
                }
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro ao validar/adicionar motorista: ${e.message}", e)
                _error.value = "Erro ao adicionar motorista: ${e.message}"
            }
        }
    }

    /**
     * Adicionar motorista à onda COM DETALHES (vaga, rota, sacas)
     */
    fun addMotoristaToOndaWithDetails(
        ondaIndex: Int,
        motorista: com.controleescalas.app.data.models.AdminMotoristaCardData,
        vaga: String,
        rota: String,
        sacas: Int?
    ) {
        viewModelScope.launch {
            try {
                // Obter turno e escala atual primeiro
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                // ✅ NOVO: Verificar se o motorista já está em alguma onda do turno ATUAL
                val ondaExistente = currentEscala.ondas.find { onda ->
                    onda.itens.any { it.motoristaId == motorista.id }
                }
                
                if (ondaExistente != null) {
                    val nomeOndaExistente = ondaExistente.nome
                    _error.value = "${motorista.nome} já está escalado na $nomeOndaExistente"
                    Log.w("OperationalViewModel", "Tentativa de escalar motorista ${motorista.nome} que já está na $nomeOndaExistente do turno $turno")
                    return@launch
                }
                
                val ondas = currentEscala.ondas.toMutableList()
                if (ondaIndex !in ondas.indices) return@launch
                
                val onda = ondas[ondaIndex]
                val novoItem = OndaItem(
                    motoristaId = motorista.id, // ✅ CORREÇÃO: Usar ID do documento, não telefone
                    nome = motorista.nome,
                    horario = onda.horario,
                    vaga = vaga,
                    rota = rota,
                    sacas = sacas,
                    modalidade = motorista.modalidade // Incluir modalidade
                )
                
                // Adicionar e ordenar itens por vaga (01, 02, 03...)
                val itensAtualizados = (onda.itens + novoItem).sortedWith(compareBy(
                    { it.vaga.toIntOrNull() ?: Int.MAX_VALUE } // Ordenar numericamente por vaga (01, 02, 03...)
                ))
                
                ondas[ondaIndex] = onda.copy(itens = itensAtualizados)
                // ✅ CRÍTICO: Garantir que a escala sempre tenha a data atual ao salvar
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val updatedEscala = currentEscala.copy(ondas = ondas, data = hoje)
                
                // ✅ DEBUG: Log para verificar a data antes de salvar
                Log.d("OperationalViewModel", "🔍 DEBUG: Data atual calculada: $hoje")
                Log.d("OperationalViewModel", "🔍 DEBUG: Data da escala antes: ${currentEscala.data}")
                Log.d("OperationalViewModel", "🔍 DEBUG: Data da escala depois: ${updatedEscala.data}")
                Log.d("OperationalViewModel", "🔍 DEBUG: Turno: ${updatedEscala.turno}")
                
                when (turno) {
                    "AM" -> _escalaAM.value = updatedEscala
                    "PM" -> _escalaPM.value = updatedEscala
                }
                
                // Log para debug
                Log.d("OperationalViewModel", "✅ Motorista ${motorista.nome} adicionado à onda $ondaIndex com vaga=$vaga, rota=$rota. Total de itens: ${itensAtualizados.size}")
                
                updateOndasForCurrentTurno()
                
                // ✅ NOVO: Resetar status do motorista ao adicionar à onda (garantir estado inicial)
                try {
                    motoristaRepository.updateStatusMotorista(
                        motoristaId = motorista.id,
                        baseId = currentBaseId,
                        estado = "A_CAMINHO", // Estado inicial quando escalado
                        mensagem = "Você está escalado! Siga para o galpão e aguarde instruções.",
                        vagaAtual = vaga.takeIf { it.isNotBlank() },
                        rotaAtual = rota.takeIf { it.isNotBlank() },
                        inicioCarregamento = null,
                        fimCarregamento = null
                    )
                    Log.d("OperationalViewModel", "✅ Status do motorista ${motorista.nome} resetado para A_CAMINHO ao adicionar à onda")
                } catch (e: Exception) {
                    Log.e("OperationalViewModel", "❌ Erro ao resetar status do motorista: ${e.message}", e)
                    // Não bloquear a adição se houver erro ao resetar status
                }
                
                // ✅ CORREÇÃO: Salvar diretamente updatedEscala ao invés de reler o estado
                try {
                    // ✅ DEBUG: Log final antes de salvar
                    Log.d("OperationalViewModel", "💾 DEBUG: Salvando escala com data: ${updatedEscala.data}, turno: ${updatedEscala.turno}")
                    escalaRepository.saveEscala(currentBaseId, updatedEscala)
                    _message.value = "Motorista adicionado com sucesso"
                    Log.d("OperationalViewModel", "✅ Escala salva com ${updatedEscala.ondas[ondaIndex].itens.size} motoristas na onda $ondaIndex")
                    // ✅ NOTA: Notificações serão enviadas apenas quando o botão "Notificar todos" for acionado
                } catch (saveException: Exception) {
                    _error.value = "Erro ao salvar: ${saveException.message}"
                    Log.e("OperationalViewModel", "❌ Erro ao salvar escala", saveException)
                }
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro ao validar/adicionar motorista: ${e.message}", e)
                _error.value = "Erro ao adicionar motorista: ${e.message}"
            }
        }
    }

    /**
     * Adiciona motorista à onda por motoristaId e nome (usado na importação por foto e pelo assistente).
     * @param sacas opcional (ex.: 4 sacas)
     */
    fun addMotoristaToOndaWithDetails(ondaIndex: Int, motoristaId: String, nome: String, vaga: String, rota: String, sacas: Int? = null) {
        viewModelScope.launch {
            try {
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch

                // Se já está escalado em alguma onda, apenas atualizar vaga/rota/sacas (não duplicar)
                for ((idx, o) in currentEscala.ondas.withIndex()) {
                    val existing = o.itens.find { it.motoristaId == motoristaId }
                    if (existing != null) {
                        updateMotoristaInOndaByDetails(idx, motoristaId, vaga, rota, sacas)
                        _message.value = "Dados do motorista atualizados"
                        return@launch
                    }
                }

                val ondas = currentEscala.ondas.toMutableList()
                while (ondaIndex >= ondas.size) {
                    val num = ondas.size + 1
                    ondas.add(Onda(nome = "${num}ª ONDA", horario = "", itens = emptyList()))
                }

                val onda = ondas[ondaIndex]
                val novoItem = OndaItem(
                    motoristaId = motoristaId,
                    nome = nome,
                    horario = onda.horario,
                    vaga = vaga,
                    rota = rota,
                    sacas = sacas,
                    modalidade = "FROTA"
                )
                val itensAtualizados = (onda.itens + novoItem).sortedWith(
                    compareBy { it.vaga.toIntOrNull() ?: Int.MAX_VALUE }
                )
                ondas[ondaIndex] = onda.copy(itens = itensAtualizados)
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val updatedEscala = currentEscala.copy(ondas = ondas, data = hoje)

                when (turno) {
                    "AM" -> _escalaAM.value = updatedEscala
                    "PM" -> _escalaPM.value = updatedEscala
                }
                updateOndasForCurrentTurno()
                escalaRepository.saveEscala(currentBaseId, updatedEscala)

                try {
                    motoristaRepository.updateStatusMotorista(
                        motoristaId = motoristaId,
                        baseId = currentBaseId,
                        estado = "A_CAMINHO",
                        mensagem = "Você está escalado!",
                        vagaAtual = vaga.takeIf { it.isNotBlank() },
                        rotaAtual = rota.takeIf { it.isNotBlank() },
                        inicioCarregamento = null,
                        fimCarregamento = null
                    )
                } catch (_: Exception) {}
                _message.value = "Motorista adicionado"
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro ao adicionar por foto: ${e.message}", e)
                _error.value = "Erro: ${e.message}"
            }
        }
    }

    /**
     * Garante que existam pelo menos N ondas no turno (usado na importação por foto).
     */
    fun ensureOndasCount(turno: String, count: Int) {
        viewModelScope.launch {
            val currentEscala = when (turno) {
                "AM" -> _escalaAM.value
                "PM" -> _escalaPM.value
                else -> return@launch
            } ?: return@launch

            var ondas = currentEscala.ondas.toMutableList()
            while (ondas.size < count) {
                val num = ondas.size + 1
                ondas.add(Onda(nome = "${num}ª ONDA", horario = "", itens = emptyList()))
            }
            val updatedEscala = currentEscala.copy(ondas = ondas)
            when (turno) {
                "AM" -> _escalaAM.value = updatedEscala
                "PM" -> _escalaPM.value = updatedEscala
            }
            updateOndasForCurrentTurno()
            escalaRepository.saveEscala(currentBaseId, updatedEscala)
        }
    }

    /**
     * Remover motorista de uma onda
     */
    fun removeMotoristaFromOnda(ondaIndex: Int, motoristaId: String) {
        viewModelScope.launch {
            try {
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                val ondas = currentEscala.ondas.toMutableList()
                if (ondaIndex !in ondas.indices) return@launch
                
                val onda = ondas[ondaIndex]
                val itensAtualizados = onda.itens.filter { it.motoristaId != motoristaId }
                
                ondas[ondaIndex] = onda.copy(itens = itensAtualizados)
                val updatedEscala = currentEscala.copy(ondas = ondas)
                
                when (turno) {
                    "AM" -> _escalaAM.value = updatedEscala
                    "PM" -> _escalaPM.value = updatedEscala
                }
                
                updateOndasForCurrentTurno()
                saveCurrentEscala()
                
                // ✅ NOVO: Resetar status do motorista quando removido da onda
                try {
                    motoristaRepository.updateStatusMotorista(
                        motoristaId = motoristaId,
                        baseId = currentBaseId,
                        estado = "A_CAMINHO", // Estado inicial
                        mensagem = "",
                        vagaAtual = null,
                        rotaAtual = null,
                        inicioCarregamento = null,
                        fimCarregamento = null
                    )
                    Log.d("OperationalViewModel", "✅ Status do motorista $motoristaId resetado para A_CAMINHO após remoção da onda")
                } catch (e: Exception) {
                    Log.e("OperationalViewModel", "❌ Erro ao resetar status do motorista: ${e.message}", e)
                    // Não bloquear a remoção se houver erro ao resetar status
                }
                
                _message.value = "Motorista removido da onda"
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro ao remover motorista da onda: ${e.message}", e)
                _error.value = "Erro ao remover motorista: ${e.message}"
            }
        }
    }
    
    /**
     * Garantir que a escala tenha a data atual antes de salvar
     */
    private fun ensureEscalaDataAtual(escala: Escala): Escala {
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return if (escala.data != hoje) {
            escala.copy(data = hoje)
        } else {
            escala
        }
    }
    
    /**
     * Salvar escala atual
     */
    fun saveCurrentEscala(showMessage: Boolean = true) {
        viewModelScope.launch {
            try {
                val escala = when (_turnoAtual.value) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                } ?: return@launch
                
                // ✅ CRÍTICO: Garantir que a escala sempre tenha a data atual
                val escalaComDataAtual = ensureEscalaDataAtual(escala)
                escalaRepository.saveEscala(currentBaseId, escalaComDataAtual)
                
                // ✅ NOVO: Só mostrar mensagem se showMessage for true
                if (showMessage) {
                    _message.value = "Escala salva com sucesso"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao salvar: ${e.message}"
            }
        }
    }
    
    /**
     * Listener em tempo real de status dos motoristas
     */
    private fun startStatusListener(baseId: String) {
        statusListener?.remove()
        
        // Atualizar estado de conexão
        updateConnectionState()
        
        statusListener = motoristaRepository.subscribeToDriverStatus(
            baseId = baseId,
            onUpdate = { statusMap: Map<String, StatusMotorista> ->
                _motoristasStatus.value = statusMap
                _lastStatusUpdate.value = System.currentTimeMillis()
                // Recalcular tempo médio quando status muda
                calculateAverageLoadingTime(statusMap)
                // Atualizar estado de conexão
                updateConnectionState()
            },
            onError = { error: Exception ->
                _error.value = "Erro ao monitorar status: ${error.message}"
                Log.e("OperationalViewModel", "❌ Erro no listener de status: ${error.message}")
                // Tentar reconectar após delay
                viewModelScope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (statusListener == null) {
                        Log.d("OperationalViewModel", "🔄 Tentando reconectar listener de status...")
                        startStatusListener(baseId)
                    }
                }
            }
        )
    }
    
    /**
     * Atualizar estado de conexão
     */
    private fun updateConnectionState() {
        val context = getApplication<Application>()
        val isOnline = NetworkUtils.isOnline(context)
        val connectionType = NetworkUtils.getConnectionType(context)
        val connectionMessage = NetworkUtils.getConnectionMessage(context)
        
        _connectionState.value = ConnectionState(
            isOnline = isOnline,
            isUsingCache = !isOnline || connectionType == NetworkUtils.ConnectionType.CELLULAR_2G,
            connectionType = connectionType,
            connectionMessage = connectionMessage,
            lastSyncTime = _lastStatusUpdate.value
        )
    }
    
    /**
     * Listener para mudanças nos dados dos motoristas (nome, modalidade, etc)
     */
    private fun startMotoristasListener(baseId: String) {
        motoristasListener?.remove()
        
        motoristasListener = com.controleescalas.app.data.FirebaseManager.firestore
            .collection("bases")
            .document(baseId)
            .collection("motoristas")
            .whereEqualTo("ativo", true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    _error.value = "Erro ao monitorar motoristas: ${error.message}"
                    return@addSnapshotListener
                }
                
                // ✅ Detectar se está usando cache
                val isFromCache = snapshot?.metadata?.isFromCache == true
                if (isFromCache) {
                    Log.d("OperationalViewModel", "📦 Dados de motoristas usando cache local")
                }
                
                if (snapshot != null) {
                    val motoristas = snapshot.documents.mapNotNull { doc ->
                        val motorista = doc.toObject(com.controleescalas.app.data.models.Motorista::class.java)
                        motorista?.copy(id = doc.id)
                    }
                    
                    // Atualizar mapa de motoristas (usar id como chave para corresponder ao motoristaId nas ondas)
                    _motoristasMap.value = motoristas.associateBy { it.id }
                    
                    // Atualizar lista de disponíveis
                    _motoristasDisponiveis.value = motoristas.map { it.toAdminMotoristaCardData() }
                    
                    // Sincronizar ondas com novos dados dos motoristas
                    syncOndasWithMotoristaData()
                    
                    // Atualizar estado de conexão
                    updateConnectionState()
                }
            }
    }
    
    /**
     * Listener para mudanças na escala (adição/remoção de motoristas das ondas)
     */
    private fun startEscalaListener(baseId: String, data: String) {
        escalaListener?.remove()
        
        val turnoAtual = _turnoAtual.value
        val escalaId = "${data}_${turnoAtual}"
        
        escalaListener = com.controleescalas.app.data.FirebaseManager.firestore
            .collection("bases")
            .document(baseId)
            .collection("escalas")
            .document(escalaId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    println("⚠️ Erro ao monitorar escala: ${error.message}")
                    Log.e("OperationalViewModel", "❌ Erro no listener de escala: ${error.message}")
                    return@addSnapshotListener
                }
                
                // ✅ Detectar se está usando cache
                val isFromCache = snapshot?.metadata?.isFromCache == true
                if (isFromCache) {
                    Log.d("OperationalViewModel", "📦 Dados de escala usando cache local")
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val escala = snapshot.toObject(Escala::class.java)
                    if (escala != null) {
                        when (turnoAtual) {
                            "AM" -> _escalaAM.value = escala
                            "PM" -> _escalaPM.value = escala
                        }
                        updateOndasForCurrentTurno()
                        // Atualizar estado de conexão
                        updateConnectionState()
                    }
                }
            }
    }
    
    /**
     * Ordena itens da onda por modalidade e vaga
     */
    private fun sortOndaItemsByModalidade(
        items: List<OndaItem>,
        motoristasMap: Map<String, Motorista>
    ): List<OndaItem> {
        return items.sortedWith(compareBy<OndaItem> { 
            it.vaga.toIntOrNull() ?: Int.MAX_VALUE // Ordenar numericamente por vaga (01, 02, 03...)
        })
    }
    
    /**
     * Calcula o tempo médio de carregamento
     */
    private fun calculateAverageLoadingTime(statusMap: Map<String, StatusMotorista>) {
        val completedLoads = statusMap.values.filter {
            it.estado == "CONCLUIDO" && it.inicioCarregamento != null && it.fimCarregamento != null
        }

        if (completedLoads.isNotEmpty()) {
            val totalDuration = completedLoads.sumOf {
                (it.fimCarregamento ?: 0) - (it.inicioCarregamento ?: 0)
            }
            _averageLoadingTime.value = totalDuration / completedLoads.size
        } else {
            _averageLoadingTime.value = null
        }
    }
    
    /**
     * Sincroniza as ondas com os dados atualizados dos motoristas
     */
    private fun syncOndasWithMotoristaData() {
        viewModelScope.launch {
            val turno = _turnoAtual.value
            val currentEscala = when (turno) {
                "AM" -> _escalaAM.value
                "PM" -> _escalaPM.value
                else -> null
            } ?: return@launch
            
            val motoristasMap = _motoristasMap.value
            var updated = false
            
            val ondasAtualizadas = currentEscala.ondas.map { onda ->
                val itensAtualizados = onda.itens.map { item ->
                    val motorista = motoristasMap[item.motoristaId]
                    if (motorista != null && (item.nome != motorista.nome || item.modalidade != motorista.modalidade)) {
                        updated = true
                        item.copy(
                            nome = motorista.nome,
                            modalidade = motorista.modalidade
                        )
                    } else {
                        item
                    }
                }
                
                // Reordenar itens se necessário
                val itensOrdenados = sortOndaItemsByModalidade(
                    itensAtualizados,
                    motoristasMap
                )
                
                onda.copy(itens = itensOrdenados)
            }
            
            if (updated) {
                // ✅ CRÍTICO: Garantir que a escala sempre tenha a data atual
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val escalaAtualizada = currentEscala.copy(ondas = ondasAtualizadas, data = hoje)
                
                when (turno) {
                    "AM" -> _escalaAM.value = escalaAtualizada
                    "PM" -> _escalaPM.value = escalaAtualizada
                }
                
                updateOndasForCurrentTurno()
                
                // Salvar no Firestore
                escalaRepository.saveEscala(currentBaseId, escalaAtualizada)
                
                println("✅ Ondas sincronizadas com dados atualizados dos motoristas")
            }
        }
    }
    
    /**
     * Notificar todos os motoristas escalados
     */
    fun saveAndNotifyAll() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val turno = _turnoAtual.value
                val currentEscala = when (turno) {
                    "AM" -> _escalaAM.value
                    "PM" -> _escalaPM.value
                    else -> null
                }
                
                if (currentEscala == null || currentEscala.ondas.isEmpty()) {
                    _error.value = "Nenhuma escala para notificar"
                    _isLoading.value = false
                    return@launch
                }
                
                // Contar total de motoristas
                val totalMotoristas = currentEscala.ondas.sumOf { it.itens.size }
                
                if (totalMotoristas == 0) {
                    _error.value = "Adicione motoristas antes de notificar"
                    _isLoading.value = false
                    return@launch
                }
                
                // 1. Atualizar status de todos os motoristas para A_CAMINHO
                var sucessos = 0
                var falhas = 0
                val motoristaIds = mutableListOf<String>()
                
                currentEscala.ondas.forEach { onda ->
                    onda.itens.forEach { item ->
                        try {
                            val success = motoristaRepository.updateStatusMotorista(
                                motoristaId = item.motoristaId,
                                baseId = currentBaseId,
                                estado = "A_CAMINHO",
                                mensagem = "Você está escalado! Siga para o galpão e aguarde instruções."
                            )
                            
                            if (success) {
                                motoristaIds.add(item.motoristaId)
                                sucessos++
                            } else {
                                falhas++
                            }
                        } catch (e: Exception) {
                            Log.e("OperationalViewModel", "Erro ao atualizar status de ${item.nome}: ${e.message}")
                            falhas++
                        }
                    }
                }
                
                // 2. Enviar notificações push via backend Python (cada motorista = 1 Worker → POST /notify/motorista)
                if (motoristaIds.isNotEmpty()) {
                    try {
                        val title = "🚛 Você foi escalado!"
                        val body = "Você está escalado! Siga para o galpão e aguarde instruções."
                        val data = mapOf("tipo" to "escalacao")
                        motoristaIds.forEach { motoristaId ->
                            NotifyMotoristaWorker.enqueue(
                                context = getApplication(),
                                baseId = currentBaseId,
                                motoristaId = motoristaId,
                                title = title,
                                body = body,
                                data = data
                            )
                        }
                        Log.d("OperationalViewModel", "✅ Notificações push enfileiradas para ${motoristaIds.size} motoristas (backend Python)")
                    } catch (e: Exception) {
                        Log.e("OperationalViewModel", "❌ Erro ao enfileirar notificações: ${e.message}", e)
                    }
                }
                
                _isLoading.value = false
                
                if (falhas == 0) {
                    _message.value = "✅ $totalMotoristas motoristas notificados!"
                } else {
                    _message.value = "⚠️ $sucessos notificados, $falhas falharam."
                }
                
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = "Erro ao notificar motoristas: ${e.message}"
                Log.e("OperationalViewModel", "Erro em saveAndNotifyAll: ${e.message}")
            }
        }
    }
    
    /**
     * Migrar status AGUARDANDO para A_CAMINHO (executar uma vez para corrigir dados antigos)
     */
    fun migrateOldStatusToNew() {
        viewModelScope.launch {
            try {
                Log.d("OperationalViewModel", "🔄 Iniciando migração de status antigos...")
                
                // Buscar todos os status com AGUARDANDO na subcoleção correta
                val db = FirebaseFirestore.getInstance()
                val statusRef = db.collection("bases")
                    .document(currentBaseId)
                    .collection("status_motoristas")
                    .whereEqualTo("estado", "AGUARDANDO")
                    .get()
                    .await()
                
                var migratedCount = 0
                
                statusRef.documents.forEach { doc ->
                    try {
                        doc.reference.update("estado", "A_CAMINHO").await()
                        migratedCount++
                        Log.d("OperationalViewModel", "✅ Migrado: ${doc.id}")
                    } catch (e: Exception) {
                        Log.e("OperationalViewModel", "❌ Erro ao migrar ${doc.id}: ${e.message}")
                    }
                }
                
                if (migratedCount > 0) {
                    _message.value = "✅ $migratedCount status(es) migrado(s) de AGUARDANDO para A_CAMINHO"
                    Log.d("OperationalViewModel", "✅ Migração concluída: $migratedCount registros atualizados")
                } else {
                    _message.value = "ℹ️ Nenhum status AGUARDANDO encontrado"
                    Log.d("OperationalViewModel", "ℹ️ Nenhum status AGUARDANDO encontrado para migrar")
                }
                
            } catch (e: Exception) {
                _error.value = "Erro na migração: ${e.message}"
                Log.e("OperationalViewModel", "❌ Erro na migração: ${e.message}")
            }
        }
    }
    
    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        statusListener?.remove()
        motoristasListener?.remove()
        escalaListener?.remove()
    }
}

