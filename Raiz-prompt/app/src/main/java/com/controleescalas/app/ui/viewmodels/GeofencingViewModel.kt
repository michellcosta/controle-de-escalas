package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.GeofencingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar geofencing e localização
 */
class GeofencingViewModel(application: Application) : AndroidViewModel(application) {
    private val geofencingService = GeofencingService(application.applicationContext)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()
    
    private val _isInsideGalpao = MutableStateFlow(false)
    val isInsideGalpao: StateFlow<Boolean> = _isInsideGalpao.asStateFlow()
    
    private val _isInsideEstacionamento = MutableStateFlow(false)
    val isInsideEstacionamento: StateFlow<Boolean> = _isInsideEstacionamento.asStateFlow()
    
    private val _distanceToGalpao = MutableStateFlow(Double.MAX_VALUE)
    val distanceToGalpao: StateFlow<Double> = _distanceToGalpao.asStateFlow()
    
    private val _distanceToEstacionamento = MutableStateFlow(Double.MAX_VALUE)
    val distanceToEstacionamento: StateFlow<Double> = _distanceToEstacionamento.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        observeGeofencingService()
    }

    /**
     * Observar mudanças no serviço de geofencing
     */
    private fun observeGeofencingService() {
        viewModelScope.launch {
            geofencingService.currentLocation.collect { location ->
                _currentLocation.value = location
            }
        }
        
        viewModelScope.launch {
            geofencingService.isLocationEnabled.collect { enabled ->
                _isLocationEnabled.value = enabled
            }
        }
        
        viewModelScope.launch {
            geofencingService.geofenceStatus.collect { statusMap ->
                statusMap[GeofencingService.GEOFENCE_GALPAO_ID]?.let { status ->
                    _isInsideGalpao.value = status.isInside
                    _distanceToGalpao.value = status.distance
                }
                
                statusMap[GeofencingService.GEOFENCE_ESTACIONAMENTO_ID]?.let { status ->
                    _isInsideEstacionamento.value = status.isInside
                    _distanceToEstacionamento.value = status.distance
                }
            }
        }
    }

    /**
     * Verificar se tem permissões de localização
     */
    fun hasLocationPermissions(): Boolean {
        return geofencingService.hasLocationPermissions()
    }

    /**
     * Obter localização atual
     */
    fun getCurrentLocation() {
        viewModelScope.launch {
            try {
                geofencingService.getCurrentLocation()
            } catch (e: SecurityException) {
                _error.value = "Permissões de localização necessárias"
            } catch (e: Exception) {
                _error.value = "Erro ao obter localização: ${e.message}"
            }
        }
    }

    /**
     * Iniciar atualizações de localização
     */
    fun startLocationUpdates() {
        try {
            geofencingService.startLocationUpdates()
        } catch (e: SecurityException) {
            _error.value = "Permissões de localização necessárias"
        } catch (e: Exception) {
            _error.value = "Erro ao iniciar atualizações: ${e.message}"
        }
    }

    /**
     * Parar atualizações de localização
     */
    fun stopLocationUpdates() {
        geofencingService.stopLocationUpdates()
    }

    /**
     * Criar geofence para galpão
     */
    fun createGalpaoGeofence(latitude: Double, longitude: Double, radius: Double = 100.0) {
        try {
            geofencingService.createGalpaoGeofence(latitude, longitude, radius)
        } catch (e: SecurityException) {
            _error.value = "Permissões de localização necessárias"
        } catch (e: Exception) {
            _error.value = "Erro ao criar geofence do galpão: ${e.message}"
        }
    }

    /**
     * Criar geofence para estacionamento
     */
    fun createEstacionamentoGeofence(latitude: Double, longitude: Double, radius: Double = 100.0) {
        try {
            geofencingService.createEstacionamentoGeofence(latitude, longitude, radius)
        } catch (e: SecurityException) {
            _error.value = "Permissões de localização necessárias"
        } catch (e: Exception) {
            _error.value = "Erro ao criar geofence do estacionamento: ${e.message}"
        }
    }

    /**
     * Remover todos os geofences
     */
    fun removeAllGeofences() {
        geofencingService.removeAllGeofences()
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
     * Obter status de localização resumido
     */
    fun getLocationStatus(): String {
        return when {
            !_isLocationEnabled.value -> "Localização desabilitada"
            _isInsideGalpao.value -> "No galpão"
            _isInsideEstacionamento.value -> "No estacionamento"
            else -> "A caminho"
        }
    }

    /**
     * Limpar erro
     */
    fun clearError() {
        _error.value = null
    }
}



