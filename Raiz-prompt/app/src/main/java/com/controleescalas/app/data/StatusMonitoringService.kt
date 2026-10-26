package com.controleescalas.app.data

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.controleescalas.app.R
import com.controleescalas.app.data.models.ConfiguracaoBase
import com.controleescalas.app.data.repositories.ConfigRepository
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.ui.screens.DriverStatusInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.jvm.Volatile
import kotlinx.coroutines.tasks.await

/**
 * Foreground Service para monitorar mudanças de status do motorista
 * Funciona mesmo quando o app está fechado
 */
class StatusMonitoringService : Service() {
    
    companion object {
        private const val TAG = "StatusMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "status_monitoring_channel"
        private const val ACCENT_COLOR = 0xFF10B981.toInt() // NeonGreen - padrão do app
        
        const val ACTION_START = "com.controleescalas.app.START_MONITORING"
        const val ACTION_STOP = "com.controleescalas.app.STOP_MONITORING"
        
        const val EXTRA_MOTORISTA_ID = "motorista_id"
        const val EXTRA_BASE_ID = "base_id"

        private const val PREFS_NAME = "status_monitoring"
        private const val KEY_MOTORISTA_ID = "motorista_id"
        private const val KEY_BASE_ID = "base_id"
        private const val IDADE_MAXIMA_LOCALIZACAO_MS = 60_000L

        // Intervalo mínimo entre retentativas de gravação automática (a verificação roda
        // a cada 30s; sem isso, com a cota estourada eram ~2.880 tentativas/dia por motorista).
        private const val RETRY_WRITE_INTERVAL_MS = 10 * 60 * 1000L
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statusListener: ListenerRegistration? = null
    private var configListener: ListenerRegistration? = null
    private var motoristaId: String? = null
    private var baseId: String? = null
    @Volatile private var lastStatus: String? = null
    @Volatile private var configPronta: Boolean = false
    @Volatile private var galpaoLat: Double = 0.0
    @Volatile private var galpaoLng: Double = 0.0
    @Volatile private var galpaoRaio: Double = 0.0
    @Volatile private var estacionamentoLat: Double = 0.0
    @Volatile private var estacionamentoLng: Double = 0.0
    @Volatile private var estacionamentoRaio: Double = 0.0
    private var notificationService: NotificationService? = null
    private var periodicCheckJob: kotlinx.coroutines.Job? = null
    private var locationCheckJob: kotlinx.coroutines.Job? = null
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private var chegueiSolicitadoEm = 0L
    private var estacionamentoSolicitadoEm = 0L
    
    override fun onCreate() {
        super.onCreate()
        // Inicializar NotificationService aqui, quando o Context já está disponível
        notificationService = NotificationService(this)
        createNotificationChannel()
        Log.d(TAG, "🔧 onCreate chamado")
        println("🔧 StatusMonitoringService.onCreate: Criando serviço")
        Log.d(TAG, "✅ StatusMonitoringService criado")
        println("✅ StatusMonitoringService: Serviço criado com sucesso")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔍 onStartCommand - Action: ${intent?.action}, MotoristaId: ${intent?.getStringExtra(EXTRA_MOTORISTA_ID)}, BaseId: ${intent?.getStringExtra(EXTRA_BASE_ID)}")
        println("🔍 StatusMonitoringService.onStartCommand: Action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                motoristaId = intent.getStringExtra(EXTRA_MOTORISTA_ID)
                baseId = intent.getStringExtra(EXTRA_BASE_ID)
                
                Log.d(TAG, "📋 Parâmetros extraídos - MotoristaId: $motoristaId, BaseId: $baseId")
                println("📋 StatusMonitoringService: MotoristaId=$motoristaId, BaseId=$baseId")
                
                if (motoristaId != null && baseId != null) {
                    salvarSessaoMonitoramento(motoristaId!!, baseId!!)
                    Log.d(TAG, "🚀 Iniciando foreground service...")
                    println("🚀 StatusMonitoringService: Iniciando foreground service")
                    startForeground(NOTIFICATION_ID, createForegroundNotification())
                    Log.d(TAG, "✅ Foreground iniciado, chamando startMonitoring()")
                    println("✅ StatusMonitoringService: Foreground iniciado")
                    startMonitoring()
                    Log.d(TAG, "✅ Monitoramento iniciado para motorista: $motoristaId")
                    println("✅ StatusMonitoringService: Monitoramento iniciado para motorista: $motoristaId")
                } else {
                    Log.e(TAG, "❌ MotoristaId ou BaseId não fornecidos - MotoristaId: $motoristaId, BaseId: $baseId")
                    println("❌ StatusMonitoringService: MotoristaId ou BaseId não fornecidos")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "🛑 Parando monitoramento...")
                println("🛑 StatusMonitoringService: Parando monitoramento")
                limparSessaoMonitoramento()
                stopMonitoring()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                Log.d(TAG, "✅ Monitoramento parado")
                println("✅ StatusMonitoringService: Monitoramento parado")
                return START_NOT_STICKY
            }
            else -> {
                val salva = lerSessaoMonitoramento()
                if (salva == null) {
                    Log.w(TAG, "⚠️ Serviço religado sem motorista salvo, encerrando")
                    println("⚠️ StatusMonitoringService: Religado sem sessão, encerrando")
                    startForeground(NOTIFICATION_ID, createForegroundNotification())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                motoristaId = salva.first
                baseId = salva.second
                Log.d(TAG, "🔄 Retomando monitoramento após religar - MotoristaId: $motoristaId, BaseId: $baseId")
                println("🔄 StatusMonitoringService: Retomando monitoramento de $motoristaId")
                startForeground(NOTIFICATION_ID, createForegroundNotification())
                startMonitoring()
            }
        }
        return START_STICKY // Reinicia o serviço se for morto pelo sistema
    }

    private fun salvarSessaoMonitoramento(motoristaId: String, baseId: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_MOTORISTA_ID, motoristaId)
            .putString(KEY_BASE_ID, baseId)
            .apply()
    }

