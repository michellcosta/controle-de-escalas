package com.controleescalas.app.data.models

import com.google.firebase.firestore.DocumentId

/**
 * Modelo de configuração do sistema de monetização
 */
data class SistemaConfig(
    val monetizacaoAtiva: Boolean = false,
    val modoAtivacao: ModoAtivacao? = null, // MANUAL, AUTOMATICA, null
    val dataAtivacaoAutomatica: Long? = null, // Timestamp
    val dataAtivacaoManual: Long? = null,
    val ativadoPor: String? = null,
    val ultimaModificacao: Long = System.currentTimeMillis(),
    /** Exibir botão "Planos e Assinatura" e banner de trial expirado. Ative quando configurar produtos no Play Console. */
    val planosHabilitados: Boolean = false,
    /** Ocultar seletor de tema (claro/escuro) para usuários até que o modo claro esteja finalizado. */
    val temaHabilitado: Boolean = false,
    // ✅ NOVO: Configurações adicionais
    val periodoTrialDias: Int = 30,
    val limiteMotoristasGratuito: Int = 5,
    val precoPremiumMensal: Double = 249.90,
    val descontoMultiplasBases: Double = 0.10, // 10%
    val minimoBasesParaDesconto: Int = 5
)

enum class ModoAtivacao {
    MANUAL,
    AUTOMATICA
}

// ✅ NOVO: Modelo para estatísticas do dashboard
data class DashboardStats(
    val totalTransportadoras: Int = 0,
    val transportadorasAtivas: Int = 0,
    val transportadorasPendentes: Int = 0,
    val transportadorasRejeitadas: Int = 0,
    val totalMotoristas: Int = 0,
    val escalasHoje: Int = 0,
    val escalasEstaSemana: Int = 0,
    val receitaMensal: Double = 0.0,
    val basesPremium: Int = 0,
    val basesGratuitas: Int = 0,
    val basesEmTrial: Int = 0
)

// ✅ NOVO: Modelo para histórico de ações
data class AcaoHistorico(
    @DocumentId val id: String = "",
    val tipo: String = "", // "aprovacao", "rejeicao", "edicao", "configuracao", etc.
    val baseId: String? = null,
    val baseNome: String? = null,
    val superAdminId: String = "",
    val descricao: String = "",
    val data: Long = System.currentTimeMillis()
)

// ✅ NOVO: Modelo para plano da base
data class PlanoBase(
    val tipo: String = "gratuito", // "gratuito", "premium", "trial"
    val dataInicioTrial: Long? = null,
    val dataFimTrial: Long? = null,
    val dataAssinatura: Long? = null,
    val valorMensal: Double = 0.0,
    val descontoAplicado: Double = 0.0
)

