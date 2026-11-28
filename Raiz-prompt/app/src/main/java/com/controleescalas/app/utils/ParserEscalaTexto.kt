package com.controleescalas.app.utils

import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import java.text.Normalizer
import java.util.UUID

/**
 * Parser da escala colada como TEXTO (mensagem do WhatsApp).
 *
 * Entende três formatos — inclusive misturados numa mesma colagem:
 *
 * 1. Ondas com docas:
 *    Onda 1
 *    • Doca 1: J8_AM1, Davi
 *
 * 2. Planejamento com traços:
 *    🟢 ONDA 1
 *    01 — D11 — Uebster
 *    Vaga 46 — S6 — Patrick
 *
 * 3. Lista numerada (sem ondas):
 *    1. G3_AM1 Nathan
 *
 * Regras:
 * - Cabeçalhos com "ONDA n" abrem onda; numeração que reinicia (volumosos)
 *   é renumerada em sequência única na aplicação.
 * - "Início 6:25h" vira o horário da primeira onda.
 * - Linhas sem onda antes do primeiro cabeçalho são ignoradas SE houver
 *   ondas no texto (é a "relação de rotas", que reaparece dentro das ondas);
 *   se o texto NÃO tem onda nenhuma, viram a 1ª onda com vaga em sequência.
 * - Marcadores "(Saca)", "(VUC)", "(utilitário)"… viram nota do item.
 * - Linhas com "❌" (motorista não definido) são ignoradas.
 */
object ParserEscalaTexto {

    data class ItemEscalaTexto(
        val rota: String,
        val nome: String,
        val vaga: String,
        val nota: String,
        val volumoso: Boolean = false
    )

    data class OndaEscalaTexto(
        val itens: List<ItemEscalaTexto>,
        val volumosos: Boolean
    )

    data class EscalaTexto(
        val ondas: List<OndaEscalaTexto>,
        val inicio: String,
        val totalItens: Int,
        val ignoradas: Int
    )

