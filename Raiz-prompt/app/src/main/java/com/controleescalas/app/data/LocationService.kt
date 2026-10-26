package com.controleescalas.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.controleescalas.app.data.models.GeofenceConfig
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Serviço de geolocalização para monitoramento automático de status
 * - 100m do galpão → Muda para CHEGUEI
 * - 50m do estacionamento → Muda para ESTACIONAMENTO
 */
class LocationService(
    private val context: Context,
    private val motoristaRepository: MotoristaRepository
) {
    companion object {
        private const val TAG = "LocationService"
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 segundos
        private const val FASTEST_INTERVAL = 5000L // 5 segundos
        
        // Raios de detecção
        private const val GALPAO_RADIUS = 100.0 // metros
        private const val ESTACIONAMENTO_RADIUS = 50.0 // metros

        // Intervalo mínimo entre retentativas de gravação automática de status.
        // Sem isso, um motorista dentro do raio com a cota do Firestore estourada
        // tentava gravar a cada tick de GPS (5-10s) - milhares de writes/dia.
        private const val RETRY_WRITE_INTERVAL_MS = 10 * 60 * 1000L
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isMonitoring = false
    
    // Coordenadas padrão (devem ser configuradas pelo usuário)
    private var galpaoConfig: GeofenceConfig? = null
    private var estacionamentoConfig: GeofenceConfig? = null
    
    // Informações do motorista
    private var motoristaId: String? = null
    private var baseId: String? = null
    private var currentStatus: String? = null

    @Volatile
    private var ultimaTentativaGravacao = 0L

    /**
     * Inicializar serviço de localização
     */
    fun initialize(motoristaId: String, baseId: String) {
        this.motoristaId = motoristaId
        this.baseId = baseId
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        Log.d(TAG, "✅ LocationService inicializado para motorista: $motoristaId")
    }

    /**
     * Configurar coordenadas do galpão e estacionamento
     */
    fun setGeofences(galpao: GeofenceConfig, estacionamento: GeofenceConfig) {
        this.galpaoConfig = galpao
        this.estacionamentoConfig = estacionamento
        
        Log.d(TAG, "📍 Geofences configuradas - Galpão: (${galpao.lat}, ${galpao.lng}), Estacionamento: (${estacionamento.lat}, ${estacionamento.lng})")
    }

    /**
     * Iniciar monitoramento de localização
     */
    fun startMonitoring(currentStatus: String) {
        if (isMonitoring) {
            Log.w(TAG, "⚠️ Monitoramento já está ativo")
            return
        }

        if (motoristaId == null || baseId == null) {
            Log.e(TAG, "❌ Motorista ou base não configurados")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Permissão de localização não concedida")
            return
        }

        this.currentStatus = currentStatus
        isMonitoring = true

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                android.os.Looper.getMainLooper()
            )
            Log.d(TAG, "✅ Monitoramento de localização iniciado")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Erro ao iniciar monitoramento: ${e.message}")
        }
    }

    /**
     * Parar monitoramento
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        
        isMonitoring = false
        Log.d(TAG, "⏹️ Monitoramento de localização parado")
    }

    /**
     * Processar atualização de localização
     */
    private fun handleLocationUpdate(location: Location) {
        val mId = motoristaId ?: return
        val bId = baseId ?: return
        val status = currentStatus ?: return

        Log.d(TAG, "📍 Nova localização: (${location.latitude}, ${location.longitude})")

        // Verificar proximidade com galpão
        galpaoConfig?.let { galpao ->
            if (galpao.ativo) {
                val distanciaGalpao = calculateDistance(
                    location.latitude,
                    location.longitude,
                    galpao.lat,
                    galpao.lng
                )

                Log.d(TAG, "📏 Distância do galpão: ${distanciaGalpao.toInt()}m")

                // Se está A_CAMINHO e chegou a 100m do galpão → CHEGUEI
                if (status == "A_CAMINHO" && distanciaGalpao <= GALPAO_RADIUS) {
                    updateStatusToCheguei(mId, bId)
                }
            }
        }

        // Verificar proximidade com estacionamento
        estacionamentoConfig?.let { estacionamento ->
            if (estacionamento.ativo) {
                val distanciaEstacionamento = calculateDistance(
                    location.latitude,
                    location.longitude,
                    estacionamento.lat,
                    estacionamento.lng
                )

                Log.d(TAG, "📏 Distância do estacionamento: ${distanciaEstacionamento.toInt()}m")

                // Se foi mandado para estacionamento e chegou a 50m → ESTACIONAMENTO
                if (status == "IR_ESTACIONAMENTO" && distanciaEstacionamento <= ESTACIONAMENTO_RADIUS) {
                    updateStatusToEstacionamento(mId, bId)
                }
            }
        }
    }

    /**
     * Atualizar status para CHEGUEI
     */
    private fun updateStatusToCheguei(motoristaId: String, baseId: String) {
        val agora = System.currentTimeMillis()
        // Se a gravação falhou (ex.: cota estourada), não re-tentar a cada tick de GPS.
        if (agora - ultimaTentativaGravacao < RETRY_WRITE_INTERVAL_MS) return
        ultimaTentativaGravacao = agora

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "CHEGUEI",
                    mensagem = "Chegou ao galpão (automático)"
                )

                if (success) {
                    currentStatus = "CHEGUEI"
                    ultimaTentativaGravacao = 0L
                    Log.d(TAG, "✅ Status atualizado para CHEGUEI automaticamente")
                    NotifyStatusChangeWorker.enqueue(context, baseId, motoristaId, "CHEGUEI")
                } else {
                    Log.e(TAG, "❌ Erro ao atualizar status para CHEGUEI")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao atualizar status: ${e.message}")
            }
        }
    }

    /**
     * Atualizar status para ESTACIONAMENTO
     */
    private fun updateStatusToEstacionamento(motoristaId: String, baseId: String) {
        val agora = System.currentTimeMillis()
        // Mesmo throttle da chegada ao galpão: sem retentativa a cada tick de GPS.
        if (agora - ultimaTentativaGravacao < RETRY_WRITE_INTERVAL_MS) return
        ultimaTentativaGravacao = agora

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = motoristaRepository.updateStatusMotorista(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    estado = "ESTACIONAMENTO",
                    mensagem = "Chegou ao estacionamento (automático)"
                )

                if (success) {
                    currentStatus = "ESTACIONAMENTO"
                    ultimaTentativaGravacao = 0L
                    Log.d(TAG, "✅ Status atualizado para ESTACIONAMENTO automaticamente")
                    NotifyStatusChangeWorker.enqueue(context, baseId, motoristaId, "ESTACIONAMENTO")
                } else {
                    Log.e(TAG, "❌ Erro ao atualizar status para ESTACIONAMENTO")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao atualizar status: ${e.message}")
            }
        }
    }

    /**
     * Calcular distância entre dois pontos (Haversine formula)
     * Retorna distância em metros
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // metros
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }

    /**
     * Atualizar status atual (para sincronizar com mudanças externas)
     */
    fun updateCurrentStatus(newStatus: String) {
        currentStatus = newStatus
        Log.d(TAG, "🔄 Status atual atualizado para: $newStatus")
    }
}

