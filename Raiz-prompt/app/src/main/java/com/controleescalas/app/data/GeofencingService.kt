package com.controleescalas.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.controleescalas.app.data.repositories.MotoristaRepository
import android.util.Log
import kotlin.math.*

/**
 * Serviço para gerenciar geofencing e localização
 */
class GeofencingService(private val context: Context) {
    companion object {
        const val GEOFENCE_RADIUS_METERS = 100.0
        const val GEOFENCE_EXPIRATION_DURATION = Geofence.NEVER_EXPIRE
        const val GEOFENCE_TRANSITION_DWELL_TIME = 30000L // 30 segundos
        
        // IDs dos geofences
        const val GEOFENCE_GALPAO_ID = "galpao_geofence"
        const val GEOFENCE_ESTACIONAMENTO_ID = "estacionamento_geofence"
        
        // Intent actions
        const val ACTION_GEOFENCE_TRANSITION = "com.controleescalas.app.GEOFENCE_TRANSITION"
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val geofencingClient = LocationServices.getGeofencingClient(context)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()
    
    private val _geofenceStatus = MutableStateFlow<Map<String, GeofenceStatus>>(emptyMap())
    val geofenceStatus: StateFlow<Map<String, GeofenceStatus>> = _geofenceStatus.asStateFlow()

    // Armazenar informações do motorista
    private var motoristaId: String? = null
    private var baseId: String? = null
    private var currentStatus: String? = null
    
    // Armazenar coordenadas do galpão
    private var galpaoLat: Double? = null
    private var galpaoLng: Double? = null
    private var galpaoRadius: Double = GEOFENCE_RADIUS_METERS
    
    // Armazenar coordenadas do estacionamento
    private var estacionamentoLat: Double? = null
    private var estacionamentoLng: Double? = null
    private var estacionamentoRadius: Double = GEOFENCE_RADIUS_METERS
    
    // Flag para evitar atualizações duplicadas
    private var lastChegueiUpdateTime: Long = 0
    private var lastEstacionamentoUpdateTime: Long = 0
    private val MIN_UPDATE_INTERVAL = 30000L // 30 segundos
    
    // Propriedades para rastrear estado e reduzir logs
    private var lastIsInside: Boolean = false
    private var lastIsInsideEstacionamento: Boolean = false
    private var lastLoggedConfigCheck: Long = 0
    private val CONFIG_CHECK_INTERVAL = 300000L // Verificar configuração a cada 5 minutos
    
    // Variáveis para intervalo adaptativo de localização (otimização de bateria)
    private var lastDistance: Double = Double.MAX_VALUE
    private var currentLocationInterval: Long = 30000L // Intervalo atual em ms (inicia com 30s)
    private val INTERVAL_NEAR = 30000L // 30s quando perto do raio (< 2x raio)
    private val INTERVAL_FAR = 120000L // 120s quando longe do raio (> 2x raio)
    private val INTERVAL_UPDATE_THRESHOLD = 5000L // Só atualizar intervalo se diferença > 5s

    data class GeofenceStatus(
        val id: String,
        val isInside: Boolean,
        val distance: Double,
        val lastTransition: Long = 0L
    )

    /**
     * Verificar permissões de localização
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obter localização atual
     */
    fun getCurrentLocation(): Task<Location> {
        if (!hasLocationPermissions()) {
            throw SecurityException("Permissões de localização não concedidas")
        }
        
        return fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                _currentLocation.value = it
                _isLocationEnabled.value = true
            }
        }
    }

    private var locationCallback: LocationCallback? = null
    
    /**
     * Calcular intervalo adaptativo baseado na distância do raio
     * Quando dentro do raio: sempre usar intervalo menor (30s) para detecção rápida
     * Quando longe (> 2x raio): intervalo maior (120s) para economizar bateria
     * Quando perto mas fora (< 2x raio): intervalo menor (30s) para detecção rápida
     */
    private fun calculateAdaptiveInterval(distance: Double, radius: Double): Long {
        // Se está dentro do raio, sempre usar intervalo menor para detecção rápida
        if (distance <= radius) {
            return INTERVAL_NEAR
        }
        
        val distanceRatio = distance / radius
        return if (distanceRatio > 2.0) {
            // Longe do raio: usar intervalo maior para economizar bateria
            INTERVAL_FAR
        } else {
            // Perto do raio: usar intervalo menor para detecção rápida
            INTERVAL_NEAR
        }
    }
    
    /**
     * Atualizar intervalo de localização dinamicamente baseado na distância
     * NOTA: Não reinicia as atualizações para evitar loops e problemas
     * O intervalo será aplicado na próxima vez que startLocationUpdates for chamado
     */
    private fun updateLocationInterval(distance: Double, radius: Double) {
        val newInterval = calculateAdaptiveInterval(distance, radius)
        
        // Só atualizar se a diferença for significativa (evita atualizações desnecessárias)
        if (kotlin.math.abs(newInterval - currentLocationInterval) > INTERVAL_UPDATE_THRESHOLD) {
            val oldInterval = currentLocationInterval
            currentLocationInterval = newInterval
            lastDistance = distance
            
            // Apenas logar a mudança - não reiniciar para evitar loops
            Log.d("GeofencingService", "🔄 Intervalo adaptativo ajustado: ${oldInterval/1000}s → ${newInterval/1000}s (distância: ${distance.toInt()}m, raio: ${radius.toInt()}m, dentro: ${distance <= radius})")
            
            // Se está dentro do raio e o intervalo estava maior, reiniciar para aplicar imediatamente
            // Isso garante detecção rápida quando entra no raio
            if (distance <= radius && oldInterval > INTERVAL_NEAR && locationCallback != null) {
                Log.d("GeofencingService", "⚡ Motorista entrou no raio, reiniciando com intervalo menor para detecção rápida")
                stopLocationUpdates()
                startLocationUpdates()
            }
        }
    }
    
    /**
     * Iniciar atualizações de localização
     */
    fun startLocationUpdates() {
        if (!hasLocationPermissions()) {
            Log.w("GeofencingService", "⚠️ Permissões de localização não concedidas")
            return
        }
        
        // Parar atualizações anteriores se houver
        stopLocationUpdates()
        
        // Logar apenas uma vez ao iniciar
        Log.d("GeofencingService", "🚀 Monitoramento iniciado | Motorista: $motoristaId | Base: $baseId | Status: $currentStatus")
        if (galpaoLat != null && galpaoLng != null) {
            Log.d("GeofencingService", "   Galpão configurado: ($galpaoLat, $galpaoLng), raio: ${galpaoRadius.toInt()}m")
        } else {
            Log.w("GeofencingService", "   ⚠️ Coordenadas do galpão não configuradas! Usando fallback")
        }
        
        // Usar intervalo adaptativo baseado na última distância conhecida
        val adaptiveInterval = if (lastDistance < Double.MAX_VALUE) {
            calculateAdaptiveInterval(lastDistance, galpaoRadius)
        } else {
            INTERVAL_NEAR // Iniciar com intervalo padrão
        }
        currentLocationInterval = adaptiveInterval
        
        // Usar prioridade mais baixa para economizar bateria
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, // Mudado de HIGH_ACCURACY para economizar bateria
            adaptiveInterval
        ).apply {
            setMinUpdateIntervalMillis(adaptiveInterval / 2) // Mínimo: metade do intervalo
            setMaxUpdateDelayMillis(adaptiveInterval * 2) // Máximo: dobro do intervalo
        }.build()
        
        Log.d("GeofencingService", "⚡ Intervalo adaptativo: ${adaptiveInterval/1000}s (distância: ${if (lastDistance < Double.MAX_VALUE) "${lastDistance.toInt()}m" else "desconhecida"})")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    _currentLocation.value = location
                    _isLocationEnabled.value = true
                    updateGeofenceStatus(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            null
        )
        
        // Verificar localização atual imediatamente (para motoristas que já estão dentro do raio)
        // IMPORTANTE: Esta verificação garante que motoristas que já estão dentro do raio quando adicionados à escala
        // sejam detectados e atualizados imediatamente, mesmo em segundo plano
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                Log.d("GeofencingService", "📍 Verificando localização atual imediatamente: (${it.latitude}, ${it.longitude})")
                Log.d("GeofencingService", "   Motorista: $motoristaId, Base: $baseId, Status: $currentStatus")
                Log.d("GeofencingService", "   Galpão: ($galpaoLat, $galpaoLng), Raio: ${galpaoRadius.toInt()}m")
                _currentLocation.value = it
                _isLocationEnabled.value = true
                // Resetar lastIsInside para garantir que a primeira detecção seja tratada corretamente
                // Isso é importante quando o motorista é adicionado à escala e já está dentro do raio
                lastIsInside = false
                updateGeofenceStatus(it)
            } ?: run {
                Log.d("GeofencingService", "ℹ️ Nenhuma localização conhecida ainda, solicitando atualização única...")
                lastIsInside = false
                requestOneTimeLocationUpdate()
            }
        }.addOnFailureListener { exception ->
            Log.w("GeofencingService", "⚠️ Erro ao obter última localização: ${exception.message}")
            lastIsInside = false
            requestOneTimeLocationUpdate()
        }
    }

    /**
     * Parar atualizações de localização
     */
    fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
            Log.d("GeofencingService", "⏹️ Atualizações de localização paradas")
        }
    }

    /**
     * Configurar informações do motorista
     */
    fun setMotoristaInfo(motoristaId: String, baseId: String, currentStatus: String) {
        this.motoristaId = motoristaId
        this.baseId = baseId
        this.currentStatus = currentStatus
        
        // Resetar estado de detecção para garantir que a primeira verificação seja tratada corretamente
        // Isso é importante quando o motorista é adicionado à escala e já está dentro do raio
        lastIsInside = false
        lastChegueiUpdateTime = 0L
        lastEstacionamentoUpdateTime = 0L
        lastDistance = Double.MAX_VALUE
        
        Log.d("GeofencingService", "✅ Informações do motorista configuradas: $motoristaId, status: $currentStatus (estado resetado para primeira detecção)")
    }

    /**
     * Atualizar status atual (chamado quando status muda externamente)
     */
    fun updateCurrentStatus(newStatus: String) {
        this.currentStatus = newStatus
        Log.d("GeofencingService", "🔄 Status atual atualizado para: $newStatus")
    }

    /**
     * Verificar localização atual imediatamente e atualizar status se motorista já estiver dentro do raio.
     * Usado quando o admin altera o raio - motorista que já está dentro deve aparecer como CHEGUEI.
     * Se lastLocation for null, solicita uma atualização temporária de alta precisão.
     */
    fun checkCurrentLocationImmediately() {
        if (!hasLocationPermissions() || motoristaId == null || baseId == null) return
        if (galpaoLat == null || galpaoLng == null) return
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d("GeofencingService", "📍 Checagem imediata: localização obtida, verificando se dentro do raio")
                lastIsInside = false
                updateGeofenceStatus(location)
            } else {
                Log.d("GeofencingService", "📍 Checagem imediata: lastLocation null, solicitando atualização temporária")
                requestOneTimeLocationUpdate()
            }
        }.addOnFailureListener { e ->
            Log.w("GeofencingService", "⚠️ Erro ao obter localização para checagem imediata: ${e.message}")
            requestOneTimeLocationUpdate()
        }
    }
    
    /**
     * Solicitar uma única atualização de localização (fallback quando lastLocation é null)
     */
    private fun requestOneTimeLocationUpdate() {
        if (!hasLocationPermissions()) return
        
        val oneTimeRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).setMaxUpdates(1).build()
        
        val oneTimeCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d("GeofencingService", "📍 Atualização única obtida, verificando se dentro do raio")
                    fusedLocationClient.removeLocationUpdates(this)
                    lastIsInside = false
                    updateGeofenceStatus(location)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(oneTimeRequest, oneTimeCallback, null)
        } catch (e: SecurityException) {
            Log.w("GeofencingService", "⚠️ Permissões não concedidas para requestOneTimeLocationUpdate")
        }
    }
    
    /**
     * Criar geofence para galpão
     */
    fun createGalpaoGeofence(latitude: Double, longitude: Double, radius: Double = GEOFENCE_RADIUS_METERS) {
        if (!hasLocationPermissions()) return
        
        // Armazenar coordenadas do galpão
        this.galpaoLat = latitude
        this.galpaoLng = longitude
        this.galpaoRadius = radius
        
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_GALPAO_ID)
            .setCircularRegion(latitude, longitude, radius.toFloat())
            .setExpirationDuration(GEOFENCE_EXPIRATION_DURATION)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT or
                Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(GEOFENCE_TRANSITION_DWELL_TIME.toInt())
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = createGeofencePendingIntent()

        // Remover geofences existentes antes de criar novos (evita erro 1004)
        // Checagem imediata: se motorista já está dentro do novo raio, atualizar para CHEGUEI
        checkCurrentLocationImmediately()
        
        geofencingClient.removeGeofences(listOf(GEOFENCE_GALPAO_ID))
            .addOnSuccessListener {
                Log.d("GeofencingService", "✅ Geofences antigos removidos, criando novo geofence do galpão...")
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                    .addOnSuccessListener {
                        Log.d("GeofencingService", "✅ Geofence do galpão criado: ($latitude, $longitude), raio: ${radius.toInt()}m")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("GeofencingService", "❌ Erro ao criar geofence: ${exception.message}", exception)
                    }
            }
            .addOnFailureListener { exception ->
                // Mesmo se falhar ao remover, tentar adicionar (pode não existir geofence anterior)
                Log.w("GeofencingService", "⚠️ Erro ao remover geofences antigos (pode não existir): ${exception.message}")
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                    .addOnSuccessListener {
                        Log.d("GeofencingService", "✅ Geofence do galpão criado: ($latitude, $longitude), raio: ${radius.toInt()}m")
                    }
                    .addOnFailureListener { addException ->
                        Log.e("GeofencingService", "❌ Erro ao criar geofence: ${addException.message}", addException)
                    }
            }
    }

    /**
     * Criar geofence para estacionamento
     */
    fun createEstacionamentoGeofence(latitude: Double, longitude: Double, radius: Double = GEOFENCE_RADIUS_METERS) {
        if (!hasLocationPermissions()) return
        
        // Armazenar coordenadas do estacionamento
        this.estacionamentoLat = latitude
        this.estacionamentoLng = longitude
        this.estacionamentoRadius = radius
        
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ESTACIONAMENTO_ID)
            .setCircularRegion(latitude, longitude, radius.toFloat())
            .setExpirationDuration(GEOFENCE_EXPIRATION_DURATION)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT or
                Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(GEOFENCE_TRANSITION_DWELL_TIME.toInt())
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = createGeofencePendingIntent()

        // Remover geofences existentes antes de criar novos (evita erro 1004)
        geofencingClient.removeGeofences(listOf(GEOFENCE_ESTACIONAMENTO_ID))
            .addOnSuccessListener {
                Log.d("GeofencingService", "✅ Geofences antigos removidos, criando novo geofence do estacionamento...")
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                    .addOnSuccessListener {
                        Log.d("GeofencingService", "✅ Geofence do estacionamento criado: ($latitude, $longitude), raio: ${radius}m")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("GeofencingService", "❌ Erro ao criar geofence do estacionamento: ${exception.message}", exception)
                    }
            }
            .addOnFailureListener { exception ->
                // Mesmo se falhar ao remover, tentar adicionar (pode não existir geofence anterior)
                Log.w("GeofencingService", "⚠️ Erro ao remover geofences antigos (pode não existir): ${exception.message}")
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                    .addOnSuccessListener {
                        Log.d("GeofencingService", "✅ Geofence do estacionamento criado: ($latitude, $longitude), raio: ${radius}m")
                    }
                    .addOnFailureListener { addException ->
                        Log.e("GeofencingService", "❌ Erro ao criar geofence do estacionamento: ${addException.message}", addException)
                    }
            }
    }

    /**
     * Remover todos os geofences
     */
    fun removeAllGeofences() {
        geofencingClient.removeGeofences(listOf(GEOFENCE_GALPAO_ID, GEOFENCE_ESTACIONAMENTO_ID))
    }

    /**
     * Criar PendingIntent para geofence
     */
    private fun createGeofencePendingIntent(): PendingIntent {
        val intent = Intent(ACTION_GEOFENCE_TRANSITION).apply {
            setClass(context, com.controleescalas.app.GeofenceBroadcastReceiver::class.java)
        }
        
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Atualizar status dos geofences baseado na localização atual
     */
    private fun updateGeofenceStatus(location: Location) {
        val currentStatusMap = _geofenceStatus.value.toMutableMap()
        
        // Usar coordenadas armazenadas ou fallback para coordenadas fixas
        val galpaoLatitude = galpaoLat ?: -23.400000
        val galpaoLongitude = galpaoLng ?: -46.500000
        
        val galpaoDistance = calculateDistance(
            location.latitude, location.longitude,
            galpaoLatitude, galpaoLongitude
        )
        
        val isInside = galpaoDistance <= galpaoRadius
        
        // Otimização: Ajustar intervalo de localização dinamicamente baseado na distância
        // Só ajustar se a distância mudou significativamente (evita ajustes desnecessários)
        // IMPORTANTE: Se está dentro do raio, sempre garantir intervalo menor para detecção rápida
        val distanceChanged = kotlin.math.abs(galpaoDistance - lastDistance) > (galpaoRadius * 0.2) // 20% do raio
        if (distanceChanged || lastDistance == Double.MAX_VALUE || (isInside && currentLocationInterval > INTERVAL_NEAR)) {
            updateLocationInterval(galpaoDistance, galpaoRadius)
        }
        
        // Log para debug
        Log.d("GeofencingService", "📊 updateGeofenceStatus: distância=${galpaoDistance.toInt()}m, dentro=${isInside}, raio=${galpaoRadius.toInt()}m, intervalo=${currentLocationInterval/1000}s, motorista=$motoristaId, base=$baseId, status=$currentStatus")
        val galpaoStatus = GeofenceStatus(
            id = GEOFENCE_GALPAO_ID,
            isInside = isInside,
            distance = galpaoDistance
        )
        
        currentStatusMap[GEOFENCE_GALPAO_ID] = galpaoStatus
        
        // Calcular distância e status do estacionamento
        estacionamentoLat?.let { estLat ->
            estacionamentoLng?.let { estLng ->
                val estacionamentoDistance = calculateDistance(
                    location.latitude, location.longitude,
                    estLat, estLng
                )
                
                val isInsideEstacionamento = estacionamentoDistance <= estacionamentoRadius
                val estacionamentoStatus = GeofenceStatus(
                    id = GEOFENCE_ESTACIONAMENTO_ID,
                    isInside = isInsideEstacionamento,
                    distance = estacionamentoDistance
                )
                
                currentStatusMap[GEOFENCE_ESTACIONAMENTO_ID] = estacionamentoStatus
                
                // Logar quando entrar/sair do estacionamento
                val estacionamentoStatusChanged = isInsideEstacionamento != lastIsInsideEstacionamento
                if (estacionamentoStatusChanged) {
                    if (isInsideEstacionamento) {
                        Log.d("GeofencingService", "✅ Motorista entrou no raio do estacionamento (${estacionamentoDistance.toInt()}m)")
                    } else {
                        Log.d("GeofencingService", "📍 Motorista saiu do raio do estacionamento (${estacionamentoDistance.toInt()}m)")
                    }
                    lastIsInsideEstacionamento = isInsideEstacionamento
                }
                
                // Atualizar status do motorista se estiver dentro do raio do estacionamento
                if (isInsideEstacionamento && motoristaId != null && baseId != null) {
                    val status = currentStatus ?: "A_CAMINHO"
                    val currentTime = System.currentTimeMillis()
                    
                    // Não atualizar para ESTACIONAMENTO se estiver carregando
                    // O status CARREGANDO tem prioridade sobre ESTACIONAMENTO
                    if (status == "CARREGANDO") {
                        Log.d("GeofencingService", "📍 Motorista dentro do raio do estacionamento, mas está carregando (mantendo CARREGANDO)")
                    }
                    // Atualizar para ESTACIONAMENTO apenas se estiver em estados apropriados
                    // (não atualizar se já estiver em estados finais como CONCLUIDO ou ESTACIONAMENTO)
                    else {
                        val estadosPermitidos = listOf("IR_ESTACIONAMENTO", "CHEGUEI", "A_CAMINHO")
                        val estadosFinais = listOf("CONCLUIDO", "ESTACIONAMENTO")
                        
                        if (status in estadosPermitidos && status !in estadosFinais) {
                            // Evitar atualizações muito frequentes
                            val shouldUpdate = if (estacionamentoStatusChanged && isInsideEstacionamento) {
                                true // Atualizar imediatamente ao entrar no raio
                            } else {
                                currentTime - lastEstacionamentoUpdateTime > MIN_UPDATE_INTERVAL
                            }
                            
                            if (shouldUpdate) {
                                Log.d("GeofencingService", "🔄 Atualizando status para ESTACIONAMENTO (distância: ${estacionamentoDistance.toInt()}m, status atual: $status)")
                                updateStatusToEstacionamento(motoristaId!!, baseId!!)
                                lastEstacionamentoUpdateTime = currentTime
                            } else if (isInsideEstacionamento) {
                                val timeRemaining = (MIN_UPDATE_INTERVAL - (currentTime - lastEstacionamentoUpdateTime)) / 1000
                                Log.d("GeofencingService", "⏳ Dentro do raio do estacionamento, aguardando intervalo mínimo (${timeRemaining}s)")
                            }
                        } else if (estacionamentoStatusChanged && isInsideEstacionamento) {
                            Log.d("GeofencingService", "⏳ Dentro do raio do estacionamento, mas status não permitido ($status)")
                        }
                    }
                }
            }
        }
        
        // Logar apenas quando houver mudança de estado (entrar/sair do raio do galpão)
        val insideStatusChanged = isInside != lastIsInside
        if (insideStatusChanged) {
            if (isInside) {
                Log.d("GeofencingService", "✅ Motorista entrou no raio do galpão (${galpaoDistance.toInt()}m)")
            } else {
                Log.d("GeofencingService", "📍 Motorista saiu do raio do galpão (${galpaoDistance.toInt()}m)")
            }
        }
        // Sempre atualizar lastIsInside para rastrear o estado atual
        lastIsInside = isInside
        
        // Verificar configuração periodicamente (a cada 5 minutos) e logar se houver problema
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLoggedConfigCheck > CONFIG_CHECK_INTERVAL) {
            var hasProblem = false
            if (motoristaId == null || baseId == null) {
                Log.w("GeofencingService", "⚠️ Motorista não configurado! motoristaId: $motoristaId, baseId: $baseId")
                hasProblem = true
            }
            if (galpaoLat == null || galpaoLng == null) {
                Log.w("GeofencingService", "⚠️ Coordenadas do galpão não configuradas! Usando fallback: ($galpaoLatitude, $galpaoLongitude)")
                hasProblem = true
            }
            if (estacionamentoLat == null || estacionamentoLng == null) {
                Log.d("GeofencingService", "ℹ️ Coordenadas do estacionamento não configuradas (opcional)")
            }
            if (hasProblem) {
                lastLoggedConfigCheck = currentTime
            }
        }
        
        // Atualizar status do motorista se estiver dentro do raio do galpão
        if (isInside && motoristaId != null && baseId != null) {
            val status = currentStatus ?: "A_CAMINHO"
            
            // Não atualizar para CHEGUEI se estiver indo para o estacionamento
            // O status IR_ESTACIONAMENTO tem prioridade sobre CHEGUEI
            if (status == "IR_ESTACIONAMENTO") {
                Log.d("GeofencingService", "📍 Motorista dentro do raio do galpão, mas indo para estacionamento (mantendo IR_ESTACIONAMENTO)")
            }
            // Caso especial: se estava em ESTACIONAMENTO e entrou no raio do galpão, atualizar imediatamente
            else if (status == "ESTACIONAMENTO" && insideStatusChanged && isInside) {
                Log.d("GeofencingService", "🔄 Atualizando status de ESTACIONAMENTO para CHEGUEI (motorista entrou no raio do galpão)")
                updateStatusToCheguei(motoristaId!!, baseId!!)
                lastChegueiUpdateTime = currentTime
            }
            // Não atualizar se já está em estados que não devem ser alterados automaticamente
            else {
                val estadosFinais = listOf("CARREGANDO", "CONCLUIDO", "CHEGUEI")
                if (status !in estadosFinais) {
                    // Se acabou de entrar no raio OU se é a primeira vez que detectamos que está dentro
                    // (quando lastIsInside ainda é false mas isInside é true)
                    val isFirstDetection = !lastIsInside && isInside
                    val justEntered = insideStatusChanged && isInside
                    
                    // Atualizar imediatamente se:
                    // 1. Acabou de entrar no raio (justEntered)
                    // 2. É a primeira detecção que está dentro (isFirstDetection) - IMPORTANTE para motoristas que já estão dentro quando adicionados
                    // 3. Nunca atualizou antes (lastChegueiUpdateTime == 0) - IMPORTANTE para garantir atualização na primeira vez
                    // 4. Ou se passou o intervalo mínimo desde a última atualização
                    // IMPORTANTE: Se está dentro do raio e status é A_CAMINHO, sempre atualizar na primeira detecção
                    // Isso garante que motoristas que já estão dentro do raio quando adicionados à escala sejam atualizados imediatamente
                    val shouldUpdate = if (justEntered || isFirstDetection || lastChegueiUpdateTime == 0L) {
                        true // Atualizar imediatamente ao entrar no raio, na primeira detecção, ou se nunca atualizou
                    } else {
                        // Se já está dentro mas não acabou de entrar, verificar intervalo mínimo
                        // Mas se passou muito tempo (mais de 1 minuto), atualizar de qualquer forma para garantir
                        val timeSinceLastUpdate = currentTime - lastChegueiUpdateTime
                        timeSinceLastUpdate > MIN_UPDATE_INTERVAL || timeSinceLastUpdate > 60000L // 1 minuto como fallback
                    }
                    
                    if (shouldUpdate) {
                        Log.d("GeofencingService", "🔄 Atualizando status para CHEGUEI (distância: ${galpaoDistance.toInt()}m, status atual: $status, primeira detecção: $isFirstDetection, acabou de entrar: $justEntered, última atualização: ${if (lastChegueiUpdateTime == 0L) "nunca" else "${(currentTime - lastChegueiUpdateTime)/1000}s atrás"})")
                        updateStatusToCheguei(motoristaId!!, baseId!!)
                        lastChegueiUpdateTime = currentTime
                    } else if (isInside) {
                        val timeRemaining = (MIN_UPDATE_INTERVAL - (currentTime - lastChegueiUpdateTime)) / 1000
                        Log.d("GeofencingService", "⏳ Dentro do raio do galpão, aguardando intervalo mínimo (${timeRemaining}s restantes, última atualização: ${(currentTime - lastChegueiUpdateTime)/1000}s atrás)")
                    }
                } else {
                    // Se já está em estado final mas está dentro do raio, logar para debug
                    Log.d("GeofencingService", "ℹ️ Motorista dentro do raio do galpão, mas status é $status (não atualizando)")
                }
            }
        }
        
        _geofenceStatus.value = currentStatusMap
    }

    /**
     * Atualizar status do motorista para CHEGUEI
     */
    private fun updateStatusToCheguei(motoristaId: String, baseId: String) {
        val motoristaRepository = MotoristaRepository()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("GeofencingService", "🔄 Tentando atualizar status para CHEGUEI...")
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "CHEGUEI",
                    mensagem = "Chegou ao galpão (automático)"
                )
                
                if (success) {
                    currentStatus = "CHEGUEI"
                    Log.d("GeofencingService", "✅ Status atualizado para CHEGUEI automaticamente")
                    NotifyStatusChangeWorker.enqueue(context, baseId, motoristaId, "CHEGUEI")
                } else {
                    Log.e("GeofencingService", "❌ Erro ao atualizar status para CHEGUEI")
                }
            } catch (e: Exception) {
                Log.e("GeofencingService", "❌ Erro ao atualizar status: ${e.message}", e)
            }
        }
    }

    /**
     * Atualizar status do motorista para ESTACIONAMENTO
     */
    private fun updateStatusToEstacionamento(motoristaId: String, baseId: String) {
        val motoristaRepository = MotoristaRepository()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("GeofencingService", "🔄 Tentando atualizar status para ESTACIONAMENTO...")
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "ESTACIONAMENTO",
                    mensagem = "Chegou ao estacionamento (automático)"
                )
                
                if (success) {
                    currentStatus = "ESTACIONAMENTO"
                    Log.d("GeofencingService", "✅ Status atualizado para ESTACIONAMENTO automaticamente")
                    NotifyStatusChangeWorker.enqueue(context, baseId, motoristaId, "ESTACIONAMENTO")
                } else {
                    Log.e("GeofencingService", "❌ Erro ao atualizar status para ESTACIONAMENTO")
                }
            } catch (e: Exception) {
                Log.e("GeofencingService", "❌ Erro ao atualizar status: ${e.message}", e)
            }
        }
    }

    /**
     * Calcular distância entre duas coordenadas (em metros)
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }

    /**
     * Verificar se está dentro do galpão
     */
    fun isInsideGalpao(): Boolean {
        return _geofenceStatus.value[GEOFENCE_GALPAO_ID]?.isInside ?: false
    }

    /**
     * Verificar se está dentro do estacionamento
     */
    fun isInsideEstacionamento(): Boolean {
        return _geofenceStatus.value[GEOFENCE_ESTACIONAMENTO_ID]?.isInside ?: false
    }

    /**
     * Obter distância até o galpão
     */
    fun getDistanceToGalpao(): Double {
        return _geofenceStatus.value[GEOFENCE_GALPAO_ID]?.distance ?: Double.MAX_VALUE
    }

    /**
     * Obter distância até o estacionamento
     */
    fun getDistanceToEstacionamento(): Double {
        return _geofenceStatus.value[GEOFENCE_ESTACIONAMENTO_ID]?.distance ?: Double.MAX_VALUE
    }
}