    private val regexOnda = Regex("ONDA\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val regexDoca =
        Regex("^•?\\s*DOCA\\s*(\\d{1,3})\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val regexVagaTraco =
        Regex("^(?:VAGA\\s*)?(\\d{1,3})\\s*[—–\\-]+\\s*(.+)$", RegexOption.IGNORE_CASE)
    // Sem âncoras: find() extrai o código mesmo quando o token vem colado
    // a vírgula ou pontuação ("J8_AM1, Davi")
    private val regexRotaToken = Regex("[A-Z]{1,4}\\d{1,3}(?:_[A-Z0-9]{1,4})?")
    private val regexNotas = Regex("\\(([^)]*)\\)")
    private val regexInicio = Regex("IN[ÍI]?CIO[^0-9]*(\\d{1,2})\\s*[:hH]\\s*(\\d{2})", RegexOption.IGNORE_CASE)
    private val regexSoTurno = Regex("^(AM|PM)\\s*$", RegexOption.IGNORE_CASE)

    fun parse(texto: String): EscalaTexto {
        var inicio = ""
        var ignoradas = 0
        var secaoVolumosos = false
        var ondaAberta: Int? = null
        val ondasPorNumero = LinkedHashMap<Int, MutableList<ItemEscalaTexto>>()
        val soltos = mutableListOf<ItemEscalaTexto>()

        for (linha in texto.lines().map { it.trim() }) {
            if (linha.isBlank()) continue
            val maiuscula = linha.uppercase()

            // Cabeçalho de onda. Dentro de "📦 VOLUMOSOS", "ONDA n" ANEXA na onda
            // n que já existe — os volumosos pertencem às ondas 1, 2, 3, não são
            // uma onda nova. Fora da seção volumosa, o texto repetindo o mesmo
            // número (mensagens duplicadas no WhatsApp) RECOMEÇA a onda, sem
            // duplicar itens.
            if (regexOnda.containsMatchIn(maiuscula)) {
                val numero = regexOnda.find(maiuscula)!!.groupValues[1].toInt()
                val lista = ondasPorNumero[numero]
                ondaAberta = numero
                if (lista == null) {
                    ondasPorNumero[numero] = mutableListOf()
                } else if (!secaoVolumosos) {
                    lista.clear()
                }
                continue
            }

            // Horário de início do turno ("Início 6:25h") — vem antes dos saltos
            // de seção porque a linha do planejamento pode trazer o horário junto
            val matchInicio = regexInicio.find(maiuscula)
            if (matchInicio != null) {
                if (inicio.isBlank()) {
                    inicio = "%02d:%02d".format(
                        matchInicio.groupValues[1].toInt(),
                        matchInicio.groupValues[2].toInt()
                    )
                }
                continue
            }

            // Marca a seção como volumosos (ex.: "📦 VOLUMOSOS")
            if (maiuscula.contains("VOLUMOSO")) {
                secaoVolumosos = true
                continue
            }
            if (maiuscula.contains("PLANEJAMENTO") || maiuscula.contains("RELAÇÃO") || maiuscula.contains("RELACAO")) {
                secaoVolumosos = false
                continue
            }

            // "AM" / "PM" sozinhos: cabeçalho de turno, ignora
            if (regexSoTurno.containsMatchIn(maiuscula)) continue

            // Linhas com ❌: motorista não definido — ignora
            if (linha.contains("❌")) {
                ignoradas++
                continue
            }

            val item = parseItem(linha, secaoVolumosos)
            if (item != null) {
                val destino = ondaAberta?.let { ondasPorNumero.getOrPut(it) { mutableListOf() } }
                if (destino != null) destino.add(item) else soltos.add(item)
            } else {
                ignoradas++
            }
        }

        // Sem cabeçalho de onda no texto (lista numerada sozinha): vira a 1ª onda
        // com vaga em sequência; com ondas no texto, os itens soltos pré-cabeçalho
        // são a "relação de rotas" e reaparecem dentro das ondas — descartados.
        val ondasFinais: List<OndaEscalaTexto> = if (ondasPorNumero.isEmpty() && soltos.isNotEmpty()) {
            listOf(
                OndaEscalaTexto(
                    itens = soltos.mapIndexed { i, item ->
                        item.copy(vaga = item.vaga.ifBlank { "%02d".format(i + 1) })
                    },
                    volumosos = false
                )
            )
        } else {
            if (soltos.isNotEmpty()) ignoradas += soltos.size
            ondasPorNumero.toSortedMap().values.map { itens ->
                OndaEscalaTexto(itens, itens.any { it.volumoso })
            }
        }

        val total = ondasFinais.sumOf { it.itens.size }
        return EscalaTexto(ondasFinais, inicio, total, ignoradas)
    }

    /**
     * Tenta interpretar a linha como item nos três formatos.
     * null = não é linha de item (ignorada).
     */
    private fun parseItem(linha: String, secaoVolumosos: Boolean): ItemEscalaTexto? {
        val limpa = linha.replace("^•\\s*".toRegex(), "").trim()
        if (limpa.isBlank()) return null

        // 1) "Doca X: ROTA, Nome" (ou "Doca X: ROTA Nome")
        regexDoca.find(limpa)?.let { m ->
            val vaga = m.groupValues[1].padStart(2, '0')
            val (rota, nome, nota) = separarRotaNome(m.groupValues[2].trim())
            if (rota == null) return null
            return ItemEscalaTexto(rota, nome, vaga, nota, secaoVolumosos)
        }

        // 2) "NN — ROTA — Nome" (traço, com ou sem "Vaga" na frente)
        regexVagaTraco.find(limpa)?.let { m ->
            val vaga = m.groupValues[1].padStart(2, '0')
            val resto = m.groupValues[2].trim().replace("^[—–\\-]+\\s*".toRegex(), "")
            val (rota, nome, nota) = separarRotaNome(resto)
            if (rota == null) return null
            return ItemEscalaTexto(rota, nome, vaga, nota, secaoVolumosos)
        }

        // 3) "N. ROTA Nome" ou "ROTA Nome" (sem vaga)
        val m3Original = Regex("^(?:\\d{1,3}\\s*[.)]\\s+)?([A-Za-z]{1,4}\\d{1,3}(?:_[A-Za-z0-9]{1,4})?)\\s+(.+)$")
            .find(limpa)
        if (m3Original != null) {
            val rota = m3Original.groupValues[1].uppercase()
            // Nome pode começar com o traço do formato ("K4 — Moisés")
            val (nome, nota) = extrairNomeENota(
                m3Original.groupValues[2].replace("^[—–\\-]+\\s*".toRegex(), "")
            )
            return ItemEscalaTexto(rota, nome, "", nota, secaoVolumosos)
        }

        return null
    }

    /** "ROTA Nome (notas)" ou "ROTA, Nome (notas)" → rota, nome limpo, nota */
    private fun separarRotaNome(resto: String): Triple<String?, String, String> {
        val (nomeSemNotas, nota) = extrairNomeENota(resto)
        val tokens = nomeSemNotas.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return Triple(null, "", "")

        val rota = regexRotaToken.find(tokens.first().uppercase())?.value
            ?: tokens.first().uppercase()
        val nomeDepois = tokens.drop(1)
            .joinToString(" ")
            .replace("^[—–\\-,]+\\s*".toRegex(), "")
            .trim()
        return Triple(rota, nomeDepois, nota)
    }

    /** Separa "(Saca) (VUC)" etc. do nome e devolve nome limpo + nota concatenada */
    private fun extrairNomeENota(texto: String): Pair<String, String> {
        val notas = regexNotas.findAll(texto).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }
        val nomeLimpo = texto.replace(regexNotas, " ").replace("\\s+".toRegex(), " ").trim()
        return Pair(nomeLimpo, notas.joinToString(", "))
    }

    /** Minúsculas, sem acentos, espaços colapsados — base da comparação de nomes */
    fun normalizarNome(nome: String): String =
        Normalizer.normalize(nome, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .trim()

    /**
     * Vincula o nome do texto a um motorista cadastrado:
     * igual exato (sem acentos) → nome contido em um cadastro (ou vice-versa)
     * → primeiro nome igual. null = sem vínculo (vira LIVRE).
     */
    fun vincularMotorista(
        nomeTexto: String,
        motoristas: List<AdminMotoristaCardData>
    ): AdminMotoristaCardData? {
        val alvo = normalizarNome(nomeTexto)
        if (alvo.isBlank()) return null
        val cadastrados = motoristas.map { it to normalizarNome(it.nome) }
        cadastrados.firstOrNull { it.second == alvo }?.let { return it.first }
        cadastrados.firstOrNull { it.second.contains(alvo) || alvo.contains(it.second) }?.let { return it.first }
        val primeiroNome = alvo.substringBefore(' ')
        return cadastrados
            .firstOrNull { it.second.substringBefore(' ') == primeiroNome }
            ?.first
    }

    /**
     * Converte o texto parseado em Ondas do app, vinculando motoristas por nome.
     * Nome sem vínculo vira item LIVRE (rota/vaga preservadas) para o admin
     * completar com um toque no painel.
     */
    fun converterEmOndas(
        escala: EscalaTexto,
        motoristas: List<AdminMotoristaCardData>
    ): List<Onda> {
        return escala.ondas.mapIndexed { indice, onda ->
            val nomeOnda = "${indice + 1}ª ONDA" + if (onda.volumosos) " · VOLUMOSOS" else ""
            val itens = onda.itens
                .map { item ->
                    val motorista = vincularMotorista(item.nome, motoristas)
                    if (motorista != null) {
                        OndaItem(
                            motoristaId = motorista.id,
                            nome = motorista.nome,
                            vaga = item.vaga,
                            rota = item.rota,
                            modalidade = motorista.modalidade,
                            nota = item.nota.ifBlank { null }
                        )
                    } else {
                        OndaItem(
                            motoristaId = "slot_" + UUID.randomUUID().toString(),
                            nome = "",
                            vaga = item.vaga,
                            rota = item.rota,
                            nota = item.nota.ifBlank { null }
                        )
                    }
                }
                .sortedBy { it.vaga.toIntOrNull() ?: Int.MAX_VALUE }
            Onda(
                nome = nomeOnda,
                horario = if (indice == 0) escala.inicio else "",
                itens = itens
            )
        }
    }
}
