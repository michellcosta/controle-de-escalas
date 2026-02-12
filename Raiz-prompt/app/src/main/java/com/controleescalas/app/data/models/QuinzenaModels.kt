package com.controleescalas.app.data.models

/**
 * Representa os dados de quinzena de um motorista
 */
data class QuinzenaMotorista(
    val motoristaId: String = "",
    val motoristaNome: String = "",
    val baseId: String = "",
    val mes: Int = 0,  // 1-12
    val ano: Int = 0,  // ex: 2024
    val primeiraQuinzena: QuinzenaDetalhes = QuinzenaDetalhes(),
    val segundaQuinzena: QuinzenaDetalhes = QuinzenaDetalhes(),
    val atualizadoEm: Long = 0L
)

/**
 * Detalhes de uma quinzena específica (1-15 ou 16-fim do mês)
 */
data class QuinzenaDetalhes(
    val diasTrabalhados: Int = 0,
    val datas: List<String> = emptyList()  // Lista de datas trabalhadas (formato: "dd/MM/yyyy")
)

/**
 * DTO para exibir resumo de quinzena
 */
data class QuinzenaResumo(
    val motoristaId: String,
    val motoristaNome: String,
    val primeiraQuinzenaDias: Int,
    val segundaQuinzenaDias: Int,
    val totalDias: Int
)