    private fun limparSessaoMonitoramento() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
    }

    private fun lerSessaoMonitoramento(): Pair<String, String>? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val motorista = prefs.getString(KEY_MOTORISTA_ID, null)
        val base = prefs.getString(KEY_BASE_ID, null)
        return if (!motorista.isNullOrBlank() && !base.isNullOrBlank()) motorista to base else null
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento em segundo plano",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitora localização sem som ou vibração"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val bigText = "Detectando sua chegada ao galpão e estacionamento."
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Controle de Escalas")
            .setContentText("Monitorando localização")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(ACCENT_COLOR)
            .setColorized(true)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }
    
    private fun startMonitoring() {
        val mId = motoristaId ?: run {
            Log.e(TAG, "❌ startMonitoring: motoristaId é null")
            println("❌ StatusMonitoringService.startMonitoring: motoristaId é null")
            return
        }
        val bId = baseId ?: run {
            Log.e(TAG, "❌ startMonitoring: baseId é null")
            println("❌ StatusMonitoringService.startMonitoring: baseId é null")
            return
        }
        
        Log.d(TAG, "🔍 startMonitoring iniciado - MotoristaId: $mId, BaseId: $bId")
        println("🔍 StatusMonitoringService.startMonitoring: MotoristaId=$mId, BaseId=$bId")
        
        serviceScope.launch {
            try {
                Log.d(TAG, "📦 Criando MotoristaRepository...")
                println("📦 StatusMonitoringService: Criando MotoristaRepository")
                val motoristaRepository = MotoristaRepository()
                
                // Obter status inicial
                Log.d(TAG, "🔍 Buscando status inicial para motorista $mId na base $bId...")
                println("🔍 StatusMonitoringService: Buscando status inicial")
                val statusInicial = motoristaRepository.getStatusMotorista(mId, bId)
                lastStatus = statusInicial?.estado
                Log.d(TAG, "📊 Status inicial obtido: ${statusInicial?.estado ?: "null"}")
                println("📊 StatusMonitoringService: Status inicial = ${statusInicial?.estado ?: "null"}")
                
                // Iniciar listener em tempo real
                Log.d(TAG, "🎧 Configurando listener de status em tempo real...")
                println("🎧 StatusMonitoringService: Configurando listener")
                statusListener = motoristaRepository.observeStatusMotorista(
                    motoristaId = mId,
                    baseId = bId,
                    onUpdate = { statusInfo: DriverStatusInfo? ->
                        Log.d(TAG, "📨 Listener recebeu atualização - StatusInfo: ${statusInfo?.estado ?: "null"}, Mensagem: ${statusInfo?.mensagem ?: "null"}")
                        println("📨 StatusMonitoringService: Listener recebeu atualização - Estado: ${statusInfo?.estado ?: "null"}")
                        
                        statusInfo?.let { status ->
                            val novoStatus = status.estado
                            Log.d(TAG, "🔍 Comparando status - Último: '$lastStatus', Novo: '$novoStatus'")
                            println("🔍 StatusMonitoringService: Último='$lastStatus', Novo='$novoStatus'")
                            
                            // Verificar se houve mudança significativa
                            if (lastStatus != novoStatus && novoStatus.isNotEmpty()) {
                                Log.d(TAG, "🔄 Mudança de status detectada: '$lastStatus' -> '$novoStatus'")
                                println("🔄 StatusMonitoringService: Mudança detectada: '$lastStatus' -> '$novoStatus'")
                                
                                // ✅ Verificar se status é CONCLUIDO - se for, parar o serviço
                                if (novoStatus == "CONCLUIDO") {
                                    Log.d(TAG, "🛑 Status CONCLUIDO detectado, parando geofencing e serviço...")
                                    println("🛑 StatusMonitoringService: Status CONCLUIDO, parando geofencing e serviço")
                                    
                                    // Remover geofences (funciona mesmo com app fechado)
                                    GeofencingService(this@StatusMonitoringService).removeAllGeofences()
                                    
                                    // Enviar notificação de conclusão antes de parar
                                    val notificationManager = NotificationManager.getInstance(this@StatusMonitoringService)
                                    notificationManager.sendConclusaoNotification(
                                        mensagem = status.mensagem.ifEmpty { "Carregamento finalizado!" }
                                    )
                                    
                                    // Parar monitoramento e serviço
                                    limparSessaoMonitoramento()
                                    stopMonitoring()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        stopForeground(STOP_FOREGROUND_REMOVE)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        stopForeground(true)
                                    }
                                    stopSelf()
                                    return@observeStatusMotorista
                                }
                                
                                // Notificar apenas para estados importantes usando NotificationManager centralizado
                                val notificationManager = NotificationManager.getInstance(this@StatusMonitoringService)
                                when (novoStatus) {
                                    "IR_ESTACIONAMENTO" -> {
                                        Log.d(TAG, "🅿️ Enviando notificação de estacionamento...")
                                        println("🅿️ StatusMonitoringService: Enviando notificação de estacionamento")
                                        notificationManager.sendMotoristaEstacionamentoNotification(
                                            motoristaNome = "Motorista"
                                        )
                                        Log.d(TAG, "✅ Notificação de estacionamento enviada")
                                        println("✅ StatusMonitoringService: Notificação de estacionamento enviada")
                                    }
                                    "CARREGANDO" -> {
                                        val vaga = status.vagaAtual ?: ""
                                        val rota = status.rotaAtual ?: ""
                                        Log.d(TAG, "🚚 Enviando notificação de carregamento - Vaga: $vaga, Rota: $rota")
                                        println("🚚 StatusMonitoringService: Enviando notificação de carregamento - Vaga=$vaga, Rota=$rota")
                                        notificationManager.sendMotoristaChamadaNotification(
                                            motoristaNome = "Motorista",
                                            vaga = vaga,
                                            rota = rota
                                        )
                                        Log.d(TAG, "✅ Notificação de carregamento enviada")
                                        println("✅ StatusMonitoringService: Notificação de carregamento enviada")
                                    }
                                    else -> {
                                        Log.d(TAG, "ℹ️ Status '$novoStatus' não requer notificação")
                                        println("ℹ️ StatusMonitoringService: Status '$novoStatus' não requer notificação")
                                    }
                                }
                                
                                lastStatus = novoStatus
                                Log.d(TAG, "📝 Último status atualizado para: '$lastStatus'")
                                println("📝 StatusMonitoringService: Último status atualizado para: '$lastStatus'")
                            } else {
                                Log.d(TAG, "ℹ️ Sem mudança de status - Último: '$lastStatus', Novo: '$novoStatus', Vazio: ${novoStatus.isEmpty()}")
                                println("ℹ️ StatusMonitoringService: Sem mudança - Último='$lastStatus', Novo='$novoStatus'")
                            }
                        } ?: run {
                            // Regra: sem status = não monitorar - parar serviço
                            Log.d(TAG, "🛑 StatusInfo é null - parando monitoramento (regra: sem status = não monitorar)")
                            println("🛑 StatusMonitoringService: Status null detectado, parando serviço")
                            GeofencingService(this@StatusMonitoringService).removeAllGeofences()
                            limparSessaoMonitoramento()
                            stopMonitoring()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(true)
                            }
                            stopSelf()
                        }
                    },
                    onError = { error: Exception ->
                        Log.e(TAG, "❌ Erro no listener de status: ${error.message}", error)
                        println("❌ StatusMonitoringService: Erro no listener - ${error.message}")
                        error.printStackTrace()
                    }
                )
                
                Log.d(TAG, "✅ Listener de status configurado e ativo")
                println("✅ StatusMonitoringService: Listener de status configurado e ativo")

                configListener?.remove()
                configListener = ConfigRepository().observeConfiguracaoBase(
                    baseId = bId,
                    onUpdate = { config -> aplicarConfiguracao(config) },
                    onError = { error ->
                        Log.e(TAG, "❌ Erro na escuta da configuração: ${error.message}", error)
                    }
                )
                Log.d(TAG, "✅ Listener de configuração do galpão ativo")
                
                // Ainda está na escala? A cada 15 minutos, sem consultar o banco no ciclo do GPS
                startPeriodicCheck(mId, bId)
                
                // ✅ Iniciar verificação de localização (a cada 30s) - detecta chegada ao raio com app em background/fechado
                startLocationCheck(mId, bId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao iniciar monitoramento: ${e.message}", e)
                println("❌ StatusMonitoringService: Erro ao iniciar monitoramento - ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Verificação a cada 15 minutos para parar o serviço se o motorista
     * saiu da escala ou o status ficou concluído.
     */
    private fun startPeriodicCheck(motoristaId: String, baseId: String) {
        periodicCheckJob?.cancel()
        periodicCheckJob = serviceScope.launch {
            while (true) {
                delay(15 * 60 * 1000L) // Verificar a cada 15 minutos
                
                try {
                    val motoristaRepository = MotoristaRepository()
                    val escalaRepository = EscalaRepository()
                    
                    // Verificar se motorista ainda está escalado
                    val escala = escalaRepository.getEscalaDoDia(baseId, motoristaId)
                    if (escala == null) {
                        Log.d(TAG, "🛑 Verificação periódica: Motorista não está mais escalado, parando serviço e removendo geofences")
                        println("🛑 StatusMonitoringService: Motorista não está mais escalado, parando serviço")
                        GeofencingService(this@StatusMonitoringService).removeAllGeofences()
                        limparSessaoMonitoramento()
                        stopMonitoring()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        stopSelf()
                        break
                    }
                    
                    // Verificar se status é CONCLUIDO
                    val status = motoristaRepository.getStatusMotorista(motoristaId, baseId)
                    if (status?.estado == "CONCLUIDO") {
                        Log.d(TAG, "🛑 Verificação periódica: Status é CONCLUIDO, parando geofencing e serviço")
                        println("🛑 StatusMonitoringService: Status CONCLUIDO detectado na verificação periódica, parando serviço")
                        GeofencingService(this@StatusMonitoringService).removeAllGeofences()
                        limparSessaoMonitoramento()
                        stopMonitoring()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        stopSelf()
                        break
                    }
                    
                    Log.d(TAG, "✅ Verificação periódica: Serviço ainda deve continuar rodando")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro na verificação periódica: ${e.message}", e)
                    // Continuar verificando mesmo em caso de erro
                }
            }
        }
    }
    
    /**
     * Verificação de localização a cada 30s - detecta quando motorista entra no raio do galpão/estacionamento.
     * Funciona com app em background ou "fechado" pois o StatusMonitoringService é foreground.
     */
    private fun startLocationCheck(motoristaId: String, baseId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "⚠️ Sem permissão de localização para verificação de raio")
            return
        }
        locationCheckJob?.cancel()
        locationCheckJob = serviceScope.launch {
            while (true) {
                val encerrar = try {
                    medirRaioUmaVez(motoristaId, baseId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro na verificação de localização: ${e.message}")
                    false
                }
                if (encerrar) break
                delay(30000L)
            }
        }
        Log.d(TAG, "📍 Verificação de localização iniciada (agora e a cada 30s)")
    }

    /**
     * Uma medição do raio usando o status e a configuração já guardados na memória.
     * A primeira ocorre ao iniciar o serviço; as seguintes, a cada 30s.
     * Retorna true quando o serviço deve encerrar (status concluído).
     */
    private suspend fun medirRaioUmaVez(motoristaId: String, baseId: String): Boolean {
        if (!configPronta || galpaoLat == 0.0 || galpaoLng == 0.0) return false

        val location = obterLocalizacaoRecente() ?: return false

        val statusAtual = lastStatus
        if (statusAtual.isNullOrBlank()) {
            Log.d(TAG, "Status nulo, sem gravar chegada")
            return false
        }
        if (statusAtual == "CONCLUIDO") {
            Log.d(TAG, "🛑 Status CONCLUIDO - parando geofencing e verificação de localização")
            GeofencingService(this@StatusMonitoringService).removeAllGeofences()
            limparSessaoMonitoramento()
            stopMonitoring()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return true
        }
        if (statusAtual == "CARREGANDO") return false

        val latGalpao = galpaoLat
        val lngGalpao = galpaoLng
        val raioGalpao = galpaoRaio
        val galpaoDist = distanceBetween(
            location.latitude, location.longitude,
            latGalpao, lngGalpao
        )

        val dentroDoGalpao = galpaoDist <= raioGalpao
        val agora = System.currentTimeMillis()
        if (!dentroDoGalpao || statusAtual != "A_CAMINHO") {
            chegueiSolicitadoEm = 0L
        }
        if (dentroDoGalpao && statusAtual == "A_CAMINHO" && agora - chegueiSolicitadoEm >= RETRY_WRITE_INTERVAL_MS) {
            // Marca a tentativa antes de gravar: se falhar (ex.: cota estourada), a próxima
            // tentativa só acontece depois do intervalo, e não a cada 30s.
            chegueiSolicitadoEm = agora
            val ok = MotoristaRepository().updateStatusMotorista(
                motoristaId, baseId, "CHEGUEI", "Chegou ao galpão (automático)"
            )
            if (ok) {
                NotifyStatusChangeWorker.enqueue(this@StatusMonitoringService, baseId, motoristaId, "CHEGUEI")
                Log.d(TAG, "✅ [BACKGROUND] Status gravado uma vez para CHEGUEI (dist: ${galpaoDist.toInt()}m)")
            }
        } else if (!dentroDoGalpao && estacionamentoLat != 0.0 && estacionamentoLng != 0.0) {
            val estDist = distanceBetween(
                location.latitude, location.longitude,
                estacionamentoLat, estacionamentoLng
            )
            val dentroDoEstacionamento = estDist <= estacionamentoRaio
            if (!dentroDoEstacionamento || statusAtual != "IR_ESTACIONAMENTO") {
                estacionamentoSolicitadoEm = 0L
            }
            if (dentroDoEstacionamento && statusAtual == "IR_ESTACIONAMENTO" && agora - estacionamentoSolicitadoEm >= RETRY_WRITE_INTERVAL_MS) {
                estacionamentoSolicitadoEm = agora
                val ok = MotoristaRepository().updateStatusMotorista(
                    motoristaId, baseId, "ESTACIONAMENTO", "Chegou ao estacionamento (automático)"
                )
                if (ok) {
                    NotifyStatusChangeWorker.enqueue(this@StatusMonitoringService, baseId, motoristaId, "ESTACIONAMENTO")
                    Log.d(TAG, "✅ [BACKGROUND] Status gravado uma vez para ESTACIONAMENTO (dist: ${estDist.toInt()}m)")
                }
            }
        }
        return false
    }

    private fun aplicarConfiguracao(config: ConfiguracaoBase?) {
        if (config == null || config.galpao.lat == 0.0 || config.galpao.lng == 0.0) {
            configPronta = false
            Log.d(TAG, "Configuração do galpão ainda indisponível")
            return
        }
        configPronta = false
        galpaoLat = config.galpao.lat
        galpaoLng = config.galpao.lng
        galpaoRaio = config.galpao.raio.toDouble()
        estacionamentoLat = config.estacionamento.lat
        estacionamentoLng = config.estacionamento.lng
        estacionamentoRaio = config.estacionamento.raio.toDouble()
        configPronta = true
        Log.d(TAG, "Configuração em memória: galpão=($galpaoLat, $galpaoLng) raio=${galpaoRaio.toInt()}m")
    }

    private suspend fun obterLocalizacaoRecente(): Location? {
        val ultima = try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erro ao ler última localização: ${e.message}")
            null
        }
        if (ultima != null && System.currentTimeMillis() - ultima.time <= IDADE_MAXIMA_LOCALIZACAO_MS) {
            return ultima
        }
        return try {
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erro ao obter localização: ${e.message}")
            null
        }
    }
    
    private fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }
    
    private fun stopMonitoring() {
        locationCheckJob?.cancel()
        locationCheckJob = null
        periodicCheckJob?.cancel()
        periodicCheckJob = null
        statusListener?.remove()
        statusListener = null
        configListener?.remove()
        configListener = null
        lastStatus = null
        configPronta = false
        chegueiSolicitadoEm = 0L
        estacionamentoSolicitadoEm = 0L
        galpaoLat = 0.0
        galpaoLng = 0.0
        galpaoRaio = 0.0
        estacionamentoLat = 0.0
        estacionamentoLng = 0.0
        estacionamentoRaio = 0.0
        Log.d(TAG, "✅ Monitoramento parado")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        Log.d(TAG, "✅ StatusMonitoringService destruído")
    }
}

