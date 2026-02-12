package com.controleescalas.app.data.models

/**
 * Modelo de dados para Devolução
 */
data class Devolucao(
    val id: String = "",
    val idsPacotes: List<String> = emptyList(), // Lista de IDs de pacotes
    val motoristaId: String = "",
    val motoristaNome: String = "",
    val baseId: String = "",
    val data: String = "",
    val hora: String = "",
    val timestamp: Long = 0L
) {
    // Propriedade de compatibilidade para manter compatibilidade com código antigo
    val idPacote: String
        get() = idsPacotes.firstOrNull() ?: ""
    
    // Quantidade de pacotes
    val quantidade: Int
        get() = idsPacotes.size
}

