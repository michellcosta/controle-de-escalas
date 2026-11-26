package com.controleescalas.app.utils

/**
 * Parser da tabela de ondas que a logística envia (foto/planilha colada).
 *
 * Layout principal (tabela de ondas):
 *   Onda 1 | I13_AM1 | AM1_237 | 1 | DELUNA
 *   Onda 2 | VA4_AM1 | AM1_16  | 1 | DELUNA
 *
 * Layout secundário (planilha grande de planejamento, SEM coluna de onda):
 *   DELUNA / SACA | BRNRJ143 | AM1 | AM1_1 | F3_AM1 | 2 | 0 | ...
 *
 * A "rota otimizada" tem sublinhado seguido de LETRAS (I13_AM1, F3_AM1, X10_AM1).
 * A "rota original" tem sublinhado seguido de DÍGITOS (AM1_237, AM1_2).
 * A "doca" é o primeiro número de 1-2 dígitos depois da rota otimizada,
 * pulando o token da rota original (quando existir).
 */
data class LinhaImportada(
    val onda: Int?,   // null = linha da planilha grande (sem coluna de onda)
    val rota: String, // rota otimizada (I13_AM1)
    val doca: Int?,   // vaga do pátio dentro da onda (null = deixar vazia)
    val turno: String? // turno lido do sufixo da rota (I13_AM1 → "AM1")
)

object OndaTableParser {

    // Rota otimizada: LETRAS _ LETRAS(+dígitos opcionais) — ex.: I13_AM1, F3_AM1, X10_AM1, VA4_AM1
    private val regexRotaOtimizada = Regex("^[A-Z]{1,4}\\d{1,3}_[A-Z]{1,4}\\d{0,3}$")

    /**
     * QR ROTA em texto corrido (planilha grande): a coluna QR ROTA é a única
     * com o padrão LETRASnúmero_LETRAS — extrai todas as ocorrências na ordem,
     * ignorando rotas originais (AM1_237), vagas e números.
     */
    val regexQrRota = Regex("[A-Z]{1,4}\\d{1,3}_[A-Z]{1,4}\\d{0,3}")

    private val regexParteASozinha = Regex("^[A-Z]{1,4}\\d{1,3}$")
    private val regexParteBSozinha = Regex("^[A-Z]{2,4}\\d{0,3}$")
    private val regexRotaColada = Regex("^([A-Z]{1,4}\\d{1,3})([A-Z]{1,4}\\d{0,3})$")

