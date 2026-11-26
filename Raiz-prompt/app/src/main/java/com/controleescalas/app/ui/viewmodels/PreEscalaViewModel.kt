package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.Repository
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.isSlot
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.utils.BrasilDate
import com.controleescalas.app.utils.LinhaImportada
import com.controleescalas.app.utils.OndaTableParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

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

        /**
         * Mescla as ondas da tabela com quem já foi escalado. [escalados] são os
         * itens reais da escala atual (PRÉ-ESCALA ou ondas antigas); cada um é
         * consumido pela primeira rota da tabela que casa com a sua
         * (ver [OndaTableParser.rotasCasa]) e vai para a onda e a vaga dela.
         * Devolve as ondas da tabela — PRÉ-ESCALA por último, se sobrar alguém —
         * e quantos motoristas foram colocados.
         */
        fun mesclarComEscalados(
            escalados: List<OndaItem>,
            horarioPreEscala: String,
            linhasPorOnda: Map<Int, List<LinhaImportada>>
        ): Pair<List<Onda>, Int> {
            val pendentes = escalados.toMutableList()
            var colocados = 0

            val ondasDaTabela = linhasPorOnda.map { (numero, linhasDaOnda) ->
                Onda(
                    nome = "${numero}ª ONDA",
                    horario = "",
                    itens = linhasDaOnda.map { linha ->
                        val rota = linha.rota.uppercase()
                        val vaga = linha.doca?.let { it.toString().padStart(2, '0') } ?: ""
                        val idx = pendentes.indexOfFirst { OndaTableParser.rotasCasa(rota, it.rota) }
                        if (idx >= 0) {
                            colocados++
                            // Rota e vaga vêm da tabela; id, nome, modalidade, sacas e nota continuam
                            pendentes.removeAt(idx).copy(rota = rota, vaga = vaga, horario = "")
                        } else {
                            OndaItem(
                                motoristaId = "slot_" + UUID.randomUUID().toString(),
                                nome = "",
                                rota = rota,
                                vaga = vaga,
                                horario = "",
                                modalidade = "FROTA"
                            )
                        }
                    }
                )
            }

            // Quem não casa com rota nenhuma da tabela continua na PRÉ-ESCALA
            val ondasFinais = if (pendentes.isEmpty()) {
                ondasDaTabela
            } else {
                ondasDaTabela + Onda(
                    nome = NOME_ONDA_PRE_ESCALA,
                    horario = horarioPreEscala,
                    itens = pendentes.toList()
                )
            }
            return ondasFinais to colocados
        }
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

    // Importação da tabela de ondas por OCR
    private val ocrRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val _linhasImportadas = MutableStateFlow<List<LinhaImportada>>(emptyList())
    val linhasImportadas: StateFlow<List<LinhaImportada>> = _linhasImportadas.asStateFlow()
    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr.asStateFlow()

    // Rotas QR ROTA lidas da planilha grande (modo escala rápida: só chips)
    private val _rotasQrLidas = MutableStateFlow<List<String>>(emptyList())
    val rotasQrLidas: StateFlow<List<String>> = _rotasQrLidas.asStateFlow()

    // Motoristas já escalados na data/turno: rota -> nome (prévia do import de ondas)
    private val _escaladosPorRota = MutableStateFlow<Map<String, String>>(emptyMap())
    val escaladosPorRota: StateFlow<Map<String, String>> = _escaladosPorRota.asStateFlow()

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
     * OCR da foto da tabela de ondas → lista de linhas (onda, rota, doca)
     * para o admin conferir antes de aplicar.
     * Usa os ELEMENTOS com bounding box do ML Kit (cada célula da tabela),
     * reconstruindo as linhas por posição — robusto quando o OCR não junta
     * as colunas com espaços.
     */
    fun processarImagemTabela(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessingOcr.value = true
            _error.value = null
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    ocrRecognizer.process(inputImage).await()
                }
                val elementos = resultado.textBlocks.flatMap { bloco ->
                    bloco.lines.flatMap { linha ->
                        linha.elements.map { el ->
                            OndaTableParser.ElementoOcr(
                                texto = el.text,
                                x = el.boundingBox?.left ?: 0,
                                y = el.boundingBox?.top ?: 0,
                                altura = el.boundingBox?.height() ?: 0
                            )
                        }
                    }
                }
                println("🔍 PreEscalaVM OCR: blocos=${resultado.textBlocks.size}, elementos=${elementos.size}, texto='${resultado.text.take(200)}'")
                val linhas = if (elementos.isNotEmpty()) {
                    OndaTableParser.parseTabelaOndasElementos(elementos)
                } else {
                    OndaTableParser.parseTabelaOndasTexto(resultado.text)
                }
                _linhasImportadas.value = linhas

                // QR ROTAs da planilha grande: extração tolerante (aceita a rota
                // completa, separada por espaço ou colada pelo OCR)
                val rotasQr = OndaTableParser.extrairRotasQr(resultado.text.uppercase())
                _rotasQrLidas.value = rotasQr
                println("🔍 PreEscalaVM OCR: ${rotasQr.size} QR ROTAs extraídas")

                if (linhas.isEmpty() && rotasQr.isEmpty()) {
                    _error.value = "Nenhuma rota lida na imagem. Tente uma foto mais nítida."
                } else {
                    _error.value = null
                }
            } catch (e: Exception) {
                println("❌ PreEscalaVM: Erro no OCR: ${e.message}")
                _error.value = "Erro ao ler a imagem: ${e.message}"
                _linhasImportadas.value = emptyList()
            } finally {
                _isProcessingOcr.value = false
            }
        }
    }

    fun limparLinhasImportadas() {
        _linhasImportadas.value = emptyList()
    }

    fun removerRotaQr(index: Int) {
        val atual = _rotasQrLidas.value.toMutableList()
        if (index in atual.indices) {
            atual.removeAt(index)
            _rotasQrLidas.value = atual
        }
    }

    fun limparRotasQr() {
        _rotasQrLidas.value = emptyList()
    }

    /**
     * Define as rotas QR lidas de texto colado (planilha copiada do Excel).
     */
    fun definirRotasQr(rotas: List<String>) {
        _rotasQrLidas.value = rotas
    }

    fun removerLinhaImportada(index: Int) {
        val atual = _linhasImportadas.value.toMutableList()
        if (index in atual.indices) {
            atual.removeAt(index)
            _linhasImportadas.value = atual
        }
    }

    /**
     * Aplica a tabela importada no turno SELECIONADO e aproveita quem já foi
     * escalado: a pré-escala disse quem pega cada rota, a tabela diz a onda e a
     * vaga (doca) de cada uma. Cada rota da tabela vira um item da sua onda —
     * com o motorista já escalado para ela ou como slot LIVRE
     * (motoristaId "slot_<uuid>", vaga = doca). Quem não casa com rota nenhuma
     * da tabela continua na onda "PRÉ-ESCALA".
     * Linhas sem onda (planilha grande) não criam estrutura.
     */
    fun aplicarRotasImportadas(
        baseId: String,
        data: String,
        turnoSelecionado: String,
        linhas: List<LinhaImportada>,
        onConcluido: (turnosAplicados: List<String>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                // Turno é o selecionado na tela: os dois passos (pré-escala e
                // tabela de ondas) gravam no mesmo documento {data}_{turno}
                val linhasPorOnda = linhas.filter { it.onda != null }
                    .sortedBy { it.onda!! }
                    .groupBy { it.onda!! }
                val escala = escalaRepository.getEscalaByDateAndTurno(baseId, data, turnoSelecionado)
                    ?: Escala(baseId = baseId, data = data, turno = turnoSelecionado, ondas = emptyList())

                val escalados = escala.ondas.flatMap { it.itens }
                    .filter { !it.isSlot && it.motoristaId.isNotBlank() }
                val horarioPreEscala = escala.ondas
                    .firstOrNull { it.nome.equals(NOME_ONDA_PRE_ESCALA, ignoreCase = true) }
                    ?.horario ?: ""
                val (ondasFinais, colocados) = mesclarComEscalados(escalados, horarioPreEscala, linhasPorOnda)

                escalaRepository.saveEscala(
                    baseId,
                    escala.copy(ondas = ondasFinais, data = data, turno = turnoSelecionado)
                )

                val rotasLivres = ondasFinais.sumOf { onda -> onda.itens.count { it.isSlot } }
                val naPreEscala = ondasFinais
                    .filter { it.nome.equals(NOME_ONDA_PRE_ESCALA, ignoreCase = true) }
                    .sumOf { it.itens.size }
                _message.value = "Import aplicado — $turnoSelecionado: ${linhasPorOnda.size} ondas, " +
                    "$colocados motoristas colocados, $rotasLivres rotas livres" +
                    (if (naPreEscala > 0) ", $naPreEscala seguiram na pré-escala" else "")
                println("✅ PreEscalaVM: Import aplicado ($data/$turnoSelecionado): ${linhasPorOnda.size} ondas, $colocados colocados, $rotasLivres livres")
                // Avisa a tela o turno aplicado
                onConcluido(listOf(turnoSelecionado))
            } catch (e: Exception) {
                println("❌ PreEscalaVM: Erro ao aplicar import: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao aplicar import: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
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

    /**
     * Rotas que já têm motorista na escala da data/turno (PRÉ-ESCALA ou ondas):
     * rota -> nome. Alimenta a prévia do import de ondas, que mostra quem já
     * vai ser colocado em cada rota da tabela.
     */
    fun carregarEscalados(baseId: String, data: String, turno: String) {
        _escaladosPorRota.value = emptyMap()
        viewModelScope.launch {
            val escalados = try {
                val ondas = escalaRepository.getEscalaByDateAndTurno(baseId, data, turno)?.ondas.orEmpty()
                ondas.flatMap { it.itens }
                    .filter { !it.isSlot && it.motoristaId.isNotBlank() && it.rota.isNotBlank() }
                    .associate { it.rota.trim().uppercase() to it.nome }
            } catch (e: Exception) {
                println("⚠️ PreEscalaVM: Erro ao carregar escalados: ${e.message}")
                emptyMap<String, String>()
            }
            _escaladosPorRota.value = escalados
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
     * Rotas vindas da importação da planilha (foto) entram nas sugestões
     * junto com as recentes, sem duplicar.
     */
    fun adicionarRotasExtras(rotas: List<String>) {
        val novas = rotas
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .filter { it !in _rotasRecentes.value }
        if (novas.isNotEmpty()) {
            _rotasRecentes.value = (_rotasRecentes.value + novas).distinct().take(80)
            println("✅ PreEscalaVM: ${novas.size} rotas importadas adicionadas às sugestões")
        }
    }

    /**
     * Salva a pré-escala no doc {data}_{turno}.
     * Cada motorista atribuído PRENCHE o slot da sua rota quando ele existe
     * (import da tabela): o item real substitui o slot, mantendo vaga/horário.
     * Rotas sem slot (adicionadas à mão) vão para a onda "PRÉ-ESCALA" como hoje.
     * PRÉ-ESCALA vazia é removida no fim.
     * [onResultado] avisa se o salvar deu certo (o dialog só compartilha depois).
     */
    fun salvarPreEscala(
        baseId: String,
        data: String,
        turno: String,
        horario: String,
        itens: List<OndaItem>,
        onResultado: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val escala = escalaRepository.getEscalaByDateAndTurno(baseId, data, turno)
                    ?: Escala(baseId = baseId, data = data, turno = turno, ondas = emptyList())

                var ondas = escala.ondas.toMutableList()
                val itensPreEscalada = mutableListOf<OndaItem>()

                itens.forEach { item ->
                    val rotaUpper = item.rota.uppercase()
                    // Regra de ouro: motorista só em UMA onda do turno — antes de
                    // preencher o slot, remove-o de qualquer outra onda (recriando
                    // o slot LIVRE quando havia rota/vaga da tabela)
                    val idxAnterior = ondas.indexOfFirst { onda ->
                        onda.itens.any { it.motoristaId == item.motoristaId && !it.isSlot }
                    }
                    if (idxAnterior >= 0) {
                        val ondaAnterior = ondas[idxAnterior]
                        val existente = ondaAnterior.itens.first { it.motoristaId == item.motoristaId }
                        ondas[idxAnterior] = if (existente.rota.isNotBlank()) {
                            ondaAnterior.copy(
                                itens = ondaAnterior.itens.map {
                                    if (it.motoristaId == item.motoristaId) {
                                        it.copy(motoristaId = "slot_" + UUID.randomUUID().toString(), nome = "")
                                    } else it
                                }
                            )
                        } else {
                            ondaAnterior.copy(itens = ondaAnterior.itens.filter { it.motoristaId != item.motoristaId })
                        }
                    }

                    // Procura slot com essa rota em qualquer onda real. A rota
                    // atribuída pode ser só o prefixo (ex.: "F3", digitada) e o
                    // slot tem o código completo ("F3_AM1") — casa pelo prefixo.
                    val idxOnda = ondas.indexOfFirst { onda ->
                        onda.itens.any { it.isSlot && OndaTableParser.rotasCasa(it.rota, rotaUpper) }
                    }
                    if (idxOnda >= 0) {
                        val onda = ondas[idxOnda]
                        val slot = onda.itens.first { it.isSlot && OndaTableParser.rotasCasa(it.rota, rotaUpper) }
                        val itemReal = item.copy(
                            vaga = slot.vaga,      // vaga da doca importada
                            horario = onda.horario,
                            nota = item.nota
                        )
                        val itensNovos = onda.itens.map { if (it.motoristaId == slot.motoristaId) itemReal else it }
                        ondas[idxOnda] = onda.copy(itens = itensNovos)
                    } else {
                        itensPreEscalada.add(item)
                    }
                }

                // Rotas sem slot seguem para a onda PRÉ-ESCALA (comportamento manual)
                if (itensPreEscalada.isNotEmpty()) {
                    val novaOnda = Onda(nome = NOME_ONDA_PRE_ESCALA, horario = horario, itens = itensPreEscalada)
                    val idx = ondas.indexOfFirst { it.nome.equals(NOME_ONDA_PRE_ESCALA, ignoreCase = true) }
                    if (idx >= 0) ondas[idx] = novaOnda else ondas.add(novaOnda)
                } else {
                    // Pré-escala vazia (todos foram para slots) sai da lista
                    ondas = ondas.filterNot { it.nome.equals(NOME_ONDA_PRE_ESCALA, ignoreCase = true) }
                        .toMutableList()
                }

                escalaRepository.saveEscala(
                    baseId,
                    escala.copy(ondas = ondas, data = data, turno = turno)
                )
                println("✅ PreEscalaVM: Pré-escala salva ($data/$turno) com ${itens.size} motoristas")
                _message.value = "Pré-escala salva (${itens.size} motoristas)"
                onResultado?.invoke(true)
            } catch (e: Exception) {
                println("❌ PreEscalaVM: Erro ao salvar pré-escala: ${e.message}")
                e.printStackTrace()
                _error.value = "Erro ao salvar pré-escala: ${e.message}"
                onResultado?.invoke(false)
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
