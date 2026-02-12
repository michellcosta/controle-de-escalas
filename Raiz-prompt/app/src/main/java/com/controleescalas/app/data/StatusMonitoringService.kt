package com.controleescalas.app.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.controleescalas.app.MainActivity
import com.controleescalas.app.R
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.ui.screens.DriverStatusInfo
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Foreground Service para monitorar mudanças de status do motorista
 * Funciona mesmo quando o app está fechado
 */
class StatusMonitoringService : Service() {
    
    companion object {
        private const val TAG = "StatusMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "status_monitoring_channel"
        
        const val ACTION_START = "com.controleescalas.app.START_MONITORING"
        const val ACTION_STOP = "com.controleescalas.app.STOP_MONITORING"
        
        const val EXTRA_MOTORISTA_ID = "motorista_id"
        const val EXTRA_BASE_ID = "base_id"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statusListener: ListenerRegistration? = null
    private var motoristaId: String? = null
    private var baseId: String? = null
    private var lastStatus: String? = null
    private var notificationService: NotificationService? = null
    private var periodicCheckJob: kotlinx.coroutines.Job? = null
    
    override fun onCreate() {
        super.onCreate()
        // Inicializar NotificationService aqui, quando o Context já está disponível
        notificationService = NotificationService(this)
        Log.d(TAG, "🔧 onCreate chamado - criando canal de notificação...")
        println("🔧 StatusMonitoringService.onCreate: Criando serviço")
        createNotificationChannel()
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
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "🛑 Parando monitoramento...")
                println("🛑 StatusMonitoringService: Parando monitoramento")
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
            }
            else -> {
                Log.w(TAG, "⚠️ Action desconhecida ou null: ${intent?.action}")
                println("⚠️ StatusMonitoringService: Action desconhecida: ${intent?.action}")
            }
        }
        return START_STICKY // Reinicia o serviço se for morto pelo sistema
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento de Status",
                android.app.NotificationManager.IMPORTANCE_LOW // Baixa prioridade para não incomodar
            ).apply {
                description = "Monitora mudanças de status do motorista"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Controle de Escalas")
            .setContentText("Monitorando seu status...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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
                                    Log.d(TAG, "🛑 Status CONCLUIDO detectado, parando serviço...")
                                    println("🛑 StatusMonitoringService: Status CONCLUIDO, parando serviço")
                                    
                                    // Enviar notificação de conclusão antes de parar
                                    val notificationManager = NotificationManager.getInstance(this@StatusMonitoringService)
                                    notificationManager.sendConclusaoNotification(
                                        mensagem = status.mensagem.ifEmpty { "Carregamento finalizado!" }
                                    )
                                    
                                    // Parar monitoramento e serviço
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
                            Log.d(TAG, "⚠️ StatusInfo é null - não há status no Firestore")
                            println("⚠️ StatusMonitoringService: StatusInfo é null")
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
                
                // ✅ Iniciar verificação periódica (a cada 5 minutos) para garantir que o serviço pare se necessário
                startPeriodicCheck(mId, bId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao iniciar monitoramento: ${e.message}", e)
                println("❌ StatusMonitoringService: Erro ao iniciar monitoramento - ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Verificação periódica para garantir que o serviço pare se status for CONCLUIDO
     * ou se motorista não estiver mais escalado
     */
    private fun startPeriodicCheck(motoristaId: String, baseId: String) {
        periodicCheckJob?.cancel()
        periodicCheckJob = serviceScope.launch {
            while (true) {
                delay(5 * 60 * 1000L) // Verificar a cada 5 minutos
                
                try {
                    val motoristaRepository = MotoristaRepository()
                    val escalaRepository = EscalaRepository()
                    
                    // Verificar se motorista ainda está escalado
                    val escala = escalaRepository.getEscalaDoDia(baseId, motoristaId)
                    if (escala == null) {
                        Log.d(TAG, "🛑 Verificação periódica: Motorista não está mais escalado, parando serviço")
                        println("🛑 StatusMonitoringService: Motorista não está mais escalado, parando serviço")
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
                        Log.d(TAG, "🛑 Verificação periódica: Status é CONCLUIDO, parando serviço")
                        println("🛑 StatusMonitoringService: Status CONCLUIDO detectado na verificação periódica, parando serviço")
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
    
    private fun stopMonitoring() {
        periodicCheckJob?.cancel()
        periodicCheckJob = null
        statusListener?.remove()
        statusListener = null
        lastStatus = null
        Log.d(TAG, "✅ Monitoramento parado")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        Log.d(TAG, "✅ StatusMonitoringService destruído")
    }
}

