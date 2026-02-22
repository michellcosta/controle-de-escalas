package com.controleescalas.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.models.Disponibilidade
import com.controleescalas.app.data.models.DisponibilidadeMotorista
import com.controleescalas.app.data.models.DisponibilidadeStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel para gerenciar o sistema de disponibilidade
 * 
 * Funcionalidades:
 * - Admin: Solicitar disponibilidade, visualizar respostas
 * - Motorista: Marcar disponibilidade, consultar solicitações
 */
class DisponibilidadeViewModel : ViewModel() {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    // States
    private val _disponibilidade = MutableStateFlow<Disponibilidade?>(null)
    val disponibilidade: StateFlow<Disponibilidade?> = _disponibilidade
    
    private val _minhaDisponibilidade = MutableStateFlow<DisponibilidadeMotorista?>(null)
    val minhaDisponibilidade: StateFlow<DisponibilidadeMotorista?> = _minhaDisponibilidade
    
    private val _disponibilidadeStatus = MutableStateFlow<DisponibilidadeStatus?>(null)
    val disponibilidadeStatus: StateFlow<DisponibilidadeStatus?> = _disponibilidadeStatus
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    // Listener para atualizações em tempo real
    private var disponibilidadeListener: ListenerRegistration? = null
    
    /**
     * Normaliza telefone removendo caracteres não numéricos
     */
    private fun normalizeTelefone(telefone: String): String {
        return telefone.replace(Regex("[^0-9]"), "")
    }
    
    /**
     * Remove duplicatas de motoristas baseado no telefone normalizado
     * Prioriza motoristas com ID do documento (correto) sobre motoristas com telefone como ID
     */
    private fun removerDuplicatasMotoristas(motoristas: List<DisponibilidadeMotorista>): List<DisponibilidadeMotorista> {
        // Agrupar motoristas por telefone normalizado
        val motoristasPorTelefone = mutableMapOf<String, MutableList<DisponibilidadeMotorista>>()
        
        for (motorista in motoristas) {
            val telefoneNormalizado = normalizeTelefone(motorista.telefone.ifEmpty { motorista.motoristaId })
            
            if (telefoneNormalizado.isNotBlank()) {
                motoristasPorTelefone.getOrPut(telefoneNormalizado) { mutableListOf() }.add(motorista)
            } else {
                // Se não tem telefone, usar motoristaId como chave única
                motoristasPorTelefone[motorista.motoristaId] = mutableListOf(motorista)
            }
        }
        
        val motoristasUnicos = mutableListOf<DisponibilidadeMotorista>()
        
        for ((telefone, listaMotoristas) in motoristasPorTelefone) {
            if (listaMotoristas.size == 1) {
                // Sem duplicatas, adicionar diretamente
                motoristasUnicos.add(listaMotoristas[0])
            } else {
                // Há duplicatas, priorizar o motorista com ID do documento (não é apenas números)
                val motoristaPrioritario = listaMotoristas.maxByOrNull { motorista ->
                    // Priorizar: ID do documento (não é apenas números) > nome não vazio > telefone não vazio
                    var score = 0
                    // Se o motoristaId não é apenas números, é um ID do documento (prioridade alta)
                    if (!motorista.motoristaId.matches(Regex("^[0-9]+$"))) {
                        score += 100
                    }
                    // Se tem nome não vazio, adicionar pontos
                    if (motorista.nome.isNotBlank() && motorista.nome != "Motorista") {
                        score += 10
                    }
                    // Se tem telefone não vazio, adicionar pontos
                    if (motorista.telefone.isNotBlank()) {
                        score += 5
                    }
                    score
                } ?: listaMotoristas[0]
                
                println("⚠️ Duplicatas encontradas para telefone $telefone:")
                listaMotoristas.forEach { m ->
                    println("   - ID: ${m.motoristaId}, Nome: '${m.nome}', Telefone: '${m.telefone}'")
                }
                println("   ✅ Mantendo: ID: ${motoristaPrioritario.motoristaId}, Nome: '${motoristaPrioritario.nome}'")
                
                motoristasUnicos.add(motoristaPrioritario)
            }
        }
        
        if (motoristas.size != motoristasUnicos.size) {
            println("✅ Duplicatas removidas: ${motoristas.size} -> ${motoristasUnicos.size} motoristas")
        }
        
        return motoristasUnicos
    }
    
