
package com.controleescalas.app.data.models

/**
 * MODELOS DE DADOS (VISÃO FIRESTORE)
 *
 * Estes data classes representam como salvaremos no Firestore.
 * Eles servem pra deixar bem claro os campos usados no app.
 *
 * IMPORTANTE:
 * - Estes modelos são rascunho. Em produção, usar @Keep / @PropertyName
 *   se necessário, e lidar com nullables porque Firestore pode não ter todos os campos.
 */

// ----------------------
// BASE
// ----------------------
data class BaseInfo(
    val nomeTransportadora: String = "",
    val nomeBase: String = "",
    val corTema: String = "#16A34A",
    val statusAprovacao: String = "pendente", // "pendente" | "ativa" | "rejeitada"
    val localizacao: LocalizacaoInfo = LocalizacaoInfo()
)

data class LocalizacaoInfo(
    val galpao: GeofenceInfo = GeofenceInfo(),
    val estacionamento: GeofenceInfo? = GeofenceInfo()
)

data class GeofenceInfo(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val raioM: Int = 100,
    val ativo: Boolean = true
)

// ----------------------
// USUÁRIO
// ----------------------
data class UsuarioInfo(
    val nome: String = "",
    val telefone: String = "",
    val pin4: String = "",          // futuro: armazenar hash
    val papel: String = "motorista", // "motorista" | "ajudante" | "admin"
    val ativo: Boolean = true,
    val superadmin: Boolean = false,
    val baseId: String = ""
)

// ----------------------
// ESCALA DO DIA
// ----------------------
data class EscalaTurnoInfo(
    val ondas: List<OndaInfo> = emptyList(),
    val dataDoDia: String = "" // "2025-10-27"
)

data class OndaInfo(
    val ordem: Int = 1,                   // 1 = PRIMEIRA ONDA
    val nomeOnda: String = "PRIMEIRA ONDA",
    val horarioPlanejado: String = "06:00",
    val itens: List<OndaItemInfo> = emptyList()
)

data class OndaItemInfo(
    val motoristaId: String = "",
    val nomeMotorista: String = "",
    val vagaPlanejada: String = "",
    val rotaCodigo: String = "",
    val pdfStoragePath: String = "", // ex: "bases/<baseId>/motoristas/<id>/2025-10-27.pdf"
    val dataDoDia: String = ""       // ex: "2025-10-27"
)

// ----------------------
// STATUS AO VIVO DO MOTORISTA
// ----------------------
data class StatusMotoristaInfo(
    val estado: String = "A_CAMINHO", // "A_CAMINHO" | "CHEGUEI" | "ESTACIONAMENTO" | "CARREGANDO" | "CONCLUIDO"
    val vagaAtualChamado: String = "",
    val rotaAtualChamado: String = "",
    val marcadoParaConclusao: Boolean = false,
    val passouEstacionamentoDepoisDeCarregar: Boolean = false,
    val ultimaAtualizacao: Long = 0L
)

/**
 * LÓGICA DE STATUS (resumo)
 *
 * 1. A_CAMINHO  -> CHEGUEI (GPS dentro do raio do galpão)
 * 2. CHEGUEI    -> ESTACIONAMENTO (GPS dentro do raio do estacionamento) [opcional]
 * 3. (de CHEGUEI ou ESTACIONAMENTO)
 *    Admin toca "Chamar / Carregando":
 *      - estado = "CARREGANDO"
 *      - vagaAtualChamado = "02" (editável na hora)
 *      - rotaAtualChamado = "M12"
 * 4. Depois que ele terminou e saiu do galpão:
 *    -> CONCLUIDO
 *    (automático com GPS se ele passou pelo estacionamento e saiu da área,
 *     ou manualmente pelo admin)
 */
