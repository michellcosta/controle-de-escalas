package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.controleescalas.app.data.StatusCheckWorker
import com.controleescalas.app.data.StatusMonitoringService
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.data.repositories.ConfigRepository
import com.controleescalas.app.data.GeofencingService
import com.controleescalas.app.data.NotificationService
import com.controleescalas.app.data.NotificationManager
import com.controleescalas.app.ui.screens.DriverEscalaInfo
import com.controleescalas.app.ui.screens.DriverStatusInfo
import com.controleescalas.app.ui.viewmodels.QuinzenaViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * DriverViewModel - Gerencia estado da tela do motorista
 */
class DriverViewModel(application: Application) : AndroidViewModel(application) {
    private val motoristaRepository = MotoristaRepository()
    private val escalaRepository = EscalaRepository()
    private val geofencingService = GeofencingService(application.applicationContext)
    private val notificationService = NotificationService(application.applicationContext)
    private val notificationManager = NotificationManager.getInstance(application.applicationContext)
    private val quinzenaViewModel = QuinzenaViewModel()
    private val workManager = WorkManager.getInstance(application)
    
    companion object {
        private const val WORK_NAME_STATUS_CHECK = "status_check_work"
    }
    
    private val _escalaInfo = MutableStateFlow<DriverEscalaInfo?>(null)
    val escalaInfo: StateFlow<DriverEscalaInfo?> = _escalaInfo.asStateFlow()
    
    private val _statusInfo = MutableStateFlow<DriverStatusInfo?>(null)
    val statusInfo: StateFlow<DriverStatusInfo?> = _statusInfo.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isInsideGalpao = MutableStateFlow(false)
    val isInsideGalpao: StateFlow<Boolean> = _isInsideGalpao.asStateFlow()
    
    private val _isInsideEstacionamento = MutableStateFlow(false)
    val isInsideEstacionamento: StateFlow<Boolean> = _isInsideEstacionamento.asStateFlow()
    
    private val _distanceToGalpao = MutableStateFlow(Double.MAX_VALUE)
    val distanceToGalpao: StateFlow<Double> = _distanceToGalpao.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _motoristaNome = MutableStateFlow<String?>(null)
    val motoristaNome: StateFlow<String?> = _motoristaNome.asStateFlow()
    
    private var motoristaNomeListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var statusListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var configListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var escalaListeners: List<com.google.firebase.firestore.ListenerRegistration> = emptyList()
    
    // Armazenar status anterior para detectar mudanças
    private var statusAnterior: DriverStatusInfo? = null
    
    // Flag para evitar reset de status na primeira carga (quando app é aberto via notificação)
    private var escalaObservacaoInicializada: Boolean = false

    init {
        observeGeofencing()
    }