    /**
     * Busca o nome do motorista no Firestore
     * Tenta primeiro pelo ID do documento, depois pelo telefone
     */
    private suspend fun buscarNomeMotorista(baseId: String, motoristaId: String): String? {
        return try {
            // Tentar buscar pelo ID do documento primeiro
            val motoristaDoc = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(motoristaId)
                .get()
                .await()
            
            if (motoristaDoc.exists()) {
                val nome = motoristaDoc.getString("nome")
                println("✅ Nome encontrado pelo ID do documento para $motoristaId: '$nome'")
                return nome
            }
            
            // Se não encontrou pelo ID, tentar buscar pelo telefone
            println("⚠️ Motorista $motoristaId não encontrado pelo ID, tentando buscar pelo telefone...")
            val telefoneNormalizado = normalizeTelefone(motoristaId)
            val buscaPorTelefone = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .whereEqualTo("telefone", telefoneNormalizado)
                .whereEqualTo("ativo", true)
                .limit(1)
                .get()
                .await()
            
            val docPorTelefone = buscaPorTelefone.documents.firstOrNull()
            if (docPorTelefone != null) {
                val nome = docPorTelefone.getString("nome")
                println("✅ Nome encontrado pelo telefone para $motoristaId: '$nome' (ID real: ${docPorTelefone.id})")
                return nome
            }
            
            println("⚠️ Motorista $motoristaId não encontrado nem pelo ID nem pelo telefone")
            null
        } catch (e: Exception) {
            println("❌ Erro ao buscar nome do motorista $motoristaId: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Calcula a data de amanhã no formato YYYY-MM-DD
     */
    private fun getDataAmanha(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }
    
    /**
     * Formata data para exibição (DD/MM/YYYY)
     */
    private fun formatarDataParaExibicao(data: String): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOutput = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = sdfInput.parse(data)
            sdfOutput.format(date ?: Date())
        } catch (e: Exception) {
            data
        }
    }
    
    // ==================== FUNÇÕES PARA ADMIN ====================
    
    /**
     * Solicita disponibilidade para todos os motoristas da base
     * Cria documento no Firestore e envia notificação push
     */
    fun solicitarDisponibilidade(baseId: String, adminId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val dataAmanha = getDataAmanha()
                val docId = "${baseId}_$dataAmanha"
                
                // Buscar todos os motoristas e auxiliares da base
                val motoristasSnapshot = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereIn("papel", listOf("motorista", "auxiliar"))
                    .whereEqualTo("ativo", true)
                    .get()
                    .await()
                
                val motoristas = motoristasSnapshot.documents.map { doc ->
                    val nome = doc.getString("nome") ?: ""
                    val motoristaId = doc.id
                    println("🔍 DisponibilidadeViewModel.solicitarDisponibilidade: Motorista encontrado - ID: $motoristaId, Nome: '$nome'")
                    
                    DisponibilidadeMotorista(
                        motoristaId = motoristaId,
                        nome = nome,
                        telefone = doc.getString("telefone") ?: "",
                        disponivel = null,
                        respondidoEm = null
                    )
                }
                
                println("✅ DisponibilidadeViewModel.solicitarDisponibilidade: Total de motoristas encontrados: ${motoristas.size}")
                motoristas.forEach { m ->
                    println("   - ID: ${m.motoristaId}, Nome: '${m.nome}', Telefone: '${m.telefone}'")
                }
                
                if (motoristas.isEmpty()) {
                    _error.value = "Nenhum motorista encontrado na base"
                    return@launch
                }
                
                // Remover duplicatas antes de criar a disponibilidade
                val motoristasUnicos = removerDuplicatasMotoristas(motoristas)
                
                // Criar documento de disponibilidade
                val disponibilidade = Disponibilidade(
                    id = docId,
                    baseId = baseId,
                    data = dataAmanha,
                    motoristas = motoristasUnicos,
                    notificacaoEnviada = false,
                    criadoEm = System.currentTimeMillis(),
                    criadoPor = adminId
                )
                
                firestore.collection("disponibilidades")
                    .document(docId)
                    .set(disponibilidade)
                    .await()
                
                // TODO: Enviar notificação push para todos os motoristas
                // Isso será implementado na fase de notificações
                
                _message.value = "Solicitação enviada para ${motoristas.size} motoristas"
                
            } catch (e: Exception) {
                _error.value = "Erro ao solicitar disponibilidade: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Garante que existe disponibilidade para amanhã
     * Cria automaticamente se não existir (fallback caso Cloud Function falhe)
     */
    fun garantirDisponibilidadeParaAmanha(baseId: String) {
        viewModelScope.launch {
            try {
                val dataAmanha = getDataAmanha()
                val docId = "${baseId}_$dataAmanha"
                
                val snapshot = firestore.collection("disponibilidades")
                    .document(docId)
                    .get()
                    .await()
                
                // Se não existe, criar automaticamente
                if (!snapshot.exists()) {
                    criarDisponibilidadeAutomatica(baseId, dataAmanha)
                }
            } catch (e: Exception) {
                _error.value = "Erro ao verificar disponibilidade: ${e.message}"
            }
        }
    }
    
    /**
     * Cria disponibilidade automaticamente (fallback)
     */
    private fun criarDisponibilidadeAutomatica(baseId: String, data: String) {
        viewModelScope.launch {
            try {
                // Buscar todos os motoristas e auxiliares ativos da base
                val motoristasSnapshot = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereEqualTo("ativo", true)
                    .whereIn("papel", listOf("motorista", "auxiliar"))
                    .get()
                    .await()
                
                val motoristas = motoristasSnapshot.documents.map { doc ->
                    val nome = doc.getString("nome") ?: ""
                    val motoristaId = doc.id
                    println("🔍 DisponibilidadeViewModel.criarDisponibilidadeAutomatica: Motorista/Auxiliar - ID: $motoristaId, Nome: '$nome'")
                    
                    DisponibilidadeMotorista(
                        motoristaId = motoristaId,
                        nome = nome,
                        telefone = doc.getString("telefone") ?: "",
                        disponivel = null,
                        respondidoEm = null
                    )
                }
                
                println("✅ DisponibilidadeViewModel.criarDisponibilidadeAutomatica: Total de motoristas: ${motoristas.size}")
                motoristas.forEach { m ->
                    println("   - ID: ${m.motoristaId}, Nome: '${m.nome}'")
                }
                
                if (motoristas.isEmpty()) {
                    return@launch
                }
                
                val disponibilidade = Disponibilidade(
                    id = "${baseId}_$data",
                    baseId = baseId,
                    data = data,
                    motoristas = motoristas,
                    notificacaoEnviada = false,
                    criadoEm = System.currentTimeMillis(),
                    criadoPor = "system"
                )
                
                firestore.collection("disponibilidades")
                    .document("${baseId}_$data")
                    .set(disponibilidade)
                    .await()
                
                println("✅ Disponibilidade criada automaticamente para $data")
                
            } catch (e: Exception) {
                println("❌ Erro ao criar disponibilidade automaticamente: ${e.message}")
                _error.value = "Erro ao criar disponibilidade: ${e.message}"
            }
        }
    }
    
    /**
     * Carrega a disponibilidade para uma data específica
     */
    fun carregarDisponibilidade(baseId: String, data: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val dataConsulta = data ?: getDataAmanha()
                val docId = "${baseId}_$dataConsulta"
                
                val snapshot = firestore.collection("disponibilidades")
                    .document(docId)
                    .get()
                    .await()
                
                if (snapshot.exists()) {
                    val disp = snapshot.toObject(Disponibilidade::class.java)
                    _disponibilidade.value = disp
                    
                    // Calcular status
                    disp?.let { calcularStatus(it) }
                } else {
                    _disponibilidade.value = null
                    _disponibilidadeStatus.value = null
                }
                
            } catch (e: Exception) {
                _error.value = "Erro ao carregar disponibilidade: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Observa disponibilidade em tempo real (para admin)
     */
    fun observarDisponibilidade(baseId: String, data: String? = null) {
        // Remover listener anterior se existir
        disponibilidadeListener?.remove()
        
        val dataConsulta = data ?: getDataAmanha()
        val docId = "${baseId}_$dataConsulta"
        
        disponibilidadeListener = firestore.collection("disponibilidades")
            .document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ Erro no listener de disponibilidade: ${error.message}")
                    _error.value = "Erro ao observar disponibilidade: ${error.message}"
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val disp = snapshot.toObject(Disponibilidade::class.java)
                    
                    // Atualizar nomes vazios em tempo real
                    disp?.let { disponibilidade ->
                        // Remover duplicatas antes de processar
                        val disponibilidadeSemDuplicatas = disponibilidade.copy(
                            motoristas = removerDuplicatasMotoristas(disponibilidade.motoristas)
                        )
                        
                        println("🔍 DisponibilidadeViewModel.observarDisponibilidade: Verificando ${disponibilidadeSemDuplicatas.motoristas.size} motoristas")
                        
                        // Identificar motoristas com nomes vazios
                        val motoristasComNomesVazios = disponibilidadeSemDuplicatas.motoristas.filter { 
                            it.nome.isBlank() || it.nome == "Motorista" 
                        }
                        
                        if (motoristasComNomesVazios.isNotEmpty()) {
                            println("⚠️ DisponibilidadeViewModel: ${motoristasComNomesVazios.size} motoristas com nome vazio, buscando nomes...")
                            motoristasComNomesVazios.forEach { m ->
                                println("   - Motorista ${m.motoristaId} precisa de nome")
                            }
                            
                            // Buscar todos os nomes de uma vez em uma única corrotina
                            viewModelScope.launch {
                                val motoristasAtualizados = disponibilidadeSemDuplicatas.motoristas.map { motorista ->
                                    if (motorista.nome.isBlank() || motorista.nome == "Motorista") {
                                        try {
                                            println("🔍 Buscando nome para motorista ${motorista.motoristaId}...")
                                            val nomeAtualizado = buscarNomeMotorista(baseId, motorista.motoristaId)
                                            
                                            if (nomeAtualizado != null && nomeAtualizado.isNotBlank() && nomeAtualizado != "Motorista") {
                                                println("✅ Nome encontrado para ${motorista.motoristaId}: '$nomeAtualizado'")
                                                motorista.copy(nome = nomeAtualizado)
                                            } else {
                                                println("⚠️ Nome ainda vazio para ${motorista.motoristaId}")
                                                motorista
                                            }
                                        } catch (e: Exception) {
                                            println("❌ Erro ao buscar nome do motorista ${motorista.motoristaId}: ${e.message}")
                                            e.printStackTrace()
                                            motorista
                                        }
                                    } else {
                                        motorista
                                    }
                                }
                                
                                // Remover duplicatas antes de atualizar
                                val motoristasSemDuplicatas = removerDuplicatasMotoristas(motoristasAtualizados)
                                val dispAtualizada = disponibilidadeSemDuplicatas.copy(motoristas = motoristasSemDuplicatas)
                                _disponibilidade.value = dispAtualizada
                                calcularStatus(dispAtualizada)
                                println("✅ Disponibilidade atualizada com nomes: ${dispAtualizada.motoristas.size} motoristas")
                                dispAtualizada.motoristas.forEach { m ->
                                    println("   - ID: ${m.motoristaId}, Nome: '${m.nome}', Disponível: ${m.disponivel}")
                                }
                            }
                        }
                        
                        // Atualizar imediatamente mesmo que alguns nomes estejam vazios
                        _disponibilidade.value = disponibilidadeSemDuplicatas
                        calcularStatus(disponibilidadeSemDuplicatas)
                        println("✅ Disponibilidade atualizada em tempo real: ${disponibilidadeSemDuplicatas.motoristas.size} motoristas")
                    }
                } else {
                    _disponibilidade.value = null
                    _disponibilidadeStatus.value = null
                }
            }
    }
    
    /**
     * Para de observar disponibilidade
     */
    fun pararObservacaoDisponibilidade() {
        disponibilidadeListener?.remove()
        disponibilidadeListener = null
    }
    
    /**
     * Calcula estatísticas da disponibilidade
     */
    private fun calcularStatus(disponibilidade: Disponibilidade) {
        val total = disponibilidade.motoristas.size
        val disponiveis = disponibilidade.motoristas.count { it.disponivel == true }
        val indisponiveis = disponibilidade.motoristas.count { it.disponivel == false }
        val semResposta = disponibilidade.motoristas.count { it.disponivel == null }
        
        _disponibilidadeStatus.value = DisponibilidadeStatus(
            data = disponibilidade.data,
            totalMotoristas = total,
            disponiveis = disponiveis,
            indisponiveis = indisponiveis,
            semResposta = semResposta
        )
    }
    
    // ==================== FUNÇÕES PARA MOTORISTA ====================
    
    /**
     * Carrega a disponibilidade pendente para o motorista
     * Garante que existe disponibilidade antes de carregar (cria se não existir)
     */
    fun carregarMinhaDisponibilidade(baseId: String, motoristaId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val dataAmanha = getDataAmanha()
                val docId = "${baseId}_$dataAmanha"
                
                // Verificar se existe disponibilidade
                var snapshot = firestore.collection("disponibilidades")
                    .document(docId)
                    .get()
                    .await()
                
                // Se não existe, criar automaticamente
                if (!snapshot.exists()) {
                    println("⚠️ Disponibilidade não existe para $docId, criando...")
                    criarDisponibilidadeParaMotorista(baseId, motoristaId, dataAmanha)
                    // Aguardar um pouco para garantir que foi criado
                    kotlinx.coroutines.delay(1000)
                    // Recarregar após criar
                    snapshot = firestore.collection("disponibilidades")
                        .document(docId)
                        .get()
                        .await()
                    
                    if (snapshot.exists()) {
                        println("✅ Disponibilidade criada com sucesso")
                    } else {
                        println("❌ Erro: Disponibilidade não foi criada")
                    }
                }
                
                if (snapshot.exists()) {
                    val disp = snapshot.toObject(Disponibilidade::class.java)
                    if (disp != null) {
                        var minhaResp = disp.motoristas.find { it.motoristaId == motoristaId }
                        
                        // Se o motorista não está na lista, adicionar automaticamente
                        if (minhaResp == null) {
                            println("⚠️ Motorista $motoristaId não está na lista de disponibilidade, adicionando...")
                            
                            // Buscar dados do motorista
                            val nome = buscarNomeMotorista(baseId, motoristaId) ?: "Motorista"
                            if (nome == "Motorista") {
                                println("⚠️ Motorista $motoristaId não encontrado no Firestore, usando fallback")
                            }
                            
                            // Buscar telefone também
                            val telefoneNormalizado = normalizeTelefone(motoristaId)
                            val buscaPorTelefone = firestore
                                .collection("bases")
                                .document(baseId)
                                .collection("motoristas")
                                .whereEqualTo("telefone", telefoneNormalizado)
                                .whereEqualTo("ativo", true)
                                .limit(1)
                                .get()
                                .await()
                            
                            val telefone = buscaPorTelefone.documents.firstOrNull()?.getString("telefone") ?: motoristaId
                            
                            // Criar entrada para o motorista
                            minhaResp = DisponibilidadeMotorista(
                                motoristaId = motoristaId,
                                nome = nome,
                                telefone = telefone,
                                disponivel = null,
                                respondidoEm = null
                            )
                            
                            // Adicionar à lista e atualizar no Firestore
                            val motoristasComNovo = disp.motoristas + minhaResp
                            // Remover duplicatas antes de salvar
                            val motoristasAtualizados = removerDuplicatasMotoristas(motoristasComNovo)
                            val dispAtualizada = disp.copy(motoristas = motoristasAtualizados)
                            
                            try {
                                firestore.collection("disponibilidades")
                                    .document(docId)
                                    .update("motoristas", motoristasAtualizados)
                                    .await()
                                
                                println("✅ Motorista $motoristaId adicionado à disponibilidade")
                                
                                // Atualizar estado local
                                _disponibilidade.value = dispAtualizada
                            } catch (e: Exception) {
                                println("❌ Erro ao atualizar disponibilidade: ${e.message}")
                                // Mesmo com erro, atualizar estado local
                                _disponibilidade.value = dispAtualizada
                            }
                        } else {
                            // Atualizar também o estado de disponibilidade completa
                            _disponibilidade.value = disp
                            println("✅ Motorista $motoristaId já está na lista de disponibilidade")
                        }
                        
                        // SEMPRE definir a disponibilidade do motorista
                        _minhaDisponibilidade.value = minhaResp
                        println("✅ Disponibilidade do motorista definida: ${minhaResp?.nome} - disponivel: ${minhaResp?.disponivel}")
                    } else {
                        _minhaDisponibilidade.value = null
                    }
                } else {
                    _minhaDisponibilidade.value = null
                }
                
            } catch (e: Exception) {
                _error.value = "Erro ao carregar disponibilidade: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Cria disponibilidade incluindo o motorista atual (se não estiver na lista)
     * Usado quando o motorista abre o app e não há disponibilidade
     */
    private suspend fun criarDisponibilidadeParaMotorista(baseId: String, motoristaId: String, dataDisponibilidade: String) {
        try {
            // Buscar todos os motoristas e auxiliares ativos da base
            val motoristasSnapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .whereEqualTo("ativo", true)
                .whereIn("papel", listOf("motorista", "auxiliar"))
                .get()
                .await()
            
            // Se não há motoristas, tentar buscar o motorista específico pelo menos
            val motoristas = if (motoristasSnapshot.isEmpty) {
                // Buscar apenas o motorista atual
                val motoristaDocSnapshot = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .document(motoristaId)
                    .get()
                    .await()
                
                if (motoristaDocSnapshot.exists()) {
                    val snapshot = motoristaDocSnapshot
                    val docDataMap: Map<String, Any> = (snapshot.get("nome")?.let { 
                        mapOf(
                            "nome" to (snapshot.getString("nome") ?: ""),
                            "telefone" to (snapshot.getString("telefone") ?: "")
                        )
                    }) ?: emptyMap()
                    listOf(
                        DisponibilidadeMotorista(
                            motoristaId = motoristaId,
                            nome = docDataMap["nome"]?.toString() ?: "",
                            telefone = docDataMap["telefone"]?.toString() ?: "",
                            disponivel = null,
                            respondidoEm = null
                        )
                    )
                } else {
                    emptyList()
                }
            } else {
                motoristasSnapshot.documents.map { doc ->
                    DisponibilidadeMotorista(
                        motoristaId = doc.id,
                        nome = doc.getString("nome") ?: "",
                        telefone = doc.getString("telefone") ?: "",
                        disponivel = null,
                        respondidoEm = null
                    )
                }
            }
            
            if (motoristas.isEmpty()) {
                return
            }
            
            // Garantir que o motorista atual está na lista
            val motoristaNaLista = motoristas.find { it.motoristaId == motoristaId }
            val motoristasFinais = if (motoristaNaLista == null) {
                // Buscar dados do motorista e adicionar
                val nome = buscarNomeMotorista(baseId, motoristaId) ?: ""
                if (nome.isNotBlank()) {
                    // Buscar telefone também
                    val telefoneNormalizado = normalizeTelefone(motoristaId)
                    val buscaPorTelefone = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("motoristas")
                        .whereEqualTo("telefone", telefoneNormalizado)
                        .whereEqualTo("ativo", true)
                        .limit(1)
                        .get()
                        .await()
                    
                    val telefone = buscaPorTelefone.documents.firstOrNull()?.getString("telefone") ?: motoristaId
                    
                    motoristas + DisponibilidadeMotorista(
                        motoristaId = motoristaId,
                        nome = nome,
                        telefone = telefone,
                        disponivel = null,
                        respondidoEm = null
                    )
                } else {
                    motoristas
                }
            } else {
                motoristas
            }
            
            // Remover duplicatas antes de criar a disponibilidade
            val motoristasFinaisUnicos = removerDuplicatasMotoristas(motoristasFinais)
            
            // Criar objeto Disponibilidade usando função helper para evitar conflito com .data()
            criarDisponibilidadeFirestore(baseId, dataDisponibilidade, motoristasFinaisUnicos)
            
            println("✅ Disponibilidade criada automaticamente para motorista $motoristaId")
            
        } catch (e: Exception) {
            println("❌ Erro ao criar disponibilidade para motorista: ${e.message}")
        }
    }
    
    /**
     * Helper para criar disponibilidade no Firestore sem conflito de nomes
     */
    private suspend fun criarDisponibilidadeFirestore(
        baseId: String,
        dataDisponibilidadeStr: String,
        motoristas: List<DisponibilidadeMotorista>
    ) {
        val disponibilidadeObj = Disponibilidade(
            id = "${baseId}_$dataDisponibilidadeStr",
            baseId = baseId,
            data = dataDisponibilidadeStr,
            motoristas = motoristas,
            notificacaoEnviada = false,
            criadoEm = System.currentTimeMillis(),
            criadoPor = "system"
        )
        
        firestore.collection("disponibilidades")
            .document("${baseId}_$dataDisponibilidadeStr")
            .set(disponibilidadeObj)
            .await()
    }
    
    /**
     * Marca a disponibilidade do motorista (disponível ou indisponível)
     */
    fun marcarDisponibilidade(baseId: String, motoristaId: String, disponivel: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val dataAmanha = getDataAmanha()
                val docId = "${baseId}_$dataAmanha"
                
                // Buscar documento
                val docRef = firestore.collection("disponibilidades").document(docId)
                val snapshot = docRef.get().await()
                
                if (!snapshot.exists()) {
                    _error.value = "Nenhuma solicitação de disponibilidade encontrada"
                    return@launch
                }
                
                val disp = snapshot.toObject(Disponibilidade::class.java)
                if (disp == null) {
                    _error.value = "Erro ao processar disponibilidade"
                    return@launch
                }
                
                // Atualizar motorista específico
                // Primeiro, remover duplicatas para garantir que temos apenas o motorista correto
                val motoristasLimpos = removerDuplicatasMotoristas(disp.motoristas)
                val dispSemDuplicatas = disp.copy(motoristas = motoristasLimpos)
                
                println("🔍 DisponibilidadeViewModel.marcarDisponibilidade: Após remover duplicatas, ${motoristasLimpos.size} motoristas únicos")
                motoristasLimpos.forEach { m ->
                    println("   - ID: ${m.motoristaId}, Nome: '${m.nome}', Telefone: '${m.telefone}'")
                }
                
                val motoristasAtualizados = dispSemDuplicatas.motoristas.map { motorista ->
                    // Buscar pelo motoristaId OU pelo telefone normalizado
                    val telefoneMotoristaId = normalizeTelefone(motoristaId)
                    val telefoneMotorista = normalizeTelefone(motorista.telefone.ifEmpty { motorista.motoristaId })
                    val motoristaIdMatch = motorista.motoristaId == motoristaId
                    val telefoneMatch = telefoneMotoristaId.isNotBlank() && telefoneMotorista == telefoneMotoristaId
                    
                    // Priorizar match por ID do documento (não é apenas números) sobre match por telefone
                    // Se encontrar por telefone, só atualizar se o motorista tiver ID do documento (correto)
                    val deveAtualizar = if (motoristaIdMatch) {
                        true
                    } else if (telefoneMatch) {
                        // Só atualizar se o motorista na lista tiver ID do documento (não é apenas números)
                        !motorista.motoristaId.matches(Regex("^[0-9]+$"))
                    } else {
                        false
                    }
                    
                    if (deveAtualizar) {
                        println("🔍 DisponibilidadeViewModel.marcarDisponibilidade: Atualizando motorista ${motoristaId} (encontrado por ${if (motoristaIdMatch) "ID" else "telefone"}), nome atual: '${motorista.nome}', ID na lista: '${motorista.motoristaId}'")
                        
                        // Se o nome estiver vazio, buscar do Firestore
                        val nomeAtualizado = if (motorista.nome.isBlank() || motorista.nome == "Motorista") {
                            try {
                                println("🔍 Buscando nome do motorista ${motoristaId} no Firestore...")
                                val nome = buscarNomeMotorista(baseId, motoristaId) ?: motorista.nome
                                
                                if (nome.isNotBlank() && nome != "Motorista") {
                                    println("✅ Nome encontrado para ${motoristaId}: '$nome'")
                                } else {
                                    println("⚠️ Nome ainda vazio para ${motoristaId}")
                                }
                                
                                nome
                            } catch (e: Exception) {
                                println("❌ Erro ao buscar nome do motorista ${motoristaId}: ${e.message}")
                                e.printStackTrace()
                                motorista.nome
                            }
                        } else {
                            println("✅ Nome já existe para ${motoristaId}: '${motorista.nome}'")
                            motorista.nome
                        }
                        
                        motorista.copy(
                            nome = nomeAtualizado,
                            disponivel = disponivel,
                            respondidoEm = System.currentTimeMillis()
                        )
                    } else {
                        motorista
                    }
                }
                
                // Remover duplicatas antes de salvar
                val motoristasSemDuplicatas = removerDuplicatasMotoristas(motoristasAtualizados)
                
                println("✅ DisponibilidadeViewModel.marcarDisponibilidade: Motoristas atualizados: ${motoristasSemDuplicatas.size}")
                motoristasSemDuplicatas.forEach { m ->
                    println("   - ID: ${m.motoristaId}, Nome: '${m.nome}', Disponível: ${m.disponivel}")
                }
                
                // Salvar no Firestore
                docRef.update("motoristas", motoristasSemDuplicatas).await()
                
                val statusTexto = if (disponivel) "disponível" else "indisponível"
                val dataFormatada = formatarDataParaExibicao(dataAmanha)
                _message.value = "Você foi marcado como $statusTexto para $dataFormatada"
                
                // Atualizar estado local
                _minhaDisponibilidade.value = motoristasSemDuplicatas.find { it.motoristaId == motoristaId }
                
            } catch (e: Exception) {
                _error.value = "Erro ao marcar disponibilidade: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Verifica se um motorista está disponível para uma data
     */
    suspend fun isMotoristaDisponivel(baseId: String, motoristaId: String, data: String): Boolean {
        return try {
            val docId = "${baseId}_$data"
            val snapshot = firestore.collection("disponibilidades")
                .document(docId)
                .get()
                .await()
            
            if (snapshot.exists()) {
                val disp = snapshot.toObject(Disponibilidade::class.java)
                val motorista = disp?.motoristas?.find { it.motoristaId == motoristaId }
                motorista?.disponivel == true
            } else {
                // Se não há solicitação de disponibilidade, considera disponível
                true
            }
        } catch (e: Exception) {
            // Em caso de erro, considera disponível para não bloquear
            true
        }
    }
    
    /**
     * Limpa mensagens de feedback
     */
    fun clearMessage() {
        _message.value = null
        _error.value = null
    }

    /**
     * Limpa mensagens de feedback (alias para compatibilidade)
     */
    fun clearMessages() {
        clearMessage()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Limpar listener quando ViewModel for destruído
        disponibilidadeListener?.remove()
        disponibilidadeListener = null
    }
}
