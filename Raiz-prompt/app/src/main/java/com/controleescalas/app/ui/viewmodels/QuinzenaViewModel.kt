package com.controleescalas.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.models.QuinzenaDetalhes
import com.controleescalas.app.data.models.QuinzenaMotorista
import com.controleescalas.app.data.models.QuinzenaResumo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class QuinzenaViewModel : ViewModel() {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _minhaQuinzena = MutableStateFlow<QuinzenaMotorista?>(null)
    val minhaQuinzena: StateFlow<QuinzenaMotorista?> = _minhaQuinzena
    
    private val _todasQuinzenas = MutableStateFlow<List<QuinzenaMotorista>>(emptyList())
    val todasQuinzenas: StateFlow<List<QuinzenaMotorista>> = _todasQuinzenas
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    /**
     * Carrega a quinzena atual do motorista
     */
    fun carregarMinhaQuinzena(baseId: String, motoristaId: String) {
        val calendar = Calendar.getInstance()
        val mes = calendar.get(Calendar.MONTH) + 1
        val ano = calendar.get(Calendar.YEAR)
        carregarMinhaQuinzenaPorMes(baseId, motoristaId, mes, ano)
    }
    
    /**
     * Carrega a quinzena do motorista para um mês/ano específico
     */
    fun carregarMinhaQuinzenaPorMes(baseId: String, motoristaId: String, mes: Int, ano: Int) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val docId = "${baseId}_${motoristaId}_${mes}_${ano}"
                
                val doc = firestore.collection("quinzenas")
                    .document(docId)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    _minhaQuinzena.value = doc.toObject(QuinzenaMotorista::class.java)
                } else {
                    // Criar documento vazio se não existir
                    val novaQuinzena = QuinzenaMotorista(
                        motoristaId = motoristaId,
                        baseId = baseId,
                        mes = mes,
                        ano = ano,
                        atualizadoEm = System.currentTimeMillis()
                    )
                    _minhaQuinzena.value = novaQuinzena
                }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar quinzena: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carrega todas as quinzenas da base (para admin)
     * 
     * ✅ MODO TESTE: Inclui TODOS os motoristas e auxiliares, mesmo sem dias trabalhados
     * 
     * Para voltar ao modo normal (apenas motoristas com dias trabalhados):
     * 1. Remover a busca de todos os motoristas (linhas 95-100)
     * 2. Remover o mapeamento que cria quinzenas vazias (linhas 110-130)
     * 3. Usar apenas a busca de quinzenas existentes (linhas 102-108)
     * 4. Manter apenas o enriquecimento de nomes (linhas 100-125 do código antigo)
     */
    fun carregarTodasQuinzenas(baseId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val calendar = Calendar.getInstance()
                val mes = calendar.get(Calendar.MONTH) + 1
                val ano = calendar.get(Calendar.YEAR)
                
                // ✅ NOVO: Buscar TODOS os motoristas e auxiliares ativos da base
                val motoristasSnapshot = firestore.collection("bases")
                    .document(baseId)
                    .collection("motoristas")
                    .whereEqualTo("ativo", true)
                    .whereIn("papel", listOf("motorista", "auxiliar"))
                    .get()
                    .await()
                
                println("✅ QuinzenaViewModel: ${motoristasSnapshot.documents.size} motoristas/auxiliares encontrados")
                
                // ✅ NOVO: Buscar quinzenas existentes do mês atual
                val quinzenasSnapshot = firestore.collection("quinzenas")
                    .whereEqualTo("baseId", baseId)
                    .whereEqualTo("mes", mes)
                    .whereEqualTo("ano", ano)
                    .get()
                    .await()
                
                // Criar mapa de quinzenas por motoristaId para busca rápida
                val quinzenasPorMotorista = quinzenasSnapshot.documents.associate { doc ->
                    val quinzena = doc.toObject(QuinzenaMotorista::class.java)
                    quinzena?.motoristaId to quinzena
                }
                
                // ✅ NOVO: Criar lista com TODOS os motoristas, incluindo os sem quinzena
                val todasQuinzenas = motoristasSnapshot.documents.mapNotNull { motoristaDoc ->
                    val motoristaId = motoristaDoc.id
                    val motoristaNome = motoristaDoc.getString("nome") ?: "Motorista"
                    
                    // Buscar quinzena existente ou criar vazia
                    val quinzena = quinzenasPorMotorista[motoristaId] ?: QuinzenaMotorista(
                        motoristaId = motoristaId,
                        motoristaNome = motoristaNome,
                        baseId = baseId,
                        mes = mes,
                        ano = ano,
                        primeiraQuinzena = QuinzenaDetalhes(),
                        segundaQuinzena = QuinzenaDetalhes(),
                        atualizadoEm = System.currentTimeMillis()
                    )
                    
                    // Garantir que o nome está atualizado
                    quinzena.copy(motoristaNome = motoristaNome)
                }
                
                _todasQuinzenas.value = todasQuinzenas.sortedBy { it.motoristaNome }
                println("✅ QuinzenaViewModel: ${todasQuinzenas.size} quinzenas carregadas (incluindo motoristas sem dias trabalhados)")
                
            } catch (e: Exception) {
                _error.value = "Erro ao carregar quinzenas: ${e.message}"
                println("❌ QuinzenaViewModel: Erro - ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Incrementa um dia trabalhado para o motorista
     * Chamado quando o motorista marca "CONCLUIDO"
     */
    suspend fun incrementarDiaTrabalhado(
        baseId: String,
        motoristaId: String,
        motoristaNome: String,
        data: String  // formato: "dd/MM/yyyy"
    ) {
        try {
            println("📊 QuinzenaViewModel.incrementarDiaTrabalhado: Iniciando")
            println("   motoristaId: $motoristaId")
            println("   motoristaNome: $motoristaNome")
            println("   baseId: $baseId")
            println("   data: $data")
            
            val calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dataDate = sdf.parse(data) ?: run {
                println("❌ QuinzenaViewModel: Erro ao fazer parse da data: $data")
                _error.value = "Erro ao fazer parse da data: $data"
                return
            }
            
            calendar.time = dataDate
            val dia = calendar.get(Calendar.DAY_OF_MONTH)
            val mes = calendar.get(Calendar.MONTH) + 1
            val ano = calendar.get(Calendar.YEAR)
            
            val docId = "${baseId}_${motoristaId}_${mes}_${ano}"
            println("📄 QuinzenaViewModel: Documento ID: $docId")
            
            val docRef = firestore.collection("quinzenas").document(docId)

            // B10 da auditoria: era get() → modifica em memória → set() completo,
            // sem transação — dois escritores simultâneos (admin marca concluído
            // enquanto o motorista confirma) perdiam um incremento. E o MESMO dia
            // marcado duas vezes contava 2 dias trabalhados (dado que alimenta
            // pagamento). Agora: transação + datas distintas (mesmo dia nunca
            // conta duas vezes).
            val isPrimeiraQuinzena = dia <= 15

            val quinzenaAtualizada = firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                val quinzena = if (snap.exists()) {
                    snap.toObject(QuinzenaMotorista::class.java)
                        ?: QuinzenaMotorista(
                            motoristaId = motoristaId,
                            motoristaNome = motoristaNome,
                            baseId = baseId,
                            mes = mes,
                            ano = ano
                        )
                } else {
                    QuinzenaMotorista(
                        motoristaId = motoristaId,
                        motoristaNome = motoristaNome,
                        baseId = baseId,
                        mes = mes,
                        ano = ano
                    )
                }

                if (isPrimeiraQuinzena) {
                    val novasDatas = (quinzena.primeiraQuinzena.datas + data).distinct()
                    if (novasDatas.size == quinzena.primeiraQuinzena.datas.size) {
                        println("ℹ️ QuinzenaViewModel: dia $data já estava contado — nada a somar")
                        return@runTransaction quinzena
                    }
                    println("✅ QuinzenaViewModel: Adicionando data $data à primeira quinzena")
                    val nova = quinzena.copy(
                        primeiraQuinzena = QuinzenaDetalhes(
                            diasTrabalhados = novasDatas.size,
                            datas = novasDatas
                        ),
                        atualizadoEm = System.currentTimeMillis()
                    )
                    println("   Novo total: ${nova.primeiraQuinzena.diasTrabalhados} dias trabalhados")
                    nova
                } else {
                    val novasDatas = (quinzena.segundaQuinzena.datas + data).distinct()
                    if (novasDatas.size == quinzena.segundaQuinzena.datas.size) {
                        println("ℹ️ QuinzenaViewModel: dia $data já estava contado — nada a somar")
                        return@runTransaction quinzena
                    }
                    println("✅ QuinzenaViewModel: Adicionando data $data à segunda quinzena")
                    val nova = quinzena.copy(
                        segundaQuinzena = QuinzenaDetalhes(
                            diasTrabalhados = novasDatas.size,
                            datas = novasDatas
                        ),
                        atualizadoEm = System.currentTimeMillis()
                    )
                    println("   Novo total: ${nova.segundaQuinzena.diasTrabalhados} dias trabalhados")
                    nova
                }.also { tx.set(docRef, it) }
            }.await()

            println("✅ QuinzenaViewModel: Quinzena salva com sucesso (transação)!")

            // Atualizar estado local se for a quinzena atual
            val calendarAtual = Calendar.getInstance()
            val mesAtual = calendarAtual.get(Calendar.MONTH) + 1
            val anoAtual = calendarAtual.get(Calendar.YEAR)
            if (mes == mesAtual && ano == anoAtual) {
                _minhaQuinzena.value = quinzenaAtualizada
                println("✅ QuinzenaViewModel: Estado local atualizado")
            }

        } catch (e: Exception) {
            println("❌ QuinzenaViewModel: Erro ao incrementar dia trabalhado: ${e.message}")
            e.printStackTrace()
            _error.value = "Erro ao incrementar dia trabalhado: ${e.message}"
        }
    }
    
    /**
     * Remove todos os dias trabalhados de uma data específica
     * Chamado quando o admin clica 3 vezes no mesmo dia
     */
    suspend fun removerDiaTrabalhado(
        baseId: String,
        motoristaId: String,
        motoristaNome: String,
        data: String  // formato: "dd/MM/yyyy"
    ) {
        try {
            println("🗑️ QuinzenaViewModel.removerDiaTrabalhado: Iniciando")
            println("   motoristaId: $motoristaId")
            println("   motoristaNome: $motoristaNome")
            println("   baseId: $baseId")
            println("   data: $data")
            
            val calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dataDate = sdf.parse(data) ?: run {
                println("❌ QuinzenaViewModel: Erro ao fazer parse da data: $data")
                _error.value = "Erro ao fazer parse da data: $data"
                return
            }
            
            calendar.time = dataDate
            val dia = calendar.get(Calendar.DAY_OF_MONTH)
            val mes = calendar.get(Calendar.MONTH) + 1
            val ano = calendar.get(Calendar.YEAR)
            
            val docId = "${baseId}_${motoristaId}_${mes}_${ano}"
            println("📄 QuinzenaViewModel: Documento ID: $docId")
            
            val docRef = firestore.collection("quinzenas").document(docId)

            // B10: mesma correção do incremento — transação em vez de RMW solto
            // (dois escritores simultâneos não perdem mais alteração)
            val isPrimeiraQuinzena = dia <= 15

            val quinzenaAtualizada = firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    println("⚠️ QuinzenaViewModel: Documento não existe, não há nada para remover")
                    return@runTransaction null
                }
                val quinzena = snap.toObject(QuinzenaMotorista::class.java) ?: run {
                    println("❌ QuinzenaViewModel: Erro ao converter documento")
                    return@runTransaction null
                }

                if (isPrimeiraQuinzena) {
                    println("🗑️ QuinzenaViewModel: Removendo todas as ocorrências da data $data da primeira quinzena")
                    val datasFiltradas = quinzena.primeiraQuinzena.datas.filter { it != data }
                    val nova = quinzena.copy(
                        primeiraQuinzena = QuinzenaDetalhes(
                            diasTrabalhados = datasFiltradas.size,
                            datas = datasFiltradas
                        ),
                        atualizadoEm = System.currentTimeMillis()
                    )
                    println("   Novo total: ${nova.primeiraQuinzena.diasTrabalhados} dias trabalhados (removidas todas as ocorrências de $data)")
                    tx.set(docRef, nova)
                    nova
                } else {
                    println("🗑️ QuinzenaViewModel: Removendo todas as ocorrências da data $data da segunda quinzena")
                    val datasFiltradas = quinzena.segundaQuinzena.datas.filter { it != data }
                    val nova = quinzena.copy(
                        segundaQuinzena = QuinzenaDetalhes(
                            diasTrabalhados = datasFiltradas.size,
                            datas = datasFiltradas
                        ),
                        atualizadoEm = System.currentTimeMillis()
                    )
                    println("   Novo total: ${nova.segundaQuinzena.diasTrabalhados} dias trabalhados (removidas todas as ocorrências de $data)")
                    tx.set(docRef, nova)
                    nova
                }
            }.await()

            if (quinzenaAtualizada == null) {
                return
            }

            println("✅ QuinzenaViewModel: Quinzena salva com sucesso após remoção (transação)!")

            // Atualizar estado local se for a quinzena atual
            val calendarAtual = Calendar.getInstance()
            val mesAtual = calendarAtual.get(Calendar.MONTH) + 1
            val anoAtual = calendarAtual.get(Calendar.YEAR)
            if (mes == mesAtual && ano == anoAtual) {
                _minhaQuinzena.value = quinzenaAtualizada
                println("✅ QuinzenaViewModel: Estado local atualizado após remoção")
            }

        } catch (e: Exception) {
            println("❌ QuinzenaViewModel: Erro ao remover dia trabalhado: ${e.message}")
            e.printStackTrace()
            _error.value = "Erro ao remover dia trabalhado: ${e.message}"
        }
    }
    
    /**
     * Gera resumo de todas as quinzenas para exibição
     */
    fun gerarResumos(): List<QuinzenaResumo> {
        return _todasQuinzenas.value.map { quinzena ->
            QuinzenaResumo(
                motoristaId = quinzena.motoristaId,
                motoristaNome = quinzena.motoristaNome,
                primeiraQuinzenaDias = quinzena.primeiraQuinzena.diasTrabalhados,
                segundaQuinzenaDias = quinzena.segundaQuinzena.diasTrabalhados,
                totalDias = quinzena.primeiraQuinzena.diasTrabalhados + quinzena.segundaQuinzena.diasTrabalhados
            )
        }.sortedByDescending { it.totalDias }
    }
    
    /**
     * Limpa mensagem de erro
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * FUNÇÃO TEMPORÁRIA PARA TESTES
     * Limpa a quinzena de TODOS os motoristas da base
     * ATENÇÃO: Esta função deve ser removida em produção!
     */
    fun limparTodasQuinzenas(baseId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                println("🗑️ QuinzenaViewModel.limparTodasQuinzenas: Iniciando limpeza de todas as quinzenas")
                
                val calendar = Calendar.getInstance()
                val mes = calendar.get(Calendar.MONTH) + 1
                val ano = calendar.get(Calendar.YEAR)
                
                // Buscar todas as quinzenas do mês atual
                val quinzenasSnapshot = firestore.collection("quinzenas")
                    .whereEqualTo("baseId", baseId)
                    .whereEqualTo("mes", mes)
                    .whereEqualTo("ano", ano)
                    .get()
                    .await()
                
                println("📋 QuinzenaViewModel: ${quinzenasSnapshot.documents.size} quinzenas encontradas para limpeza")
                
                // Limpar cada quinzena
                quinzenasSnapshot.documents.forEach { doc ->
                    val quinzena = doc.toObject(QuinzenaMotorista::class.java)
                    if (quinzena != null) {
                        val quinzenaVazia = quinzena.copy(
                            primeiraQuinzena = QuinzenaDetalhes(),
                            segundaQuinzena = QuinzenaDetalhes(),
                            atualizadoEm = System.currentTimeMillis()
                        )
                        doc.reference.set(quinzenaVazia).await()
                        println("✅ QuinzenaViewModel: Quinzena de ${quinzena.motoristaNome} limpa")
                    }
                }
                
                println("✅ QuinzenaViewModel: Todas as quinzenas foram limpas com sucesso")
                
                // Recarregar as quinzenas
                carregarTodasQuinzenas(baseId)
                
            } catch (e: Exception) {
                println("❌ QuinzenaViewModel: Erro ao limpar todas as quinzenas: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao limpar quinzenas: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * FUNÇÃO TEMPORÁRIA PARA TESTES
     * Reseta a quinzena de um motorista (remove todos os dias trabalhados)
     * ATENÇÃO: Esta função deve ser removida em produção!
     */
    suspend fun resetarQuinzena(baseId: String, motoristaId: String, mes: Int, ano: Int) {
        try {
            println("🔄 QuinzenaViewModel.resetarQuinzena: Iniciando reset")
            println("   baseId: $baseId, motoristaId: $motoristaId, mes: $mes, ano: $ano")
            
            val docId = "${baseId}_${motoristaId}_${mes}_${ano}"
            val docRef = firestore.collection("quinzenas").document(docId)
            
            // Verificar se existe antes de resetar
            val doc = docRef.get().await()
            if (doc.exists()) {
                val quinzenaAntiga = doc.toObject(QuinzenaMotorista::class.java)
                println("📋 QuinzenaViewModel: Quinzena atual encontrada:")
                println("   Primeira quinzena: ${quinzenaAntiga?.primeiraQuinzena?.diasTrabalhados} dias, ${quinzenaAntiga?.primeiraQuinzena?.datas?.size} datas")
                println("   Segunda quinzena: ${quinzenaAntiga?.segundaQuinzena?.diasTrabalhados} dias, ${quinzenaAntiga?.segundaQuinzena?.datas?.size} datas")
            }
            
            // Criar quinzena vazia
            val quinzenaVazia = QuinzenaMotorista(
                motoristaId = motoristaId,
                motoristaNome = _minhaQuinzena.value?.motoristaNome ?: "Motorista",
                baseId = baseId,
                mes = mes,
                ano = ano,
                primeiraQuinzena = QuinzenaDetalhes(),
                segundaQuinzena = QuinzenaDetalhes(),
                atualizadoEm = System.currentTimeMillis()
            )
            
            println("💾 QuinzenaViewModel: Salvando quinzena vazia no Firestore...")
            docRef.set(quinzenaVazia).await()
            println("✅ QuinzenaViewModel: Quinzena vazia salva no Firestore")
            
            // Atualizar estado local imediatamente
            _minhaQuinzena.value = quinzenaVazia
            println("✅ QuinzenaViewModel: Estado local atualizado")
            
            // Recarregar do Firestore para garantir sincronização
            println("🔄 QuinzenaViewModel: Recarregando quinzena do Firestore...")
            carregarMinhaQuinzenaPorMes(baseId, motoristaId, mes, ano)
            println("✅ QuinzenaViewModel: Quinzena resetada e recarregada com sucesso")
            
        } catch (e: Exception) {
            println("❌ QuinzenaViewModel: Erro ao resetar quinzena: ${e.message}")
            e.printStackTrace()
            _error.value = "Erro ao resetar quinzena: ${e.message}"
        }
    }
}