    /**
     * Observar mudanças no geofencing
     */
    private fun observeGeofencing() {
        viewModelScope.launch {
            geofencingService.geofenceStatus.collect { statusMap ->
                statusMap[GeofencingService.GEOFENCE_GALPAO_ID]?.let { status ->
                    _isInsideGalpao.value = status.isInside
                    _distanceToGalpao.value = status.distance
                }
                
                statusMap[GeofencingService.GEOFENCE_ESTACIONAMENTO_ID]?.let { status ->
                    _isInsideEstacionamento.value = status.isInside
                }
            }
        }
    }
    fun loadDriverData(motoristaId: String, baseId: String) {
        android.util.Log.i("DriverViewModel", "📥 [MOTORISTA] loadDriverData - motoristaId=$motoristaId, baseId=$baseId")
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Sempre observar config da base (mesmo quando não escalado)
                // Assim o motorista recebe atualizações do galpão quando o admin salva
                startConfigListener(motoristaId, baseId, _statusInfo.value?.estado ?: "A_CAMINHO")
                
                // Nome do motorista é carregado via listener em observeMotoristaNome()
                // Carregar escala do dia PRIMEIRO
                val escala = escalaRepository.getEscalaDoDia(baseId, motoristaId)
                _escalaInfo.value = escala
                
                // ✅ Verificar se motorista está escalado antes de iniciar serviços
                if (escala == null) {
                    println("ℹ️ DriverViewModel: Motorista não está escalado, não iniciando serviços de localização")
                    // Garantir que serviços e geofences estão parados (mantém config listener ativo)
                    geofencingService.stopLocationUpdates()
                    geofencingService.removeAllGeofences()
                    stopStatusMonitoringService()
                    _isLoading.value = false
                    return@launch
                }
                
                // Carregar status atual
                val status = motoristaRepository.getStatusMotorista(motoristaId, baseId)
                _statusInfo.value = status
                
                // ✅ Verificar se status é CONCLUIDO antes de iniciar serviços
                if (status?.estado == "CONCLUIDO") {
                    println("ℹ️ DriverViewModel: Status é CONCLUIDO, não iniciando serviços de localização")
                    geofencingService.stopLocationUpdates()
                    stopStatusMonitoringService()
                    _isLoading.value = false
                    return@launch
                }
                
            // Configurar informações do motorista no GeofencingService
            status?.let {
                geofencingService.setMotoristaInfo(motoristaId, baseId, it.estado)
                
                // Carregar configurações da base para obter coordenadas do galpão
                val configRepository = ConfigRepository()
                val config = configRepository.getConfiguracaoBase(baseId)
                
                if (config != null && config.galpao.lat != 0.0 && config.galpao.lng != 0.0) {
                    // Configurar geofence com coordenadas reais do galpão da base
                    geofencingService.createGalpaoGeofence(
                        config.galpao.lat,
                        config.galpao.lng,
                        config.galpao.raio.toDouble()
                    )
                    android.util.Log.d("DriverViewModel", "✅ Coordenadas do galpão configuradas para base $baseId: (${config.galpao.lat}, ${config.galpao.lng}), raio: ${config.galpao.raio}m")
                    println("✅ DriverViewModel: Coordenadas do galpão configuradas para base $baseId: (${config.galpao.lat}, ${config.galpao.lng}), raio: ${config.galpao.raio}m")
                } else {
                    android.util.Log.w("DriverViewModel", "⚠️ Coordenadas do galpão não encontradas para base $baseId")
                    println("⚠️ DriverViewModel: Coordenadas do galpão não encontradas para base $baseId")
                }
                
                // Configurar geofence do estacionamento se estiver configurado
                if (config != null && config.estacionamento.lat != 0.0 && config.estacionamento.lng != 0.0) {
                    // Configurar geofence com coordenadas reais do estacionamento da base
                    geofencingService.createEstacionamentoGeofence(
                        config.estacionamento.lat,
                        config.estacionamento.lng,
                        config.estacionamento.raio.toDouble()
                    )
                    android.util.Log.d("DriverViewModel", "✅ Coordenadas do estacionamento configuradas para base $baseId: (${config.estacionamento.lat}, ${config.estacionamento.lng}), raio: ${config.estacionamento.raio}m")
                    println("✅ DriverViewModel: Coordenadas do estacionamento configuradas para base $baseId: (${config.estacionamento.lat}, ${config.estacionamento.lng}), raio: ${config.estacionamento.raio}m")
                } else {
                    android.util.Log.d("DriverViewModel", "ℹ️ Coordenadas do estacionamento não configuradas para base $baseId (opcional)")
                    println("ℹ️ DriverViewModel: Coordenadas do estacionamento não configuradas para base $baseId (opcional)")
                }
                
                // Iniciar monitoramento de localização APENAS se estiver escalado e não estiver CONCLUIDO
                if (geofencingService.hasLocationPermissions()) {
                    geofencingService.startLocationUpdates()
                    println("✅ DriverViewModel: Monitoramento de localização iniciado (motorista escalado)")
                } else {
                    println("⚠️ DriverViewModel: Permissões de localização não concedidas")
                }
                
                // Iniciar serviço de monitoramento de status em background APENAS se estiver escalado e não estiver CONCLUIDO
                startStatusMonitoringService(motoristaId, baseId)
                
                // Iniciar WorkManager para verificação periódica APENAS se estiver escalado e não estiver CONCLUIDO
                startStatusCheckWork(motoristaId, baseId, it.estado, checagemImediata = true)
                // Config listener já iniciado no início de loadDriverData
            } ?: run {
                println("⚠️ DriverViewModel: Status não encontrado, não foi possível iniciar monitoramento")
            }
            
        } catch (e: Exception) {
            _error.value = "Erro ao carregar dados: ${e.message}"
            println("❌ DriverViewModel: Erro ao carregar dados: ${e.message}")
        } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Atualizar status do motorista
     */
    fun updateStatus(
        motoristaId: String,
        baseId: String,
        estado: String,
        mensagem: String = "",
        vagaAtual: String? = null
    ) {
        viewModelScope.launch {
            try {
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = estado,
                    mensagem = mensagem,
                    vagaAtual = vagaAtual
                )
                
                if (success) {
                    // Recarregar status atualizado
                    loadDriverData(motoristaId, baseId)
                } else {
                    _error.value = "Erro ao atualizar status"
                }
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar status: ${e.message}"
            }
        }
    }
    
    /**
     * Iniciar monitoramento de localização
     * ✅ Verifica se motorista está escalado e não está CONCLUIDO antes de iniciar
     */
    fun startLocationMonitoring() {
        try {
            // Verificar se motorista está escalado
            if (_escalaInfo.value == null) {
                println("⚠️ DriverViewModel: Não é possível iniciar monitoramento - motorista não está escalado")
                return
            }
            
            // Verificar se status não é CONCLUIDO
            if (_statusInfo.value?.estado == "CONCLUIDO") {
                println("⚠️ DriverViewModel: Não é possível iniciar monitoramento - status é CONCLUIDO")
                return
            }
            
            geofencingService.startLocationUpdates()
        } catch (e: SecurityException) {
            _error.value = "Permissões de localização necessárias"
        } catch (e: Exception) {
            _error.value = "Erro ao iniciar localização: ${e.message}"
        }
    }

    /**
     * Parar monitoramento de localização
     */
    fun stopLocationMonitoring() {
        geofencingService.stopLocationUpdates()
    }

    /**
     * Criar geofences para base
     */
    fun setupGeofencesForBase(galpaoLat: Double, galpaoLng: Double, estacionamentoLat: Double?, estacionamentoLng: Double?) {
        try {
            geofencingService.createGalpaoGeofence(galpaoLat, galpaoLng)
            estacionamentoLat?.let { lat ->
                estacionamentoLng?.let { lng ->
                    geofencingService.createEstacionamentoGeofence(lat, lng)
                }
            }
        } catch (e: SecurityException) {
            _error.value = "Permissões de localização necessárias"
        } catch (e: Exception) {
            _error.value = "Erro ao configurar geofences: ${e.message}"
        }
    }

    /**
     * Formatar distância para exibição
     */
    fun formatDistance(distance: Double): String {
        return when {
            distance >= 1000 -> "${String.format("%.1f", distance / 1000)} km"
            distance >= 1 -> "${String.format("%.0f", distance)} m"
            else -> "< 1 m"
        }
    }

    /**
     * Limpar erro
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Observar mudanças no nome do motorista em tempo real
     */
    fun observeMotoristaNome(motoristaId: String, baseId: String) {
        println("🔍 DriverViewModel.observeMotoristaNome: Chamado com motoristaId=$motoristaId, baseId=$baseId")
        
        // Limpar listener anterior se existir
        motoristaNomeListener?.remove()
        
        // Carregar nome inicial antes de iniciar o listener
        viewModelScope.launch {
            try {
                println("📥 DriverViewModel: Carregando nome inicial...")
                val nomeInicial = motoristaRepository.getMotoristaNome(motoristaId, baseId)
                println("✅ DriverViewModel: Nome inicial carregado: $nomeInicial")
                _motoristaNome.value = nomeInicial
                
                // Se o nome inicial for null, verificar se o motorista foi excluído
                if (nomeInicial == null) {
                    println("⚠️ DriverViewModel: Motorista não encontrado, pode ter sido excluído")
                    checkMotoristaExcluido(motoristaId, baseId)
                }
            } catch (e: Exception) {
                println("❌ DriverViewModel: Erro ao carregar nome inicial: ${e.message}")
                e.printStackTrace()
            }
        }
        
        println("🎧 DriverViewModel: Iniciando listener...")
        motoristaNomeListener = motoristaRepository.observeMotoristaNome(
            motoristaId = motoristaId,
            baseId = baseId,
            onUpdate = { nome ->
                println("🔄 DriverViewModel: Nome atualizado via listener: $nome")
                _motoristaNome.value = nome
                
                // Se o nome for null, verificar se o motorista foi excluído
                if (nome == null) {
                    println("⚠️ DriverViewModel: Nome retornou null, verificando se motorista foi excluído")
                    checkMotoristaExcluido(motoristaId, baseId)
                }
            },
            onError = { error ->
                println("❌ DriverViewModel: Erro no listener: ${error.message}")
                error.printStackTrace()
                _error.value = "Erro ao observar nome: ${error.message}"
            }
        )
        println("✅ DriverViewModel: Listener configurado")
    }
    
    /**
     * Observar mudanças na escala do motorista em tempo real
     */
    /**
     * Confirmar que o motorista entendeu a chamada
     */
    fun confirmarChamada(motoristaId: String, baseId: String) {
        viewModelScope.launch {
            try {
                val success = motoristaRepository.confirmarChamada(motoristaId, baseId)
                if (success) {
                    println("✅ DriverViewModel: Chamada confirmada com sucesso")
                } else {
                    println("❌ DriverViewModel: Falha ao confirmar chamada")
                    _error.value = "Erro ao confirmar chamada"
                }
            } catch (e: Exception) {
                println("❌ DriverViewModel: Erro ao confirmar chamada: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao confirmar: ${e.message}"
            }
        }
    }
    
    /**
     * Marcar carregamento como concluído
     */
    fun concluirCarregamento(motoristaId: String, baseId: String) {
        viewModelScope.launch {
            try {
                println("🔄 DriverViewModel.concluirCarregamento: Iniciando")
                println("   motoristaId: $motoristaId")
                println("   baseId: $baseId")
                
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "CONCLUIDO",
                    mensagem = "Carregamento concluído pelo motorista"
                )
                if (success) {
                    println("✅ DriverViewModel: Carregamento marcado como concluído")
                    
                    // Incrementar dia trabalhado na quinzena
                    val motoristaNome = _motoristaNome.value ?: "Motorista"
                    val dataAtual = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    
                    println("📅 DriverViewModel: Incrementando quinzena")
                    println("   motoristaId: $motoristaId")
                    println("   motoristaNome: $motoristaNome")
                    println("   baseId: $baseId")
                    println("   data: $dataAtual")
                    
                    try {
                        quinzenaViewModel.incrementarDiaTrabalhado(
                            baseId = baseId,
                            motoristaId = motoristaId,
                            motoristaNome = motoristaNome,
                            data = dataAtual
                        )
                        println("✅ DriverViewModel: Dia trabalhado incrementado na quinzena para data $dataAtual")
                    } catch (e: Exception) {
                        println("❌ DriverViewModel: Erro ao incrementar quinzena: ${e.message}")
                        e.printStackTrace()
                        _error.value = "Erro ao incrementar quinzena: ${e.message}"
                    }
                } else {
                    println("❌ DriverViewModel: Falha ao concluir carregamento")
                    _error.value = "Erro ao concluir carregamento"
                }
            } catch (e: Exception) {
                println("❌ DriverViewModel: Erro ao concluir carregamento: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao concluir: ${e.message}"
            }
        }
    }

    fun observeEscalaMotorista(motoristaId: String, baseId: String) {
        // Remover listeners anteriores se existirem
        escalaListeners.forEach { it.remove() }
        escalaListeners = emptyList()
        
        // Resetar flag de inicialização quando começar nova observação
        escalaObservacaoInicializada = false
        
        escalaListeners = escalaRepository.observeEscalaDoMotorista(
            baseId = baseId,
            motoristaId = motoristaId,
            onUpdate = { escalaInfo ->
                val escalaAnterior = _escalaInfo.value
                _escalaInfo.value = escalaInfo
                
                // Se tinha escala antes e agora não tem, motorista foi removido da escala
                if (escalaAnterior != null && escalaInfo == null) {
                    println("⚠️ DriverViewModel: Motorista removido da escala, limpando status")
                    android.util.Log.w("DriverViewModel", "⚠️ Motorista $motoristaId removido da escala da base $baseId")
                    
                    // Limpar status para mostrar "não escalado"
                    _statusInfo.value = null
                    
                    // Parar monitoramento de localização e remover geofences
                    geofencingService.stopLocationUpdates()
                    geofencingService.removeAllGeofences()
                    
                    // Parar listener de status para evitar que ele sobrescreva o status limpo
                    statusListener?.remove()
                    statusListener = null
                    
                    // Resetar flag de inicialização
                    escalaObservacaoInicializada = false
                    
                    println("✅ DriverViewModel: Status limpo e listeners parados, tela voltará para estado 'não escalado'")
                }
                
                // Se não tinha escala antes e agora tem, motorista foi adicionado à escala
                // IMPORTANTE: Só resetar se NÃO for a primeira carga (inicialização)
                // Se for a primeira carga, apenas marcar como inicializada e não resetar
                if (escalaAnterior == null && escalaInfo != null) {
                    // Se é a primeira vez que estamos observando (inicialização), apenas marcar como inicializada
                    if (!escalaObservacaoInicializada) {
                        println("ℹ️ DriverViewModel: Primeira carga da escala detectada, não resetando status (preservando status atual)")
                        android.util.Log.d("DriverViewModel", "ℹ️ Primeira carga da escala para motorista $motoristaId, preservando status")
                        escalaObservacaoInicializada = true
                        
                        // ✅ CRÍTICO: Reiniciar listener de status para processar atualizações que foram ignoradas
                        // O listener estava ignorando atualizações porque _escalaInfo.value era null
                        // Agora que a escala foi carregada, precisamos reiniciar o listener para processar atualizações pendentes
                        println("🔄 DriverViewModel: Reiniciando listener de status para processar atualizações pendentes após escala ser carregada")
                        observeStatusMotorista(motoristaId, baseId)
                        
                        // Não fazer nada mais, apenas marcar como inicializada
                    } else {
                        // Se não é a primeira carga, então é uma mudança real (motorista foi adicionado à escala)
                        println("✅ DriverViewModel: Motorista adicionado à escala, verificando se precisa resetar status")
                        android.util.Log.d("DriverViewModel", "✅ Motorista $motoristaId adicionado à escala da base $baseId")
                        
                        // Verificar se já existe um status válido no Firebase antes de resetar
                    viewModelScope.launch {
                        try {
                            val statusAtual = motoristaRepository.getStatusMotorista(motoristaId, baseId)
                            
                            // Quando um motorista é adicionado à escala após ser reativado, resetar o status
                            // Exceto se for um estado final (CONCLUIDO) ou já estiver em A_CAMINHO
                            val estadosFinais = listOf("CONCLUIDO")
                            
                            // Resetar se:
                            // 1. Status não existe (null)
                            // 2. Status é diferente de A_CAMINHO e não é um estado final
                            val deveResetar = when {
                                statusAtual == null -> true
                                statusAtual.estado == "A_CAMINHO" -> false // Já está no estado inicial
                                statusAtual.estado in estadosFinais -> false // Estados finais não devem ser resetados
                                else -> true // Qualquer outro estado deve ser resetado (CARREGANDO, CHEGUEI, ESTACIONAMENTO, etc.)
                            }
                            
                            if (deveResetar) {
                                println("✅ DriverViewModel: Status não existe ou é antigo (${statusAtual?.estado}), resetando para A_CAMINHO")
                                val success = motoristaRepository.updateStatusMotorista(
                                    motoristaId = motoristaId,
                                    baseId = baseId,
                                    estado = "A_CAMINHO",
                                    mensagem = "Aguardando instruções"
                                )
                                
                                if (success) {
                                    // Limpar status local
                                    _statusInfo.value = null
                                    
                                    // Reiniciar listener de status (pode ter sido parado quando foi removido da escala)
                                    // O listener vai receber a atualização do Firebase automaticamente
                                    if (statusListener == null) {
                                        observeStatusMotorista(motoristaId, baseId)
                                    }
                                    
                                    // Reiniciar monitoramento de localização e configurar geofences
                                    if (geofencingService.hasLocationPermissions()) {
                                        // Recarregar dados para configurar geofences com as coordenadas da base
                                        loadDriverData(motoristaId, baseId)
                                    }
                                    
                                    println("✅ DriverViewModel: Status resetado para A_CAMINHO no Firebase")
                                } else {
                                    println("❌ DriverViewModel: Erro ao resetar status no Firebase")
                                }
                            } else {
                                println("ℹ️ DriverViewModel: Status já existe e é válido (${statusAtual?.estado}), mantendo status atual")
                                // Mesmo sem resetar, garantir que os listeners e geofences estejam configurados
                                if (statusListener == null) {
                                    observeStatusMotorista(motoristaId, baseId)
                                }
                                if (geofencingService.hasLocationPermissions()) {
                                    loadDriverData(motoristaId, baseId)
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ DriverViewModel: Erro ao verificar/resetar status: ${e.message}")
                            android.util.Log.e("DriverViewModel", "❌ Erro ao verificar/resetar status: ${e.message}", e)
                        }
                    }
                    }
                } else if (escalaAnterior != null && escalaInfo != null) {
                    // Escala já existia e continua existindo (mudança normal, não resetar)
                    // Apenas garantir que a flag está marcada
                    if (!escalaObservacaoInicializada) {
                        escalaObservacaoInicializada = true
                        println("ℹ️ DriverViewModel: Escala já existia, marcando como inicializada")
                        
                        // ✅ CRÍTICO: Garantir que o listener de status esteja ativo quando a escala já existia
                        // Isso resolve o caso onde o app foi aberto e a escala já estava presente
                        if (statusListener == null) {
                            println("🔄 DriverViewModel: Escala já existia mas listener de status não estava ativo, reiniciando...")
                            observeStatusMotorista(motoristaId, baseId)
                        }
                    }
                }
            },
            onError = { error ->
                _error.value = "Erro ao observar escala: ${error.message}"
            }
        )
    }
    
    /**
     * Observar mudanças no status do motorista em tempo real
     */
    fun observeStatusMotorista(motoristaId: String, baseId: String) {
        println("🔍 DriverViewModel.observeStatusMotorista: Chamado com motoristaId=$motoristaId, baseId=$baseId")
        
        // Limpar listener anterior se existir
        statusListener?.remove()
        
        // Resetar status anterior ao iniciar nova observação
        statusAnterior = null
        
        // Carregar status inicial antes de iniciar o listener
        viewModelScope.launch {
            try {
                println("📥 DriverViewModel: Carregando status inicial...")
                val statusInicial = motoristaRepository.getStatusMotorista(motoristaId, baseId)
                println("✅ DriverViewModel: Status inicial carregado: estado=${statusInicial?.estado}, mensagem=${statusInicial?.mensagem}")
                _statusInfo.value = statusInicial
                statusAnterior = statusInicial
                
                // Configurar informações do motorista no GeofencingService
                statusInicial?.let {
                    geofencingService.setMotoristaInfo(motoristaId, baseId, it.estado)
                }
            } catch (e: Exception) {
                println("❌ DriverViewModel: Erro ao carregar status inicial: ${e.message}")
                e.printStackTrace()
            }
        }
        
        println("🎧 DriverViewModel: Iniciando listener de status...")
        statusListener = motoristaRepository.observeStatusMotorista(
            motoristaId = motoristaId,
            baseId = baseId,
            onUpdate = { statusInfo ->
                // Verificar se há escala antes de processar atualização de status
                // Se não houver escala, ignorar a atualização para não sobrescrever o status limpo
                if (_escalaInfo.value == null) {
                    println("⚠️ DriverViewModel: Ignorando atualização de status - motorista não está na escala")
                    return@observeStatusMotorista
                }
                
                println("🔄 DriverViewModel: Status atualizado via listener")
                println("   📋 Status anterior: estado=${statusAnterior?.estado}, mensagem=${statusAnterior?.mensagem}")
                println("   📋 Status novo: estado=${statusInfo?.estado}, mensagem=${statusInfo?.mensagem}")
                
                // Se o status for null: parar monitoramento (regra: sem status = não monitorar)
                if (statusInfo == null) {
                    println("🛑 DriverViewModel: Status null detectado, parando monitoramento de localização")
                    geofencingService.stopLocationUpdates()
                    geofencingService.removeAllGeofences()
                    stopStatusMonitoringService()
                    stopStatusCheckWork()
                    _statusInfo.value = null
                    statusAnterior = null
                    checkMotoristaExcluido(motoristaId, baseId)
                    return@observeStatusMotorista
                }
                
                // Se status mudou de null para válido (não CONCLUIDO): iniciar monitoramento
                if (statusAnterior == null && statusInfo.estado != "CONCLUIDO" && _escalaInfo.value != null) {
                    println("✅ DriverViewModel: Status criado (${statusInfo.estado}), iniciando monitoramento de localização")
                    loadDriverData(motoristaId, baseId)
                }
                
                // Detectar mudança de status ou mensagem (não notificar na primeira carga)
                val statusMudou = statusAnterior?.estado != statusInfo?.estado
                val mensagemMudou = statusAnterior?.mensagem != statusInfo?.mensagem
                val houveMudanca = statusMudou || mensagemMudou
                
                // Detectar mensagens de escalação para sempre notificar (mesmo sem mudança detectada)
                // Isso garante que o botão "Notificar Todos" sempre funcione, inclusive em segundo plano
                val mensagemEscalacao = statusInfo?.mensagem?.contains("escalado", ignoreCase = true) == true ||
                                        statusInfo?.mensagem?.contains("Siga para o galpão", ignoreCase = true) == true ||
                                        statusInfo?.mensagem?.contains("Você está escalado", ignoreCase = true) == true
                
                println("   🔍 Análise de mudanças:")
                println("      📊 Status mudou: $statusMudou")
                println("      💬 Mensagem mudou: $mensagemMudou")
                println("      ✅ Houve mudança: $houveMudanca")
                println("      📝 Status anterior é null: ${statusAnterior == null}")
                println("      🚛 Mensagem de escalação detectada: $mensagemEscalacao")
                
                // Sempre notificar quando for CARREGANDO ou IR_ESTACIONAMENTO, mesmo se repetido
                if (statusInfo != null && (statusInfo.estado == "CARREGANDO" || statusInfo.estado == "IR_ESTACIONAMENTO")) {
                    // Verificar se apenas confirmadoEm mudou (sem mudança de estado/mensagem/vaga/rota)
                    val apenasConfirmacaoMudou = statusAnterior?.estado == statusInfo.estado &&
                                                statusAnterior?.mensagem == statusInfo.mensagem &&
                                                statusAnterior?.vagaAtual == statusInfo.vagaAtual &&
                                                statusAnterior?.rotaAtual == statusInfo.rotaAtual &&
                                                statusAnterior?.confirmadoEm != statusInfo.confirmadoEm
                    
                    // Não notificar se apenas a confirmação mudou
                    if (!apenasConfirmacaoMudou) {
                        println("🔔 DriverViewModel: Disparando notificação para ${statusInfo.estado}")
                        when (statusInfo.estado) {
                            "CARREGANDO" -> {
                                val vaga = statusInfo.vagaAtual ?: "N/A"
                                val rota = statusInfo.rotaAtual ?: try {
                                    val rotaMatch = Regex("rota ([A-Z0-9-]+)", RegexOption.IGNORE_CASE).find(statusInfo.mensagem)
                                    rotaMatch?.groupValues?.get(1) ?: "N/A"
                                } catch (e: Exception) {
                                    "N/A"
                                }
                                notificationManager.sendMotoristaChamadaNotification(
                                    motoristaNome = "Motorista",
                                    vaga = vaga,
                                    rota = rota
                                )
                                println("🔔 DriverViewModel: Notificação de chamada para vaga enviada - Vaga: $vaga, Rota: $rota")
                            }
                            "IR_ESTACIONAMENTO" -> {
                                notificationManager.sendMotoristaEstacionamentoNotification(
                                    motoristaNome = "Motorista"
                                )
                                println("🔔 DriverViewModel: Notificação de estacionamento enviada")
                            }
                        }
                    } else {
                        println("ℹ️ DriverViewModel: Apenas confirmação mudou, não notificando")
                    }
                } else if (statusAnterior != null && (houveMudanca || (statusInfo?.estado == "A_CAMINHO" && mensagemEscalacao))) {
                    // Para outros estados, notificar apenas se houver mudança OU se for mensagem de escalação
                    println("🔔 DriverViewModel: Disparando notificação devido a mudança detectada ou mensagem de escalação")
                    statusInfo?.let { status ->
                        when (status.estado) {
                            "CONCLUIDO" -> {
                                notificationManager.sendConclusaoNotification(
                                    mensagem = status.mensagem.ifEmpty { "Carregamento finalizado com sucesso!" }
                                )
                                println("🔔 DriverViewModel: Notificação de conclusão enviada")
                            }
                            else -> {
                                // Notificar outras mudanças de status importantes
                                if (status.estado.isNotEmpty() && status.estado != "A_CAMINHO") {
                                    notificationManager.sendStatusUpdateNotification(
                                        status = status.estado.replace("_", " "),
                                        mensagem = status.mensagem.ifEmpty { "Status atualizado" }
                                    )
                                    println("🔔 DriverViewModel: Notificação de mudança de status enviada: ${status.estado}")
                                } else if (status.estado == "A_CAMINHO" && mensagemEscalacao) {
                                    // Apenas mensagem de escalação (ex: "Você foi escalado") - reset de status
                                    // já notifica via NotifyMotoristaWorker, evitar duplicata
                                    notificationManager.sendStatusUpdateNotification(
                                        status = "🚛 Você foi escalado!",
                                        mensagem = status.mensagem.ifEmpty { "Status atualizado" }
                                    )
                                    println("🔔 DriverViewModel: Notificação enviada (A_CAMINHO - escalação)")
                                }
                            }
                        }
                    }
                } else if (statusAnterior == null && statusInfo != null) {
                    // Primeira carga: notificar apenas para estados importantes (exceto CARREGANDO e IR_ESTACIONAMENTO que já foram tratados acima)
                    if (statusInfo.estado == "CONCLUIDO") {
                        println("🔔 DriverViewModel: Primeira carga com estado importante, disparando notificação")
                        notificationManager.sendConclusaoNotification(
                            mensagem = statusInfo.mensagem.ifEmpty { "Carregamento finalizado com sucesso!" }
                        )
                        println("🔔 DriverViewModel: Notificação de conclusão enviada (primeira carga)")
                    } else {
                        println("ℹ️ DriverViewModel: Primeira carga com estado neutro, não disparando notificação")
                    }
                } else {
                    println("ℹ️ DriverViewModel: Nenhuma mudança detectada, não disparando notificação")
                }
                
                // Atualizar status no GeofencingService quando mudar
                statusInfo?.let {
                    geofencingService.updateCurrentStatus(it.estado)
                    
                    // ✅ Parar serviços e geofencing se status for CONCLUIDO
                    if (it.estado == "CONCLUIDO") {
                        println("🛑 DriverViewModel: Status CONCLUIDO detectado, parando geofencing e serviços")
                        geofencingService.stopLocationUpdates()
                        geofencingService.removeAllGeofences()
                        stopStatusMonitoringService()
                        // Cancelar WorkManager imediatamente
                        stopStatusCheckWork()
                        // Limpar histórico de notificações para permitir nova notificação se necessário
                        notificationManager.clearNotificationHistory()
                    }
                    
                    // ✅ Verificar se ainda está escalado, se não estiver, parar serviços
                    if (_escalaInfo.value == null) {
                        println("🛑 DriverViewModel: Motorista não está mais escalado, parando serviços de localização")
                        geofencingService.stopLocationUpdates()
                        stopStatusMonitoringService()
                    }
                    
                    // Atualizar WorkManager com novo status
                    updateStatusCheckWork(motoristaId, baseId, it.estado)
                }
                
                // Atualizar status anterior
                statusAnterior = statusInfo
                _statusInfo.value = statusInfo
                println("✅ DriverViewModel: Status atualizado no StateFlow")
            },
            onError = { error ->
                println("❌ DriverViewModel: Erro no listener de status: ${error.message}")
                error.printStackTrace()
                _error.value = "Erro ao observar status: ${error.message}"
            }
        )
        println("✅ DriverViewModel: Listener de status configurado")
    }
    
    /**
     * Verificar se o motorista foi excluído e limpar dados para voltar ao estado "não escalado"
     */
    private fun checkMotoristaExcluido(motoristaId: String, baseId: String) {
        viewModelScope.launch {
            try {
                // Verificar se o documento do motorista existe
                val nome = motoristaRepository.getMotoristaNome(motoristaId, baseId)
                val status = motoristaRepository.getStatusMotorista(motoristaId, baseId)
                
                // Se ambos forem null, o motorista foi excluído
                if (nome == null && status == null) {
                    println("❌ DriverViewModel: Motorista foi excluído! Limpando dados e voltando para estado 'não escalado'...")
                    android.util.Log.w("DriverViewModel", "❌ Motorista $motoristaId foi excluído da base $baseId")
                    
                    // Limpar dados locais - isso fará a tela mostrar "não escalado"
                    _motoristaNome.value = null
                    _statusInfo.value = null
                    _escalaInfo.value = null
                    
                    // Parar monitoramento de localização
                    geofencingService.stopLocationUpdates()
                    
                    // Parar serviço de monitoramento
                    stopStatusMonitoringService()
                    
                    // Parar WorkManager
                    stopStatusCheckWork()
                    
                    // Limpar listeners
                    motoristaNomeListener?.remove()
                    statusListener?.remove()
                    
                    // Não fazer logout, apenas limpar os dados para mostrar estado "não escalado"
                    println("✅ DriverViewModel: Dados limpos, tela voltará para estado 'não escalado'")
                }
            } catch (e: Exception) {
                println("❌ DriverViewModel: Erro ao verificar exclusão: ${e.message}")
            }
        }
    }
    
    /**
     * Iniciar serviço de monitoramento de status em background
     */
    private fun startStatusMonitoringService(motoristaId: String, baseId: String) {
        println("🔍 DriverViewModel.startStatusMonitoringService: Chamado - MotoristaId=$motoristaId, BaseId=$baseId")
        android.util.Log.d("DriverViewModel", "🔍 Iniciando serviço de monitoramento - MotoristaId=$motoristaId, BaseId=$baseId")
        
        try {
            val context = getApplication<Application>().applicationContext
            println("✅ DriverViewModel: Context obtido")
            android.util.Log.d("DriverViewModel", "✅ Context obtido")
            
            val intent = Intent(context, StatusMonitoringService::class.java).apply {
                action = StatusMonitoringService.ACTION_START
                putExtra(StatusMonitoringService.EXTRA_MOTORISTA_ID, motoristaId)
                putExtra(StatusMonitoringService.EXTRA_BASE_ID, baseId)
            }
            println("✅ DriverViewModel: Intent criado - Action=${intent.action}, MotoristaId=${intent.getStringExtra(StatusMonitoringService.EXTRA_MOTORISTA_ID)}, BaseId=${intent.getStringExtra(StatusMonitoringService.EXTRA_BASE_ID)}")
            android.util.Log.d("DriverViewModel", "✅ Intent criado - Action=${intent.action}")
            
            ContextCompat.startForegroundService(context, intent)
            println("✅ DriverViewModel: startForegroundService chamado com sucesso")
            android.util.Log.d("DriverViewModel", "✅ startForegroundService chamado")
        } catch (e: Exception) {
            println("❌ DriverViewModel: Erro ao iniciar serviço de monitoramento: ${e.message}")
            android.util.Log.e("DriverViewModel", "❌ Erro ao iniciar serviço: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Logout: encerra medição, geofences e a verificação periódica.
     * Fechar a tela não chama isto; o serviço continua em segundo plano.
     */
    fun pararMonitoramentoAoSair() {
        geofencingService.stopLocationUpdates()
        geofencingService.removeAllGeofences()
        stopStatusMonitoringService()
        stopStatusCheckWork()
    }

    /**
     * Parar serviço de monitoramento de status
     */
    private fun stopStatusMonitoringService() {
        try {
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, StatusMonitoringService::class.java).apply {
                action = StatusMonitoringService.ACTION_STOP
            }
            context.startService(intent)
            println("✅ DriverViewModel: Serviço de monitoramento de status parado")
        } catch (e: Exception) {
            println("❌ DriverViewModel: Erro ao parar serviço de monitoramento: ${e.message}")
        }
    }
    
    /**
     * Iniciar WorkManager para verificação periódica de status
     * Funciona mesmo quando o app está completamente fechado
     */
    private fun startStatusCheckWork(
        motoristaId: String,
        baseId: String,
        lastStatus: String?,
        checagemImediata: Boolean = false
    ) {
        try {
            println("🔍 DriverViewModel.startStatusCheckWork: Iniciando - MotoristaId=$motoristaId, BaseId=$baseId, Último Status=$lastStatus, imediata=$checagemImediata")
            android.util.Log.d("DriverViewModel", "🔍 Iniciando WorkManager - MotoristaId=$motoristaId, BaseId=$baseId")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()
            
            val inputData = Data.Builder()
                .putString(StatusCheckWorker.KEY_MOTORISTA_ID, motoristaId)
                .putString(StatusCheckWorker.KEY_BASE_ID, baseId)
                .putString(StatusCheckWorker.KEY_LAST_STATUS, lastStatus)
                .build()
            
            val workInterval = 15L
            val flexInterval = 5L
            android.util.Log.d("DriverViewModel", "⚡ WorkManager configurado: intervalo=${workInterval}min (status: $lastStatus)")
            
            val periodicWork = PeriodicWorkRequestBuilder<StatusCheckWorker>(
                workInterval, TimeUnit.MINUTES,
                flexInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME_STATUS_CHECK)
                .build()
            
            workManager.cancelUniqueWork(WORK_NAME_STATUS_CHECK)

            if (checagemImediata) {
                val immediateWork = OneTimeWorkRequestBuilder<StatusCheckWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .addTag("${WORK_NAME_STATUS_CHECK}_immediate")
                    .build()
                workManager.enqueue(immediateWork)
                android.util.Log.d("DriverViewModel", "✅ OneTimeWorkRequest imediato enfileirado para verificação de localização")
            }
            
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_STATUS_CHECK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )
            
            println("✅ DriverViewModel: WorkManager iniciado com sucesso")
            android.util.Log.d("DriverViewModel", "✅ WorkManager iniciado - periódica a cada ${workInterval} minutos (imediata: $checagemImediata, status: $lastStatus)")
        } catch (e: Exception) {
            println("❌ DriverViewModel: Erro ao iniciar WorkManager: ${e.message}")
            android.util.Log.e("DriverViewModel", "❌ Erro ao iniciar WorkManager: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Parar WorkManager
     */
    private fun stopStatusCheckWork() {
        try {
            workManager.cancelUniqueWork(WORK_NAME_STATUS_CHECK)
            println("✅ DriverViewModel: WorkManager parado")
            android.util.Log.d("DriverViewModel", "✅ WorkManager parado")
        } catch (e: Exception) {
            println("❌ DriverViewModel: Erro ao parar WorkManager: ${e.message}")
            android.util.Log.e("DriverViewModel", "❌ Erro ao parar WorkManager: ${e.message}", e)
        }
    }
    
    /**
     * Atualizar último status no WorkManager
     */
    private fun updateStatusCheckWork(motoristaId: String, baseId: String, newStatus: String) {
        try {
            // Cancelar trabalho atual
            workManager.cancelUniqueWork(WORK_NAME_STATUS_CHECK)
            
            // Reiniciar com novo status
            startStatusCheckWork(motoristaId, baseId, newStatus)
            
            println("✅ DriverViewModel: WorkManager atualizado com novo status: $newStatus")
            android.util.Log.d("DriverViewModel", "✅ WorkManager atualizado - Status: $newStatus")
        } catch (e: Exception) {
            println("❌ DriverViewModel: Erro ao atualizar WorkManager: ${e.message}")
            android.util.Log.e("DriverViewModel", "❌ Erro ao atualizar WorkManager: ${e.message}", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        geofencingService.stopLocationUpdates()
        stopConfigListener()
        motoristaNomeListener?.remove()
        motoristaNomeListener = null
        statusListener?.remove()
        statusListener = null
        escalaListeners.forEach { it.remove() }
        escalaListeners = emptyList()
    }
    
    /**
     * Observar mudanças na configuração da base (raio do galpão) em tempo real.
     * Quando o admin altera o raio e o motorista já está dentro, atualiza para CHEGUEI imediatamente.
     */
    private fun startConfigListener(motoristaId: String, baseId: String, currentStatus: String) {
        stopConfigListener()
        android.util.Log.i("DriverViewModel", "🎧 [MOTORISTA] Listener config - baseId=$baseId")
        val configRepository = ConfigRepository()
        configListener = configRepository.observeConfiguracaoBase(
            baseId = baseId,
            onUpdate = { config ->
                android.util.Log.d("DriverViewModel", "📥 Config recebida via listener - baseId=$baseId, galpao.lat=${config?.galpao?.lat}, galpao.lng=${config?.galpao?.lng}, galpao.raio=${config?.galpao?.raio}m")
                if (config != null && config.galpao.lat != 0.0 && config.galpao.lng != 0.0) {
                    // Regra: só aplicar geofences se motorista escalado e com status válido (não CONCLUIDO)
                    if (_escalaInfo.value == null || _statusInfo.value == null || _statusInfo.value?.estado == "CONCLUIDO") {
                        android.util.Log.d("DriverViewModel", "⚠️ Motorista não escalado, sem status ou CONCLUIDO - removendo geofences, não aplicando")
                        geofencingService.removeAllGeofences()
                        return@observeConfiguracaoBase
                    }
                    android.util.Log.d("DriverViewModel", "🔄 Config do galpão atualizada - raio: ${config.galpao.raio}m, aplicando geofences...")
                    val status = _statusInfo.value!!.estado
                    geofencingService.setMotoristaInfo(motoristaId, baseId, status)
                    geofencingService.createGalpaoGeofence(
                        config.galpao.lat,
                        config.galpao.lng,
                        config.galpao.raio.toDouble()
                    )
                    if (config.estacionamento.lat != 0.0 && config.estacionamento.lng != 0.0) {
                        geofencingService.createEstacionamentoGeofence(
                            config.estacionamento.lat,
                            config.estacionamento.lng,
                            config.estacionamento.raio.toDouble()
                        )
                    }
                } else {
                    android.util.Log.d("DriverViewModel", "⚠️ Config recebida mas galpão inválido (lat=${config?.galpao?.lat}, lng=${config?.galpao?.lng}) - ignorando")
                }
            },
            onError = { e ->
                android.util.Log.e("DriverViewModel", "❌ Erro no listener de config: ${e.message}")
            }
        )
    }
    
    private fun stopConfigListener() {
        configListener?.remove()
        configListener = null
        android.util.Log.d("DriverViewModel", "🛑 Listener de config parado")
    }
}
