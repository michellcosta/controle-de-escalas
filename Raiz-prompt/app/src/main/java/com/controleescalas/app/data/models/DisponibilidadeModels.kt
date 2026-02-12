package com.controleescalas.app.data.models

import com.google.firebase.firestore.DocumentId

/**
 * Modelos de dados para o sistema de disponibilidade
 * 
 * Fluxo:
 * 1. Admin solicita disponibilidade para o dia seguinte
 * 2. Motoristas marcam disponível/indisponível
 * 3. Admin visualiza lista e cria escala com disponíveis
 */

/**
 * Representa uma solicitação de disponibilidade para uma data específica
 */
data class Disponibilidade(
    @DocumentId val id: String = "",
    val baseId: String = "",
    val data: String = "", // YYYY-MM-DD (data para qual está solicitando disponibilidade)
    val motoristas: List<DisponibilidadeMotorista> = emptyList(),
    val notificacaoEnviada: Boolean = false,
    val criadoEm: Long = System.currentTimeMillis(),
    val criadoPor: String = "" // ID do admin que solicitou
)

/**
 * Representa a resposta de um motorista sobre sua disponibilidade
 */
data class DisponibilidadeMotorista(
    val motoristaId: String = "",
    val nome: String = "", // Desnormalizado para facilitar exibição
    val telefone: String = "", // Desnormalizado
    val disponivel: Boolean? = null, // null = não respondeu, true = disponível, false = indisponível
    val respondidoEm: Long? = null
)

/**
 * DTO para facilitar a exibição na UI
 */
data class DisponibilidadeStatus(
    val data: String,
    val totalMotoristas: Int,
    val disponiveis: Int,
    val indisponiveis: Int,
    val semResposta: Int
) {
    val percentualResposta: Float
        get() = if (totalMotoristas > 0) {
            ((disponiveis + indisponiveis).toFloat() / totalMotoristas) * 100
        } else 0f
}
