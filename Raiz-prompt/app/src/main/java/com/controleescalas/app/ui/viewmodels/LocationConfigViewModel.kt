package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.repositories.ConfigRepository
import com.controleescalas.app.ui.screens.GeofenceUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val configRepository = ConfigRepository()
    
    private val _galpao = MutableStateFlow(GeofenceUi(0.0, 0.0, 100))
    val galpao: StateFlow<GeofenceUi> = _galpao.asStateFlow()
    
    private val _estacionamento = MutableStateFlow<GeofenceUi?>(null)
    val estacionamento: StateFlow<GeofenceUi?> = _estacionamento.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadConfig(baseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = configRepository.getConfiguracaoBase(baseId)
                if (config != null) {
                    config.galpao?.let {
                        _galpao.value = GeofenceUi(it.lat, it.lng, it.raio)
                    }
                    config.estacionamento?.let {
                        _estacionamento.value = GeofenceUi(it.lat, it.lng, it.raio)
                    }
                }
            } catch (e: Exception) {
                // Erro silencioso ou log
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveGalpao(baseId: String, lat: Double, lng: Double, raio: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = configRepository.saveGeofenceConfig(baseId, "galpao", lat, lng, raio)
                if (success) {
                    _galpao.value = GeofenceUi(lat, lng, raio)
                    _message.value = "Configuração do Galpão salva!"
                } else {
                    _message.value = "Erro ao salvar Galpão"
                }
            } catch (e: Exception) {
                _message.value = "Erro: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveEstacionamento(baseId: String, lat: Double, lng: Double, raio: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = configRepository.saveGeofenceConfig(baseId, "estacionamento", lat, lng, raio)
                if (success) {
                    _estacionamento.value = GeofenceUi(lat, lng, raio)
                    _message.value = "Configuração do Estacionamento salva!"
                } else {
                    _message.value = "Erro ao salvar Estacionamento"
                }
            } catch (e: Exception) {
                _message.value = "Erro: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateGalpaoUi(lat: Double, lng: Double) {
        _galpao.value = _galpao.value.copy(lat = lat, lng = lng)
    }
    
    fun updateEstacionamentoUi(lat: Double, lng: Double) {
        val current = _estacionamento.value ?: GeofenceUi(lat, lng, 50)
        _estacionamento.value = current.copy(lat = lat, lng = lng)
    }

    fun clearMessage() {
        _message.value = null
    }
}
