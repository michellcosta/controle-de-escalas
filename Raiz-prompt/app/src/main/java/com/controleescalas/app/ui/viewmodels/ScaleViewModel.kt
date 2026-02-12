package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.Repository
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.AdminMotoristaCardData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScaleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository()
    
    private val _escala = MutableStateFlow<Escala?>(null)
    val escala: StateFlow<Escala?> = _escala.asStateFlow()
    
    private val _motoristasDisponiveis = MutableStateFlow<List<AdminMotoristaCardData>>(emptyList())
    val motoristasDisponiveis: StateFlow<List<AdminMotoristaCardData>> = _motoristasDisponiveis.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    // Cache para manter escalas AM e PM separadas
    private val escalasCache = mutableMapOf<String, Escala>() // key: "AM" ou "PM"

    fun loadData(baseId: String, turno: String = "AM") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Carregar motoristas para seleção
                val motoristas = repository.getMotoristas(baseId)
                println("✅ ScaleViewModel: ${motoristas.size} motoristas carregados")
                _motoristasDisponiveis.value = motoristas
                
                // Carregar escala de hoje
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                
                // 1. Verificar cache local primeiro
                val cachedEscala = escalasCache[turno]
                if (cachedEscala != null && cachedEscala.data == hoje && cachedEscala.baseId == baseId) {
                    _escala.value = cachedEscala
                    println("📦 ScaleViewModel: Escala $turno carregada do cache com ${cachedEscala.ondas.size} ondas")
                    return@launch
                }
                
                // 2. Buscar do Firestore
                val escalaFromDb = repository.getEscalaByDateAndTurno(baseId, hoje, turno)
                if (escalaFromDb != null) {
                    _escala.value = escalaFromDb
                    escalasCache[turno] = escalaFromDb
                    println("🔥 ScaleViewModel: Escala $turno carregada do Firestore com ${escalaFromDb.ondas.size} ondas")
                } else {
                    // 3. Criar nova escala vazia
                    val novaEscala = Escala(
                        baseId = baseId,
                        data = hoje,
                        turno = turno,
                        ondas = emptyList()
                    )
                    _escala.value = novaEscala
                    escalasCache[turno] = novaEscala
                    println("🆕 ScaleViewModel: Nova escala $turno criada")
                }
                
            } catch (e: Exception) {
                println("❌ ScaleViewModel erro: ${e.message}")
                _error.value = "Erro ao carregar dados: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addOnda(turno: String) {
        val currentEscala = _escala.value ?: return
        val numeroOnda = currentEscala.ondas.size + 1
        
        // ✅ Mudança: Onda criada sem horário - admin define depois
        val horarioPadrao = "" // Vazio = indefinido
        
        val novaOnda = Onda(
            nome = "${numeroOnda}ª ONDA",
            horario = horarioPadrao, // Vazio até admin definir
            itens = emptyList()
        )
        
        val updatedEscala = currentEscala.copy(
            ondas = currentEscala.ondas + novaOnda
        )
        _escala.value = updatedEscala
        escalasCache[turno] = updatedEscala
    }

    fun addMotoristaToOnda(ondaIndex: Int, motorista: AdminMotoristaCardData) {
        val currentEscala = _escala.value ?: return
        val ondas = currentEscala.ondas.toMutableList()
        
        if (ondaIndex in ondas.indices) {
            val onda = ondas[ondaIndex]
            val novoItem = OndaItem(
                motoristaId = motorista.id, // ✅ CORREÇÃO: Usar ID do documento, não telefone
                nome = motorista.nome,
                horario = onda.horario // Herda horário da onda por padrão
            )
            
            ondas[ondaIndex] = onda.copy(itens = onda.itens + novoItem)
            val updatedEscala = currentEscala.copy(ondas = ondas)
            _escala.value = updatedEscala
            escalasCache[currentEscala.turno] = updatedEscala
        }
    }

    fun updateOndaItem(ondaIndex: Int, itemIndex: Int, novoHorario: String, novaRota: String, novaVaga: String) {
        val currentEscala = _escala.value ?: return
        val ondas = currentEscala.ondas.toMutableList()
        
        if (ondaIndex in ondas.indices) {
            val onda = ondas[ondaIndex]
            val itens = onda.itens.toMutableList()
            
            if (itemIndex in itens.indices) {
                itens[itemIndex] = itens[itemIndex].copy(
                    horario = novoHorario,
                    rota = novaRota,
                    vaga = novaVaga
                )
                ondas[ondaIndex] = onda.copy(itens = itens)
                val updatedEscala = currentEscala.copy(ondas = ondas)
                _escala.value = updatedEscala
                escalasCache[currentEscala.turno] = updatedEscala
            }
        }
    }

    fun saveEscala(baseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentEscala = _escala.value
                if (currentEscala == null) {
                    _error.value = "Nenhuma escala para salvar"
                    return@launch
                }
                
                println("💾 Salvando escala: ${currentEscala.ondas.size} ondas")
                repository.saveEscala(baseId, currentEscala)
                println("✅ Escala salva com sucesso!")
                _message.value = "Escala salva com sucesso!"
            } catch (e: Exception) {
                println("❌ Erro ao salvar escala: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao salvar escala: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
