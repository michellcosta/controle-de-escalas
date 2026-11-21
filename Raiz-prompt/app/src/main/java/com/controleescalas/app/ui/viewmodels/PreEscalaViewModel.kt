package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.Repository
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.utils.BrasilDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pré-escala rápida: "quem vai trabalhar no turno + rota", sem ondas.
 * Salva na escala do dia (onda "PRÉ-ESCALA") para a montagem das ondas
 * depois só precisar de vaga/sacas.
 */
class PreEscalaViewModel(application: Application) : AndroidViewModel(application) {
    private val escalaRepository = EscalaRepository()
    private val repository = Repository()

    companion object {
        const val NOME_ONDA_PRE_ESCALA = "PRÉ-ESCALA"
    }

    private val _motoristas = MutableStateFlow<List<AdminMotoristaCardData>>(emptyList())
    val motoristas: StateFlow<List<AdminMotoristaCardData>> = _motoristas.asStateFlow()

    // Disponibilidade declarada na data selecionada: motoristaId -> resposta
    // (o dialog deriva o estado com estadoNoTurno(turno))
    private val _disponibilidadeRespostas = MutableStateFlow<Map<String, com.controleescalas.app.data.models.DisponibilidadeMotorista>>(emptyMap())
    val disponibilidadeRespostas: StateFlow<Map<String, com.controleescalas.app.data.models.DisponibilidadeMotorista>> = _disponibilidadeRespostas.asStateFlow()

    private val _rotasRecentes = MutableStateFlow<List<String>>(emptyList())
    val rotasRecentes: StateFlow<List<String>> = _rotasRecentes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentBaseId: String = ""

    fun carregar(baseId: String, data: String = BrasilDate.hoje()) {
        currentBaseId = baseId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _motoristas.value = repository.getMotoristas(baseId).filter { it.papel != "admin" }
                carregarRotasRecentes(baseId)
                carregarDisponibilidade(baseId, data)
            } catch (e: Exception) {
                println("❌ PreEscalaVM: Erro ao carregar: ${e.message}")
                _error.value = "Erro ao carregar dados: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selecionarData(baseId: String, data: String) {
        carregarDisponibilidade(baseId, data)
    }

    /**
     * Pré-escala já salva para data/turno (para preencher o dialog ao reabrir).
     * Retorna a onda "PRÉ-ESCALA" ou null se não existir.
     */
    suspend fun carregarPreEscalaSalva(baseId: String, data: String, turno: String): Onda? {
        return try {
            escalaRepository.getEscalaByDateAndTurno(baseId, data, turno)
                ?.ondas
                ?.firstOrNull { it.nome.equals(NOME_ONDA_PRE_ESCALA, ignoreCase = true) }
        } catch (e: Exception) {
            println("⚠️ PreEscalaVM: Erro ao buscar pré-escala existente: ${e.message}")
            null
        }
    }

    private fun carregarDisponibilidade(baseId: String, data: String) {
        viewModelScope.launch {
            try {
                val disp = repository.getDisponibilidade(baseId, data)
                _disponibilidadeRespostas.value = disp?.motoristas
                    ?.associateBy { it.motoristaId }
                    ?: emptyMap()
            } catch (e: Exception) {
                println("⚠️ PreEscalaVM: Erro ao carregar disponibilidade: ${e.message}")
                _disponibilidadeRespostas.value = emptyMap()
            }
        }
    }

    private suspend fun carregarRotasRecentes(baseId: String) {
        try {
            val escalas = escalaRepository.getEscalasRecentes(baseId, dias = 7)
            val rotas = escalas
                .flatMap { it.ondas }
                .flatMap { it.itens }
                .map { it.rota.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
            _rotasRecentes.value = rotas
        } catch (e: Exception) {
            println("⚠️ PreEscalaVM: Erro ao carregar rotas recentes: ${e.message}")
            _rotasRecentes.value = emptyList()
        }
    }

    /**
     * Salva a pré-escala como onda "PRÉ-ESCALA" no doc {data}_{turno}.
     * Re-compartilhar substitui os itens da PRÉ-ESCALA sem tocar nas ondas reais.
     */
    fun salvarPreEscala(
        baseId: String,
        data: String,
        turno: String,
        horario: String,
        itens: List<OndaItem>
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val escala = escalaRepository.getEscalaByDateAndTurno(baseId, data, turno)
                    ?: Escala(baseId = baseId, data = data, turno = turno, ondas = emptyList())

                val ondas = escala.ondas.toMutableList()
                val novaOnda = Onda(nome = NOME_ONDA_PRE_ESCALA, horario = horario, itens = itens)

                val idx = ondas.indexOfFirst { it.nome.equals(NOME_ONDA_PRE_ESCALA, ignoreCase = true) }
                if (idx >= 0) ondas[idx] = novaOnda else ondas.add(novaOnda)

                escalaRepository.saveEscala(
                    baseId,
                    escala.copy(ondas = ondas, data = data, turno = turno)
                )
                println("✅ PreEscalaVM: Pré-escala salva ($data/$turno) com ${itens.size} motoristas")
                _message.value = "Pré-escala salva (${itens.size} motoristas)"
            } catch (e: Exception) {
                println("❌ PreEscalaVM: Erro ao salvar pré-escala: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao salvar pré-escala: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