    /**
     * Extração TOLERANTE de QR ROTAs do texto do OCR: o ML Kit lê a mesma
     * rota de 3 formas — "VD2_AM1" (completa), "VD2 AM1" (separada) ou
     * "VD2AM1" (colada). Aceita as três e normaliza para "VD2_AM1".
     * Ignora números, rotas originais (AM1_237) e palavras sem dígitos.
     */
    fun extrairRotasQr(texto: String): List<String> {
        val tokens = texto.uppercase().split(Regex("[\\s,;|]+")).filter { it.isNotBlank() }
        val candidatas = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                regexRotaOtimizada.matches(token) -> candidatas.add(token)
                regexRotaColada.matches(token) -> {
                    val m = regexRotaColada.find(token)!!
                    candidatas.add("${m.groupValues[1]}_${m.groupValues[2]}")
                }
                i + 1 < tokens.size &&
                    regexParteASozinha.matches(token) &&
                    regexParteBSozinha.matches(tokens[i + 1]) -> {
                    candidatas.add("${token}_${tokens[i + 1]}")
                    i++
                }
            }
            i++
        }
        return candidatas.distinct()
    }
    // Rota original: prefixo _ DÍGITOS — ex.: AM1_237, AM1_2 (para ser pulada na leitura da doca)
    private val regexRotaOriginal = Regex("^[A-Z]{1,4}\\d{0,3}_\\d{1,4}$")
    private val regexOnda = Regex("^ONDA\\s*(\\d+)$")
    private val regexOndaInline = Regex("ONDA\\s*(\\d+)")
    private val regexDoca = Regex("^\\d{1,2}$")

    /**
     * Um pedaço de texto reconhecido pelo OCR com sua posição na imagem.
     */
    data class ElementoOcr(val texto: String, val x: Int, val y: Int, val altura: Int)

    /**
     * Parse POSICIONAL (preferencial): usa os elementos do ML Kit com bounding
     * box — cada célula da tabela é um elemento, então reconstruímos as linhas
     * agrupando por coordenada Y e ordenando por X. Muito mais robusto que
     * dividir o texto por linhas, porque o OCR nem sempre junta as colunas
     * com espaços.
     */
    fun parseTabelaOndasElementos(elementos: List<ElementoOcr>): List<LinhaImportada> {
        if (elementos.isEmpty()) return emptyList()

        // Agrupa em linhas visuais por centro-Y (tolerância = meia altura do elemento)
        val ordenadosPorY = elementos.sortedBy { it.y }
        val linhasVisuais = mutableListOf<MutableList<ElementoOcr>>()
        var yAtual: Int? = null
        for (el in ordenadosPorY) {
            val centro = el.y + el.altura / 2
            val linhaAtual = linhasVisuais.lastOrNull()
            val yReferencia = yAtual?.let { it }
            if (linhaAtual != null && yReferencia != null &&
                Math.abs(centro - yReferencia) <= (el.altura / 2 + 10)
            ) {
                linhaAtual.add(el)
                yAtual = ((yReferencia + centro) / 2)
            } else {
                linhasVisuais.add(mutableListOf(el))
                yAtual = centro
            }
        }

        val resultado = mutableListOf<LinhaImportada>()
        val vistos = mutableSetOf<Pair<Int?, String>>()

        linhasVisuais.forEach { elementosDaLinha ->
            val tokens = elementosDaLinha.sortedBy { it.x }.map { it.texto.trim().uppercase() }
            val linha = parseTokens(tokens)
            println("🔍 OndaTableParser: tokens=$tokens → $linha")
            linha?.let {
                val chave = it.onda to it.rota
                if (vistos.add(chave)) resultado.add(it)
            }
        }
        println("🔍 OndaTableParser: resultado=${resultado.size} linhas")

        return resultado
    }

    /**
     * Parse por linha de TEXTO (fallback para tabela colada como texto).
     */
    fun parseTabelaOndasTexto(texto: String): List<LinhaImportada> {
        val resultado = mutableListOf<LinhaImportada>()
        val vistos = mutableSetOf<Pair<Int?, String>>()

        texto.lines().forEach { linhaBruta ->
            parseTokens(linhaBruta.trim().uppercase().split(Regex("\\s+")).filter { it.isNotBlank() })
                ?.let { linha ->
                    val chave = linha.onda to linha.rota
                    if (vistos.add(chave)) resultado.add(linha)
                }
        }
        return resultado
    }


    // Parte A da rota otimizada: letras + dígitos (I13, G3, VA4, F2...)
    private val regexParteA = Regex("^[A-Z]{1,4}\\d{1,3}$")
    // Parte B da rota otimizada: letras (+dígitos opcionais) (AM1, PM, SD...)
    private val regexParteB = Regex("^[A-Z]{1,4}\\d{0,3}$")
    // Número de 2-4 dígitos (sufixo da rota original: 237, 16, 264)
    private val regexNumero3 = Regex("^\\d{2,4}$")

    private fun parseTokens(tokens: List<String>): LinhaImportada? {
        // Onda: "ONDA1" colado OU "ONDA" e o número em elementos separados
        var onda: Int? = null
        var idxInicio = 0
        val idxTokenOnda = tokens.indexOfFirst { regexOndaInline.containsMatchIn(it) }
        if (idxTokenOnda >= 0) {
            onda = regexOndaInline.find(tokens[idxTokenOnda])?.groupValues?.get(1)?.toIntOrNull()
            idxInicio = idxTokenOnda + 1
        } else {
            val idxOnda = tokens.indexOfFirst { it.equals("ONDA", ignoreCase = true) }
            if (idxOnda >= 0) {
                val numeroSeparado = tokens.getOrNull(idxOnda + 1)?.toIntOrNull()
                if (numeroSeparado != null && numeroSeparado in 1..99) {
                    onda = numeroSeparado
                    idxInicio = idxOnda + 2
                } else {
                    idxInicio = idxOnda + 1
                }
            }
        }
        val restantes = tokens.drop(idxInicio)

        // Rota otimizada = o ÚLTIMO candidato da linha (na planilha grande a QR
        // ROTA vem depois do turno solto e da rota original; pares falsos como
        // AM1+AM1 aparecem antes e são descartados)
        var rota: String? = null
        var turnoLido: String? = null
        var posAposRota = -1
        var j = 0
        while (j < restantes.size) {
            val t = restantes[j]
            if (regexRotaOtimizada.matches(t)) {
                rota = t
                turnoLido = turnoDoSufixo(t)
                posAposRota = j + 1
                j += 1
                continue
            }
            if (j + 1 < restantes.size &&
                regexParteA.matches(t) &&
                regexParteB.matches(restantes[j + 1])
            ) {
                rota = "${t}_${restantes[j + 1]}"
                turnoLido = turnoDoSufixo(rota!!)
                posAposRota = j + 2
                j += 2
                continue
            }
            j++
        }
        if (rota == null) return null

        // Doca: primeiro número de 1-2 dígitos APÓS a rota otimizada,
        // pulando a rota original (par [letras+dígitos][número] ou "AM1_237")
        var doca: Int? = null
        var k = posAposRota
        if (k + 1 < restantes.size &&
            regexParteA.matches(restantes[k]) &&
            regexNumero3.matches(restantes[k + 1])
        ) {
            k += 2
        } else if (k < restantes.size && regexRotaOriginal.matches(restantes[k])) {
            k += 1
        }
        while (k < restantes.size) {
            if (regexDoca.matches(restantes[k])) {
                doca = restantes[k].toIntOrNull()
                break
            }
            k++
        }

        return LinhaImportada(onda = onda, rota = rota, doca = doca, turno = turnoLido)
    }

    /**
     * Turno lido do sufixo da rota otimizada: "I13_AM1" → "AM1".
     * Só considera sufixo com letras (rota original "AM1_237" não tem turno).
     */
    private fun turnoDoSufixo(rota: String): String? {
        val sufixo = rota.substringAfter('_', "").takeIf { it.any(Char::isLetter) }
        return sufixo?.uppercase()
    }

    /**
     * Casa a rota da tabela (código completo, ex.: "H4_AM1") com a rota já
     * atribuída a um motorista na pré-escala ("H4_AM1" ou só o prefixo "H4"):
     * igualdade exata ou mesmo prefixo antes do underline.
     */
    fun rotasCasa(rotaTabela: String, rotaAtribuida: String): Boolean {
        val tabela = rotaTabela.trim().uppercase()
        val atribuida = rotaAtribuida.trim().uppercase()
        return tabela == atribuida || tabela.substringBefore('_') == atribuida
    }
}
