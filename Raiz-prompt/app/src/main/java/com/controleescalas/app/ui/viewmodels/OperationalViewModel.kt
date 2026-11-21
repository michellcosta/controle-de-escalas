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
import com.controleescalas.app.data.NotifyStatusChangeWorker
import com.controleescalas.app.utils.BrasilDate
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.*

/**
 * Representa uma ação de escala em massa.
 */
data class BulkScaleAction(
    val isAdd: Boolean,
    val motoristaId: String,
    val nome: String,
    val ondaIndex: Int,
    val vaga: String?,
    val rota: String?,
    val sacas: Int?
)

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
    
    // Mutex para garantir que atualizações na escala sejam sequenciais e evitar condições de corrida
    private val escalaMutex = Mutex()
    
        private val configRepository = com.controleescalas.app.data.repositories.ConfigRepository()
    // Controla se o turno inicial (primeiro da lista) já foi definido nesta sessão
    private var turnoInicialDefinido = false

    // Turno selecionado: SEMPRE o PRIMEIRO da lista da base ao entrar na Operação
    private val _turnoAtual = MutableStateFlow(configRepository.turnosPadrao.first())
    val turnoAtual: StateFlow<String> = _turnoAtual.asStateFlow()

    // Turnos cadastrados da base (realtime — admin pode adicionar/remover)
    private val _turnosDaBase = MutableStateFlow(configRepository.turnosPadrao)
    val turnosDaBase: StateFlow<List<String>> = _turnosDaBase.asStateFlow()

    // Escalas por turno (chave = nome do turno cadastrado na base)
    private val _escalasPorTurno = MutableStateFlow<Map<String, Escala>>(emptyMap())

    private fun escalaDoTurno(turno: String): Escala? =
        _escalasPorTurno.value[turno]

    private fun setEscalaDoTurno(turno: String, escala: Escala) {
        _escalasPorTurno.value = _escalasPorTurno.value + (turno to escala)
        escalasCache["${escala.data}_${turno}"] = escala
    }
    
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

    // Disponibilidade declarada para hoje: motoristaId -> estado no turno selecionado
    // (true = disponível, false = indisponível, null/ausente = sem resposta)
    private val _disponibilidadeMap = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val disponibilidadeMap: StateFlow<Map<String, Boolean?>> = _disponibilidadeMap.asStateFlow()
    private var disponibilidadeDoc: com.controleescalas.app.data.models.Disponibilidade? = null
    private val disponibilidadeRepository = com.controleescalas.app.data.Repository()

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
    // B7: data da escala em observação — changeTurno re-registra o listener com ela
    private var escalaDataHoje: String = ""
    
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
                val hoje = BrasilDate.hoje()
                escalaDataHoje = hoje
                carregarDisponibilidadeHoje(baseId)

                // Turnos cadastrados da base (padrão AM0/AM/PM/SD/SN se não cadastrado)
                val turnos = configRepository.getTurnosBase(baseId)
                _turnosDaBase.value = turnos
                // Turno selecionado ao ENTRAR no app = PRIMEIRO da lista da base.
                // Durante a sessão, trocar de aba preserva a seleção do admin;
                // volta ao primeiro apenas se o turno atual for removido da lista.
                if (!turnoInicialDefinido || _turnoAtual.value !in turnos) {
                    _turnoAtual.value = turnos.firstOrNull() ?: "AM"
                    turnoInicialDefinido = true
                }

                // Carregar a escala de CADA turno cadastrado
                val mapaEscalas = mutableMapOf<String, Escala>()
                for (turno in turnos) {
                    val escala = escalaRepository.getEscalaByDateAndTurno(baseId, hoje, turno)
                        ?: Escala(baseId = baseId, data = hoje, turno = turno, ondas = emptyList())
                    mapaEscalas[turno] = escala
                    escalasCache["${hoje}_$turno"] = escala
                }
                _escalasPorTurno.value = mapaEscalas

                // Mostrar ondas do turno atual
                updateOndasForCurrentTurno()

                // Carregar motoristas disponíveis
                val motoristas = motoristaRepository.getMotoristas(baseId)
                _motoristasDisponiveis.value = motoristas

                // Iniciar listeners de tempo real
                startStatusListener(baseId)
                startMotoristasListener(baseId)
                startEscalaListener(baseId, hoje)

                // Realtime: admin adiciona/remove turnos -> Operação acompanha
                startTurnosListener(baseId)

            } catch (e: Exception) {
                _error.value = "Erro ao carregar dados: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Realtime dos turnos cadastrados da base: turno novo entra na Operação;
     * turno removido sai (a escala dele deixa de ser exibida, doc permanece).
     */
    private var turnosListener: ListenerRegistration? = null
    private fun startTurnosListener(baseId: String) {
        turnosListener?.remove()
        val configRepository = com.controleescalas.app.data.repositories.ConfigRepository()
        turnosListener = configRepository.observeTurnosBase(baseId, onUpdate = { turnos ->
            _turnosDaBase.value = turnos
            val mapaAtual = _escalasPorTurno.value
            // Adicionar turnos novos com escala vazia
            for (turno in turnos) {
                if (!mapaAtual.containsKey(turno)) {
                    val escala = Escala(baseId = currentBaseId, data = escalaDataHoje, turno = turno, ondas = emptyList())
                    setEscalaDoTurno(turno, escala)
                }
            }
            // Remover turnos retirados
            val mapaFiltrado = mapaAtual.filterKeys { it in turnos }
            if (mapaFiltrado != mapaAtual) {
                _escalasPorTurno.value = mapaFiltrado
            }
            // Turno atual não existe mais -> voltar para o primeiro
            if (_turnoAtual.value !in turnos) {
                changeTurno(turnos.firstOrNull() ?: "AM")
            } else {
                updateOndasForCurrentTurno()
            }
        })
    }

    fun changeTurno(novoTurno: String) {
        _turnoAtual.value = novoTurno
        updateOndasForCurrentTurno()
        atualizarDisponibilidadeMap()
        // B7 da auditoria: o listener de escala captura o turno no registro —
        // sem re-registrar, a escala PM nunca recebia atualizações realtime
        // (admin via dado congelado após trocar de aba).
        if (currentBaseId.isNotBlank() && escalaDataHoje.isNotBlank()) {
            startEscalaListener(currentBaseId, escalaDataHoje)
        }
    }

    /**
     * Carrega a disponibilidade declarada para hoje (badge no dialog de montagem rápida).
     * Disparo em paralelo para não atrasar o loadData.
     */
    private fun carregarDisponibilidadeHoje(baseId: String) {
        viewModelScope.launch {
            try {
                disponibilidadeDoc = disponibilidadeRepository.getDisponibilidade(baseId, BrasilDate.hoje())
                atualizarDisponibilidadeMap()
            } catch (e: Exception) {
                println("⚠️ OperationalVM: Erro ao carregar disponibilidade: ${e.message}")
                _disponibilidadeMap.value = emptyMap()
            }
        }
    }

    private fun atualizarDisponibilidadeMap() {
        _disponibilidadeMap.value = disponibilidadeDoc?.motoristas
            ?.associate { it.motoristaId to it.estadoNoTurno(_turnoAtual.value) }
            ?: emptyMap()
    }

    private fun updateOndasForCurrentTurno() {
        val ondasOriginais = _escalasPorTurno.value[_turnoAtual.value]?.ondas ?: emptyList()
        
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
                    // Notificar motorista e admins via backend Python
                    NotifyStatusChangeWorker.enqueue(
                        getApplication(),
                        currentBaseId,
                        motorista.motoristaId,
                        "CONCLUIDO",
                        motorista.nome
                    )
                    // Notificar admin localmente
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
                    NotifyStatusChangeWorker.enqueue(
                        getApplication(),
                        currentBaseId,
                        motorista.motoristaId,
                        "CONCLUIDO",
                        motorista.nome
                    )
                    _message.value = "${motorista.nome} marcado como concluído"
                    Log.d("OperationalViewModel", "✅ Motorista ${motorista.nome} marcado como concluído pelo admin")
                    
                    // Incrementar dia trabalhado na quinzena
                    val dataAtual = BrasilDate.formatDdMmYyyy()
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
                val success = RetryUtils.retryWithBackoff(maxRetries = 3) {
                    motoristaRepository.updateStatusMotorista(
                        motoristaId = motorista.motoristaId,
                        baseId = currentBaseId,
                        estado = "A_CAMINHO",
                        mensagem = "Status resetado. Continue aguardando instruções."
                    )
                }
                
                if (success) {
                    val title = "Status Atualizado"
                    val body = "Status resetado. Continue aguardando instruções."
                    val data = mapOf("tipo" to "status_reset", "estado" to "A_CAMINHO")
                    // Enviar push direto (igual chamada estacionamento/vaga) para resposta imediata
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
                    }
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
                    NotifyMotoristaWorker.enqueue(
                        context = getApplication(),
                        baseId = currentBaseId,
                        motoristaId = motorista.motoristaId,
                        title = "Status Atualizado",
                        body = "A caminho do galpão",
                        data = mapOf("tipo" to "status_reset", "estado" to "A_CAMINHO")
                    )
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
            val currentEscala = (_escalasPorTurno.value[turno] ?: null) ?: return@launch
            val onda = currentEscala.ondas.getOrNull(ondaIndex) ?: return@launch
            val item = onda.itens.find { it.motoristaId == motoristaId } ?: return@launch
            val vaga = novaVaga?.takeIf { it.isNotBlank() } ?: item.vaga
            val rota = novaRota?.takeIf { it.isNotBlank() } ?: item.rota
            val sacas = novasSacas ?: item.sacas
            updateDriverInOnda(ondaIndex, item, vaga, rota, sacas)
        }
    }

    /**
     * B8 da auditoria: aplica uma transformação na escala do turno ATUAL
     * ATOMICAMENTE sob o escalaMutex. O eco do snapshot listener (que grava
     * o mapa _escalasPorTurno sob o MESMO mutex) não pode mais sobrescrever uma
     * edição no meio do caminho (lost update: admin edita, chega snapshot, a
     * edição some da tela e da próxima gravação).
     * A gravação no Firestore fica FORA do lock — Mutex do Kotlin não é
     * reentrante; nunca chamar funções que travam dentro da transformação.
     * Retorna a escala atualizada, ou null se não havia escala ou a
     * transformação decidiu abortar (chamador não salva).
     */
    private suspend fun editarEscalaDoTurno(transformar: (Escala) -> Escala?): Escala? {
        return escalaMutex.withLock {
            val turno = _turnoAtual.value
            val current = _escalasPorTurno.value[turno] ?: return@withLock null
            val atualizada = transformar(current) ?: return@withLock null
            setEscalaDoTurno(turno, atualizada)
            atualizada
        }
    }

    /**
     * Atualizar vaga, rota e sacas de um motorista em uma onda
     */
    fun updateDriverInOnda(ondaIndex: Int, motorista: OndaItem, novaVaga: String, novaRota: String, novasSacas: Int? = null) {
        viewModelScope.launch {
            try {
                // B8: leitura → mutação → estado, atomicamente sob o escalaMutex
                val escalaAtualizada = editarEscalaDoTurno { currentEscala ->
                    val onda = currentEscala.ondas.getOrNull(ondaIndex) ?: return@editarEscalaDoTurno null

                    // Encontrar o índice do motorista na onda
                    val motoristaIndex = onda.itens.indexOfFirst { it.motoristaId == motorista.motoristaId }
                    if (motoristaIndex == -1) return@editarEscalaDoTurno null

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
                    val hoje = BrasilDate.hoje()
                    currentEscala.copy(ondas = ondasAtualizadas, data = hoje)
                } ?: return@launch

                val turno = _turnoAtual.value
                escalasCache["${escalaAtualizada.data}_$turno"] = escalaAtualizada

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
        viewModelScope.launch {
            try {
                val turno = _turnoAtual.value

                // B8: mutação atômica sob o escalaMutex
                val updatedEscala = editarEscalaDoTurno { currentEscala ->
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
                    currentEscala.copy(
                        ondas = ondasAtualizadas
                    )
                } ?: return@launch

                escalasCache["${updatedEscala.data}_$turno"] = updatedEscala

                updateOndasForCurrentTurno()
                escalaRepository.saveEscala(currentBaseId, ensureEscalaDataAtual(updatedEscala))

                Log.d("OperationalViewModel", "✅ Nova onda adicionada. Total de ondas: ${updatedEscala.ondas.size}")
            } catch (e: Exception) {
                _error.value = "Erro ao adicionar onda: ${e.message}"
            }
        }
    }
    
    /**
     * Atualizar dados de uma onda (nome, horário, tipo)
     */
    fun updateOnda(ondaIndex: Int, novoNome: String, novoHorario: String, novoTipo: String) {
        viewModelScope.launch {
            try {
                // B8: leitura → mutação → estado, atomicamente sob o escalaMutex
                val escalaAtualizada = editarEscalaDoTurno { currentEscala ->
                    val ondas = currentEscala.ondas.toMutableList()
                    if (ondaIndex !in ondas.indices) return@editarEscalaDoTurno null

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
                    val hoje = BrasilDate.hoje()
                    currentEscala.copy(ondas = ondas, data = hoje)
                } ?: return@launch

                val turno = _turnoAtual.value
                escalasCache["${escalaAtualizada.data}_$turno"] = escalaAtualizada

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
                // B8: mutação atômica sob o escalaMutex
                val updatedEscala = editarEscalaDoTurno { currentEscala ->
                    val ondas = currentEscala.ondas.toMutableList()
                    if (ondaIndex !in ondas.indices) return@editarEscalaDoTurno null

                    // Remover a onda da lista
                    ondas.removeAt(ondaIndex)

                    currentEscala.copy(ondas = ondas)
                } ?: return@launch

                val turno = _turnoAtual.value
                escalasCache["${updatedEscala.data}_$turno"] = updatedEscala

                updateOndasForCurrentTurno()
                escalaRepository.saveEscala(currentBaseId, ensureEscalaDataAtual(updatedEscala))
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
                // B8: verificação de duplicado + leitura → mutação → estado,
                // tudo atômico sob o escalaMutex
                val updatedEscala = editarEscalaDoTurno { currentEscala ->
                    // ✅ NOVO: Verificar se o motorista já está em alguma onda do turno ATUAL
                    val ondaExistente = currentEscala.ondas.find { onda ->
                        onda.itens.any { it.motoristaId == motorista.id }
                    }

                    if (ondaExistente != null) {
                        val nomeOndaExistente = ondaExistente.nome
                        _error.value = "${motorista.nome} já está escalado na $nomeOndaExistente"
                        Log.w("OperationalViewModel", "Tentativa de escalar motorista ${motorista.nome} que já está na $nomeOndaExistente")
                        return@editarEscalaDoTurno null
                    }

                    val ondas = currentEscala.ondas.toMutableList()
                    if (ondaIndex !in ondas.indices) return@editarEscalaDoTurno null

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
                    val hoje = BrasilDate.hoje()
                    val updated = currentEscala.copy(ondas = ondas, data = hoje)

                    Log.d("OperationalViewModel", "✅ Motorista ${motorista.nome} adicionado à onda $ondaIndex. Total de itens: ${itensAtualizados.size}")
                    updated
                } ?: return@launch

                Log.d("OperationalViewModel", "🔍 DEBUG addMotoristaToOnda: Data da escala depois: ${updatedEscala.data}, turno: ${updatedEscala.turno}")

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
                val currentEscala = (_escalasPorTurno.value[turno] ?: null) ?: return@launch
                
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
                val hoje = BrasilDate.hoje()
                val updatedEscala = currentEscala.copy(ondas = ondas, data = hoje)
                
                // ✅ DEBUG: Log para verificar a data antes de salvar
                Log.d("OperationalViewModel", "🔍 DEBUG: Data atual calculada: $hoje")
                Log.d("OperationalViewModel", "🔍 DEBUG: Data da escala antes: ${currentEscala.data}")
                Log.d("OperationalViewModel", "🔍 DEBUG: Data da escala depois: ${updatedEscala.data}")
                Log.d("OperationalViewModel", "🔍 DEBUG: Turno: ${updatedEscala.turno}")
                
                setEscalaDoTurno(turno, updatedEscala)
                
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
                escalaMutex.withLock {
                    val turno = _turnoAtual.value
                    val currentEscala = (_escalasPorTurno.value[turno] ?: null) ?: return@withLock

                    // Se já está escalado em alguma onda, apenas atualizar vaga/rota/sacas (não duplicar)
                    for ((idx, o) in currentEscala.ondas.withIndex()) {
                        val existing = o.itens.find { it.motoristaId == motoristaId }
                        if (existing != null) {
                            // Chamamos a função de atualização interna (que deve estar fora do lock ou ser suspensa)
                            // Para simplificar, vamos atualizar diretamente aqui já que estamos com o lock
                            val item = existing
                            val vagaNormalized = vaga.trim().takeIf { it.isNotBlank() }?.let { if (it.length == 1) "0$it" else it } ?: item.vaga
                            val rotaNormalized = rota.trim().takeIf { it.isNotBlank() }?.uppercase() ?: item.rota
                            val sacasNormalized = sacas ?: item.sacas
                            
                            val asOndas = currentEscala.ondas.toMutableList()
                            val itensOnda = asOndas[idx].itens.toMutableList()
                            val itemIdx = itensOnda.indexOf(existing)
                            if (itemIdx >= 0) {
                                itensOnda[itemIdx] = item.copy(vaga = vagaNormalized, rota = rotaNormalized, sacas = sacasNormalized)
                                asOndas[idx] = asOndas[idx].copy(itens = itensOnda.sortedBy { it.vaga.toIntOrNull() ?: Int.MAX_VALUE })
                                val updateEscala = currentEscala.copy(ondas = asOndas, data = BrasilDate.hoje())
                                
                                setEscalaDoTurno(turno, updateEscala)
                                updateOndasForCurrentTurno()
                                escalaRepository.saveEscala(currentBaseId, updateEscala)
                                
                                try {
                                    motoristaRepository.updateStatusMotorista(
                                        motoristaId = motoristaId,
                                        baseId = currentBaseId,
                                        estado = "A_CAMINHO",
                                        vagaAtual = vagaNormalized,
                                        rotaAtual = rotaNormalized,
                                        mensagem = "Dados atualizados!"
                                    )
                                } catch (_: Exception) {}
                            }
                            
                            _message.value = "Dados do motorista atualizados"
                            return@withLock
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
                        vaga = vaga.trim().let { if (it.length == 1) "0$it" else it },
                        rota = rota.trim().uppercase(),
                        sacas = sacas,
                        modalidade = "FROTA"
                    )
                    val itensAtualizados = (onda.itens + novoItem).sortedBy { 
                        it.vaga.toIntOrNull() ?: Int.MAX_VALUE 
                    }
                    ondas[ondaIndex] = onda.copy(itens = itensAtualizados)
                    val hoje = BrasilDate.hoje()
                    val updatedEscala = currentEscala.copy(ondas = ondas, data = hoje)

                    setEscalaDoTurno(turno, updatedEscala)
                    updateOndasForCurrentTurno()
                    escalaRepository.saveEscala(currentBaseId, updatedEscala)

                    try {
                        motoristaRepository.updateStatusMotorista(
                            motoristaId = motoristaId,
                            baseId = currentBaseId,
                            estado = "A_CAMINHO",
                            mensagem = "Você está escalado!",
                            vagaAtual = novoItem.vaga.takeIf { it.isNotBlank() },
                            rotaAtual = novoItem.rota.takeIf { it.isNotBlank() },
                            inicioCarregamento = null,
                            fimCarregamento = null
                        )
                    } catch (_: Exception) {}
                    _message.value = "Motorista adicionado"
                }
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro ao adicionar por foto: ${e.message}", e)
                _error.value = "Erro: ${e.message}"
            }
        }
    }

    fun bulkApplyScaleActions(actions: List<BulkScaleAction>) {
        if (actions.isEmpty()) return
        viewModelScope.launch {
            try {
                escalaMutex.withLock {
                    val turno = _turnoAtual.value
                    var currentEscala = (_escalasPorTurno.value[turno] ?: null) ?: return@withLock

                    val ondas = currentEscala.ondas.toMutableList()
                    val dataEscala = BrasilDate.hoje()

                    android.util.Log.d("OperationalVM", "🚀 Iniciando processamento de ${actions.size} ações em massa")
                    
                    for (action in actions) {
                        val motoristaId = action.motoristaId
                        val nome = action.nome
                        val ondaIndex = action.ondaIndex
                        val vaga = action.vaga ?: ""

                        android.util.Log.d("OperationalVM", "📦 Processando: $nome (ID: $motoristaId) -> Onda $ondaIndex, Vaga $vaga, Rota ${action.rota ?: "(preservar)"}")

                        // 1. Remover de qualquer onda que ele já esteja, capturando os dados
                        //    atuais: ao mover (ex.: da PRÉ-ESCALA para uma onda real), rota,
                        //    sacas, modalidade e nota são PRESERVADOS quando a ação não informa.
                        var itemAnterior: OndaItem? = null
                        for (i in ondas.indices) {
                            val anterior = ondas[i].itens.firstOrNull { it.motoristaId == motoristaId }
                            if (anterior != null) itemAnterior = anterior
                            val itensFiltrados = ondas[i].itens.filter { it.motoristaId != motoristaId }
                            if (itensFiltrados.size != ondas[i].itens.size) {
                                ondas[i] = ondas[i].copy(itens = itensFiltrados)
                            }
                        }

                        // 2. Garantir que a onda de destino existe
                        //    (numeração ignora a PRÉ-ESCALA: 1ª, 2ª, 3ª... são ondas reais)
                        while (ondaIndex >= ondas.size) {
                            val numero = ondas.count { !it.nome.equals("PRÉ-ESCALA", true) } + 1
                            ondas.add(Onda(nome = "${numero}ª ONDA", horario = "", itens = emptyList()))
                        }

                        // 3. Adicionar na onda correta; null na ação = preservar o valor anterior
                        val targetOnda = ondas[ondaIndex]
                        val rota = action.rota ?: itemAnterior?.rota ?: ""
                        val sacas = action.sacas ?: itemAnterior?.sacas
                        val vagaNormalized = vaga.trim().let { v ->
                            if (v.isEmpty()) "" else if (v.length == 1) "0$v" else v
                        }
                        val rotaNormalized = rota.trim().uppercase()

                        val novoItem = OndaItem(
                            motoristaId = motoristaId,
                            nome = nome,
                            horario = targetOnda.horario,
                            vaga = vagaNormalized,
                            rota = rotaNormalized,
                            hasPdf = itemAnterior?.hasPdf ?: false,
                            sacas = sacas,
                            modalidade = itemAnterior?.modalidade ?: "FROTA",
                            nota = itemAnterior?.nota
                        )
                        
                        val itensAtualizados = (targetOnda.itens + novoItem).sortedBy { 
                            it.vaga.toIntOrNull() ?: Int.MAX_VALUE 
                        }
                        ondas[ondaIndex] = targetOnda.copy(itens = itensAtualizados)

                        // 4. Atualizar status individual no Firestore
                        try {
                            motoristaRepository.updateStatusMotorista(
                                motoristaId = motoristaId,
                                baseId = currentBaseId,
                                estado = "A_CAMINHO",
                                mensagem = "Dados atualizados via Assistente!",
                                vagaAtual = vagaNormalized.takeIf { it.isNotBlank() },
                                rotaAtual = rotaNormalized.takeIf { it.isNotBlank() }
                            )
                        } catch (_: Exception) {}
                    }

                    // PRÉ-ESCALA vazia (todos distribuídos) sai da lista automaticamente
                    val ondasFinais = ondas.filterNot {
                        it.nome.equals("PRÉ-ESCALA", true) && it.itens.isEmpty()
                    }

                    val updatedEscala = currentEscala.copy(ondas = ondasFinais, data = dataEscala)
                    setEscalaDoTurno(turno, updatedEscala)
                    updateOndasForCurrentTurno()
                    escalaRepository.saveEscala(currentBaseId, updatedEscala)
                }
                _message.value = "Escala atualizada (${actions.size} motoristas)"
            } catch (e: Exception) {
                Log.e("OperationalViewModel", "Erro no bulk apply: ${e.message}", e)
                _error.value = "Erro ao atualizar: ${e.message}"
            }
        }
    }

    /**
     * Salva uma notificação enviada no Firestore para histórico do assistente.
     */
    private fun saveNotificationToHistory(
        destinatarioId: String,
        destinatarioNome: String,
        texto: String
    ) {
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser ?: return@launch
                
                // Nome/papel do remetente vêm da sessão local — a coleção `usuarios`
                // não tinha regra de segurança e toda leitura aqui era negada,
                // queimando tentativas e derrubando o histórico de avisos.
                val sessao = com.controleescalas.app.data.SessionManager(getApplication()).getLastActiveSession()
                val remetenteNome = sessao?.userName ?: "Admin"
                val remetentePapel = sessao?.userRole ?: "admin"

                val aviso = hashMapOf(
                    "remetenteId" to currentUser.uid,
                    "remetenteNome" to remetenteNome,
                    "remetentePapel" to remetentePapel,
                    "destinatarioId" to destinatarioId,
                    "destinatarioNome" to destinatarioNome,
                    "texto" to texto,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                db.collection("bases")
                    .document(currentBaseId)
                    .collection("avisos_enviados")
                    .add(aviso)
                    .await()
                
                Log.d("OperationalVM", "✅ Aviso salvo no histórico: $destinatarioNome")
            } catch (e: Exception) {
                Log.e("OperationalVM", "❌ Erro ao salvar histórico de aviso: ${e.message}")
            }
        }
    }

    /**
     * Envia notificações em lote para motoristas (individual ou por onda).
     * @param targetId O ID do motorista ou "WAVE_{index}" para notificar uma onda inteira.
     */
    fun sendNotificationBatch(targetId: String, nome: String, body: String) {
        viewModelScope.launch {
            try {
                val baseId = currentBaseId
                val api = NotificationApiService()
                val recipients = mutableListOf<Pair<String, String>>()

                if (targetId == "ALL") {
                    // Aviso para a base inteira (ação do assistente IA)
                    try {
                        motoristaRepository.getMotoristas(baseId).forEach { m ->
                            if (m.id.isNotBlank()) recipients.add(m.id to m.nome)
                        }
                    } catch (e: Exception) {
                        Log.e("OperationalVM", "❌ Erro ao listar motoristas para aviso à base: ${e.message}")
                    }
                } else if (targetId.startsWith("WAVE_")) {
                    val ondaIndexStr = targetId.removePrefix("WAVE_")
                    val ondaIndex = ondaIndexStr.toIntOrNull() ?: return@launch
                    val currentTurno = _turnoAtual.value
                    val skala = (_escalasPorTurno.value[currentTurno] ?: null)

                    skala?.ondas?.getOrNull(ondaIndex)?.itens?.forEach {
                        recipients.add(it.motoristaId to it.nome)
                    }
                } else {
                    recipients.add(targetId to nome)
                }

                if (recipients.isEmpty()) {
                    Log.w("OperationalVM", "⚠️ Nenhum destinatário encontrado para aviso: $targetId")
                    return@launch
                }

                Log.d("OperationalVM", "🔔 Enviando avisos para ${recipients.size} destinatários")
                // Achado 14 da auditoria: envio sequencial (1 HTTP por motorista
                // em série) + um SessionManager novo por destinatário dentro do
                // histórico. Agora: envio concorrente com limite e erro visível.
                var falhas = 0
                val semaforo = kotlinx.coroutines.sync.Semaphore(6)
                coroutineScope {
                    recipients.map { (id, n) ->
                        async {
                            semaforo.withPermit {
                                val (sent, _) = api.notifyMotorista(
                                    baseId = baseId,
                                    motoristaId = id,
                                    title = "📢 Aviso do Assistente",
                                    body = body,
                                    data = mapOf("tipo" to "aviso")
                                )
                                if (sent) {
                                    saveNotificationToHistory(id, n, body)
                                } else {
                                    falhas++
                                }
                            }
                        }
                    }.awaitAll()
                }

                _message.value = if (falhas > 0) {
                    "Aviso enviado, mas $falhas de ${recipients.size} falharam. Tente reenviar."
                } else {
                    "Aviso enviado para ${recipients.size} motorista(s)"
                }
            } catch (e: Exception) {
                Log.e("OperationalVM", "❌ Erro ao enviar aviso em massa: ${e.message}")
            }
        }
    }
    fun ensureOndasCount(turno: String, count: Int) {
        viewModelScope.launch {
            // B8: mutação atômica; achado 12: só grava quando algo mudou
            // (antes salvava o doc inteiro mesmo sem adicionar onda nenhuma)
            val updatedEscala = editarEscalaDoTurno { currentEscala ->
                var ondas = currentEscala.ondas.toMutableList()
                if (ondas.size >= count) return@editarEscalaDoTurno null
                while (ondas.size < count) {
                    val num = ondas.size + 1
                    ondas.add(Onda(nome = "${num}ª ONDA", horario = "", itens = emptyList()))
                }
                currentEscala.copy(ondas = ondas)
            } ?: return@launch

            updateOndasForCurrentTurno()
            escalaRepository.saveEscala(currentBaseId, ensureEscalaDataAtual(updatedEscala))
        }
    }

    /**
     * Remover motorista de uma onda
     */
    fun removeMotoristaFromOnda(ondaIndex: Int, motoristaId: String) {
        viewModelScope.launch {
            try {
                // B8: mutação atômica sob o escalaMutex
                val updatedEscala = editarEscalaDoTurno { currentEscala ->
                    val ondas = currentEscala.ondas.toMutableList()
                    if (ondaIndex !in ondas.indices) return@editarEscalaDoTurno null

                    val onda = ondas[ondaIndex]
                    val itensAtualizados = onda.itens.filter { it.motoristaId != motoristaId }

                    ondas[ondaIndex] = onda.copy(itens = itensAtualizados)
                    currentEscala.copy(ondas = ondas)
                } ?: return@launch

                updateOndasForCurrentTurno()
                escalaRepository.saveEscala(currentBaseId, ensureEscalaDataAtual(updatedEscala))
                
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
        val hoje = BrasilDate.hoje()
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
                val escala = (_escalasPorTurno.value[_turnoAtual.value] ?: null) ?: return@launch
                
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
                // B11 da auditoria: o retry só disparava se statusListener == null,
                // mas o campo guarda a registration falha e nunca era anulado —
                // a reconexão NUNCA ocorria e o painel ficava sem realtime após
                // uma falha de rede. Anula antes do retry.
                statusListener?.remove()
                statusListener = null
                if (com.controleescalas.app.data.ErroFirestore.cotaOuPermissaoNegada(error)) {
                    // Negação permanente (gate/cota): retry martela ops negadas
                    // que consomem cota — sem reconexão automática.
                    Log.w("OperationalViewModel", "⛔ Listener de status negado (permissão/cota); sem reconexão automática")
                } else {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(5000)
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
                        viewModelScope.launch {
                            escalaMutex.withLock {
                                setEscalaDoTurno(turnoAtual, escala)
                                updateOndasForCurrentTurno()
                                // Atualizar estado de conexão
                                updateConnectionState()
                            }
                        }
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
            val currentEscala = (_escalasPorTurno.value[turno] ?: null) ?: return@launch
            
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
                val hoje = BrasilDate.hoje()
                val escalaAtualizada = currentEscala.copy(ondas = ondasAtualizadas, data = hoje)
                
                setEscalaDoTurno(turno, escalaAtualizada)
                
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
                val currentEscala = (_escalasPorTurno.value[turno] ?: null)
                
                if (currentEscala == null || currentEscala.ondas.isEmpty()) {
                    _error.value = "Nenhuma escala para notificar"
                    _isLoading.value = false
                    return@launch
                }

                // Notificar apenas motoristas em ONDAS REAIS — quem está só na
                // PRÉ-ESCALA ainda não tem onda/vaga e não deve ser notificado
                val ondasReais = currentEscala.ondas.filterNot { it.nome.equals("PRÉ-ESCALA", true) }

                // Contar total de motoristas
                val totalMotoristas = ondasReais.sumOf { it.itens.size }

                if (totalMotoristas == 0) {
                    _error.value = "Adicione motoristas antes de notificar"
                    _isLoading.value = false
                    return@launch
                }

                // 1. Atualizar status de todos os motoristas para A_CAMINHO
                // Gravação em lote único (1 RPC) em vez de um set + leitura de
                // verificação por motorista.
                val motoristaIds = ondasReais.flatMap { onda -> onda.itens.map { it.motoristaId } }
                var sucessos = 0
                try {
                    sucessos = motoristaRepository.atualizarStatusVariosMotoristas(
                        baseId = currentBaseId,
                        motoristaIds = motoristaIds,
                        estado = "A_CAMINHO",
                        mensagem = "Você está escalado! Siga para o galpão e aguarde instruções."
                    ).coerceAtLeast(0)
                } catch (e: Exception) {
                    Log.e("OperationalViewModel", "Erro ao atualizar status em lote: ${e.message}")
                }
                val falhas = motoristaIds.size - sucessos
                
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
        turnosListener?.remove()
    }
}

