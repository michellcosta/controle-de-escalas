package com.controleescalas.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.Repository
import com.controleescalas.app.data.models.Disponibilidade
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.utils.BrasilDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScaleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository()
    
    private val _escala = MutableStateFlow<Escala?>(null)
    val escala: StateFlow<Escala?> = _escala.asStateFlow()
    
    private val _motoristasDisponiveis = MutableStateFlow<List<AdminMotoristaCardData>>(emptyList())
    val motoristasDisponiveis: StateFlow<List<AdminMotoristaCardData>> = _motoristasDisponiveis.asStateFlow()

    // Disponibilidade declarada para hoje: motoristaId -> estado no turno atual
    // (true = disponível, false = indisponível, null/ausente = sem resposta)
    private val _disponibilidadeMap = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val disponibilidadeMap: StateFlow<Map<String, Boolean?>> = _disponibilidadeMap.asStateFlow()
    
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

                // Carregar disponibilidade declarada para hoje (badge no dialog de seleção)
                carregarDisponibilidadeHoje(baseId, turno)
                
                // Carregar escala de hoje
                val hoje = BrasilDate.hoje()
                
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
        // Numeração ignora a PRÉ-ESCALA: primeira onda real = "1ª ONDA"
        val numeroOnda = currentEscala.ondas.count { !it.nome.equals("PRÉ-ESCALA", true) } + 1
        
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

    /**
     * Adiciona motorista à onda com vaga e rota (usado na importação por foto)
     */
    fun addMotoristaToOndaWithDetails(ondaIndex: Int, motoristaId: String, nome: String, vaga: String, rota: String) {
        val currentEscala = _escala.value ?: return
        val ondas = currentEscala.ondas.toMutableList()

        if (ondaIndex in ondas.indices) {
            val onda = ondas[ondaIndex]
            val novoItem = OndaItem(
                motoristaId = motoristaId,
                nome = nome,
                vaga = vaga,
                rota = rota,
                horario = onda.horario
            )
            ondas[ondaIndex] = onda.copy(itens = onda.itens + novoItem)
            val updatedEscala = currentEscala.copy(ondas = ondas)
            _escala.value = updatedEscala
            escalasCache[currentEscala.turno] = updatedEscala
        }
    }

    /**
     * Montagem rápida: adiciona vários motoristas de uma vez.
     *
     * @param distribuirEm se não-nulo e > 0, cria N ondas novas e distribui
     *        os motoristas igualmente entre elas (round-robin); caso contrário,
     *        adiciona todos na onda destinoOndaIndex.
     * @param rotas rota individual por motoristaId (opcional; ausente = rota vazia)
     * @param destinosPorMotorista ondaIndex explícito por motoristaId (modo
     *        "ondas diferentes"); tem prioridade sobre distribuirEm. Ondas
     *        faltantes são criadas.
     */
    fun addMotoristasBatch(
        motoristas: List<AdminMotoristaCardData>,
        destinoOndaIndex: Int,
        distribuirEm: Int? = null,
        rotas: Map<String, String> = emptyMap(),
        destinosPorMotorista: Map<String, Int> = emptyMap()
    ) {
        val currentEscala = _escala.value ?: return
        var ondas = currentEscala.ondas.toMutableList()

        // Remover os motoristas de qualquer onda antes de adicionar (mover sem duplicar),
        // capturando os dados atuais para preservar rota/sacas quando a ação não informa
        val anteriores = mutableMapOf<String, OndaItem>()
        val idsNovos = motoristas.map { it.id }.toSet()
        ondas = ondas.map { o ->
            val removidos = o.itens.filter { it.motoristaId in idsNovos }
            removidos.forEach { anteriores[it.motoristaId] = it }
            o.copy(itens = o.itens.filter { it.motoristaId !in idsNovos })
        }.toMutableList()

        if (destinosPorMotorista.isNotEmpty()) {
            // Criar ondas até cobrir o maior índice escolhido
            // (numeração ignora a PRÉ-ESCALA: 1ª, 2ª, 3ª... são ondas reais)
            val maxIndex = destinosPorMotorista.values.maxOrNull() ?: 0
            while (ondas.size <= maxIndex) {
                val numero = ondas.count { !it.nome.equals("PRÉ-ESCALA", true) } + 1
                ondas.add(Onda(nome = "${numero}ª ONDA", horario = "", itens = emptyList()))
            }
            motoristas.forEach { motorista ->
                val ondaIndex = destinosPorMotorista[motorista.id] ?: return@forEach
                if (ondaIndex !in ondas.indices) return@forEach
                val anterior = anteriores[motorista.id]
                val onda = ondas[ondaIndex]
                val novoItem = OndaItem(
                    motoristaId = motorista.id,
                    nome = motorista.nome,
                    rota = rotas[motorista.id] ?: anterior?.rota ?: "",
                    vaga = "", // Vaga fica para depois (montagem das ondas)
                    horario = onda.horario,
                    hasPdf = anterior?.hasPdf ?: false,
                    sacas = anterior?.sacas,
                    modalidade = anterior?.modalidade ?: motorista.modalidade,
                    nota = anterior?.nota
                )
                ondas[ondaIndex] = onda.copy(itens = onda.itens + novoItem)
            }
        } else if (distribuirEm != null && distribuirEm > 0) {
            // Criar N ondas novas (horário vazio = indefinido) numeradas a partir da atual
            val numeroInicial = ondas.size + 1
            repeat(distribuirEm) { i ->
                ondas.add(Onda(nome = "${numeroInicial + i}ª ONDA", horario = "", itens = emptyList()))
            }
            // Distribuir round-robin pelas N ondas novas (10 em 3 -> 4/3/3)
            val primeiraNova = ondas.size - distribuirEm
            motoristas.forEachIndexed { i, motorista ->
                val ondaIndex = primeiraNova + (i % distribuirEm)
                val onda = ondas[ondaIndex]
                val anterior = anteriores[motorista.id]
                val novoItem = OndaItem(
                    motoristaId = motorista.id,
                    nome = motorista.nome,
                    rota = rotas[motorista.id] ?: anterior?.rota ?: "",
                    vaga = "", // Vaga fica para depois (montagem das ondas)
                    horario = onda.horario,
                    hasPdf = anterior?.hasPdf ?: false,
                    sacas = anterior?.sacas,
                    modalidade = anterior?.modalidade ?: motorista.modalidade,
                    nota = anterior?.nota
                )
                ondas[ondaIndex] = onda.copy(itens = onda.itens + novoItem)
            }
        } else {
            if (destinoOndaIndex !in ondas.indices) return
            val onda = ondas[destinoOndaIndex]
            val novosItens = motoristas.map { motorista ->
                val anterior = anteriores[motorista.id]
                OndaItem(
                    motoristaId = motorista.id,
                    nome = motorista.nome,
                    rota = rotas[motorista.id] ?: anterior?.rota ?: "",
                    vaga = "", // Vaga fica para depois (montagem das ondas)
                    horario = onda.horario,
                    hasPdf = anterior?.hasPdf ?: false,
                    sacas = anterior?.sacas,
                    modalidade = anterior?.modalidade ?: motorista.modalidade,
                    nota = anterior?.nota
                )
            }
            ondas[destinoOndaIndex] = onda.copy(itens = onda.itens + novosItens)
        }

        val updatedEscala = currentEscala.copy(ondas = ondas)
        _escala.value = updatedEscala
        escalasCache[currentEscala.turno] = updatedEscala
        println("⚡ ScaleViewModel: ${motoristas.size} motoristas adicionados em lote (distribuirEm=$distribuirEm)")
    }

    /**
     * Carrega a disponibilidade declarada para hoje e deriva o mapa por motorista
     * no turno atual. Cache do doc evita refetch ao trocar de turno.
     */
    private var disponibilidadeCache: Pair<String, Disponibilidade?>? = null

    private fun carregarDisponibilidadeHoje(baseId: String, turno: String) {
        viewModelScope.launch {
            try {
                val hoje = BrasilDate.hoje()
                val cacheKey = "${baseId}_$hoje"
                val disp = if (disponibilidadeCache?.first == cacheKey) {
                    disponibilidadeCache?.second
                } else {
                    val fetched = repository.getDisponibilidade(baseId, hoje)
                    disponibilidadeCache = cacheKey to fetched
                    fetched
                }
                val mapa = disp?.motoristas?.associate { it.motoristaId to it.estadoNoTurno(turno) } ?: emptyMap()
                _disponibilidadeMap.value = mapa
            } catch (e: Exception) {
                println("⚠️ ScaleViewModel: Erro ao carregar disponibilidade: ${e.message}")
                _disponibilidadeMap.value = emptyMap()
            }
        }
    }

    /**
     * Garante que existam pelo menos N ondas no turno (cria se faltar)
     */
    fun ensureOndasCount(turno: String, count: Int) {
        val currentEscala = _escala.value ?: return
        if (currentEscala.turno != turno) return
        var ondas = currentEscala.ondas.toMutableList()
        while (ondas.size < count) {
            val num = ondas.size + 1
            ondas.add(Onda(nome = "${num}ª ONDA", horario = "", itens = emptyList()))
        }
        _escala.value = currentEscala.copy(ondas = ondas)
        escalasCache[turno] = currentEscala.copy(ondas = ondas)
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
