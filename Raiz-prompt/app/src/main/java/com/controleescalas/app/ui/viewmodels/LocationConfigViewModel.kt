package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.models.GeoPonto
import com.controleescalas.app.data.models.GeofenceConfig
import com.controleescalas.app.data.repositories.ConfigRepository
import com.controleescalas.app.ui.screens.GeofenceUi
import com.controleescalas.app.utils.GeoArea
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
                    config.galpao.let {
                        _galpao.value = it.toUi()
                    }
                    if (GeoArea.configurado(config.estacionamento)) {
                        _estacionamento.value = config.estacionamento.toUi()
                    }
                }
            } catch (e: Exception) {
                // Erro silencioso ou log
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addPonto(tipo: String, lat: Double, lng: Double) {
        val ponto = GeoPonto(lat, lng)
        if (tipo == "galpao") {
            _galpao.value = _galpao.value.copy(pontos = _galpao.value.pontos + ponto)
        } else {
            val atual = _estacionamento.value ?: GeofenceUi(0.0, 0.0, 50)
            _estacionamento.value = atual.copy(pontos = atual.pontos + ponto)
        }
    }

    fun desfazerPonto(tipo: String) {
        if (tipo == "galpao") {
            val pontos = _galpao.value.pontos
            if (pontos.isNotEmpty()) {
                _galpao.value = _galpao.value.copy(pontos = pontos.dropLast(1))
            }
        } else {
            val atual = _estacionamento.value ?: return
            if (atual.pontos.isNotEmpty()) {
                _estacionamento.value = atual.copy(pontos = atual.pontos.dropLast(1))
            }
        }
    }

    fun limparArea(tipo: String) {
        if (tipo == "galpao") {
            _galpao.value = GeofenceUi(0.0, 0.0, 100)
        } else {
            _estacionamento.value = GeofenceUi(0.0, 0.0, 50)
        }
    }

    fun updateGalpaoUi(lat: Double, lng: Double) {
        _galpao.value = _galpao.value.copy(lat = lat, lng = lng)
    }

    fun updateEstacionamentoUi(lat: Double, lng: Double) {
        val atual = _estacionamento.value ?: GeofenceUi(lat, lng, 50)
        _estacionamento.value = atual.copy(lat = lat, lng = lng)
    }

    fun saveGalpao(baseId: String) {
        saveArea(baseId, "galpao", _galpao.value)
    }

    fun saveGalpao(baseId: String, lat: Double, lng: Double, raio: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val geofence = GeofenceConfig(lat = lat, lng = lng, raio = raio, ativo = true)
                val success = configRepository.saveGeofenceConfig(baseId, "galpao", geofence)
                if (success) {
                    _galpao.value = geofence.toUi()
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

    fun saveEstacionamento(baseId: String) {
        val atual = _estacionamento.value ?: return
        saveArea(baseId, "estacionamento", atual)
    }

    fun saveEstacionamento(baseId: String, lat: Double, lng: Double, raio: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val geofence = GeofenceConfig(lat = lat, lng = lng, raio = raio, ativo = true)
                val success = configRepository.saveGeofenceConfig(baseId, "estacionamento", geofence)
                if (success) {
                    _estacionamento.value = geofence.toUi()
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

    private fun saveArea(baseId: String, tipo: String, ui: GeofenceUi) {
        if (ui.pontos.size < 3) {
            _message.value = "Marque pelo menos 3 pontos no mapa"
            return
        }
        val geofence = ui.toGeofenceConfig()
        android.util.Log.d(
            "LocationConfigVM",
            "📤 Salvando $tipo - baseId=$baseId, pontos=${geofence.pontos.size}, centro=(${geofence.lat}, ${geofence.lng})"
        )
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = configRepository.saveGeofenceConfig(baseId, tipo, geofence)
                if (success) {
                    if (tipo == "galpao") {
                        _galpao.value = geofence.toUi()
                        _message.value = "Área do galpão salva"
                    } else {
                        _estacionamento.value = geofence.toUi()
                        _message.value = "Área do estacionamento salva"
                    }
                } else {
                    _message.value = "Erro ao salvar área"
                }
            } catch (e: Exception) {
                _message.value = "Erro: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun GeofenceConfig.toUi(): GeofenceUi {
        return GeofenceUi(
            lat = lat,
            lng = lng,
            raioM = raio,
            ativo = ativo,
            pontos = pontos
        )
    }

    private fun GeofenceUi.toGeofenceConfig(): GeofenceConfig {
        val centro = GeoArea.centroide(pontos)
        return GeofenceConfig(
            lat = centro.lat,
            lng = centro.lng,
            raio = GeoArea.raioEnvolvente(pontos),
            ativo = true,
            pontos = pontos
        )
    }
}
