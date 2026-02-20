package com.controleescalas.app.data.models

import com.google.firebase.firestore.DocumentId

/**
 * Data classes para Firebase Firestore
 * 
 * Estrutura de dados otimizada para o app Controle de Escalas
 */

data class Base(
    @DocumentId val id: String = "",
    val nome: String = "",
    val transportadora: String = "",
    val corTema: String = "#16A34A",
    val coordenadas: Coordenadas = Coordenadas(),
    val criadoEm: Long = System.currentTimeMillis(),
    val ativo: Boolean = true,
    val statusAprovacao: String = "pendente", // "pendente" | "ativa" | "rejeitada"
    val aprovadoPor: String? = null, // ID do super admin que aprovou
    val aprovadoEm: Long? = null, // Timestamp da aprovação
    // ✅ NOVO: Campos de monetização
    val plano: String? = null, // "gratuito", "premium", "trial"
    val dataInicioTrial: Long? = null, // Timestamp do início do trial
    val dataFimTrial: Long? = null // Timestamp do fim do trial
) {
    /**
     * Verifica se a base está em período de trial
     */
    fun estaEmTrial(): Boolean {
        if (plano != "trial") return false
        val agora = System.currentTimeMillis()
        val inicio = dataInicioTrial ?: return false
        val fim = dataFimTrial ?: return false
        return agora >= inicio && agora <= fim
    }

    /**
     * Verifica se o trial expirou (era trial e passou da data)
     */
    fun trialExpirado(): Boolean {
        if (plano != "trial") return false
        val fim = dataFimTrial ?: return false
        return System.currentTimeMillis() > fim
    }

    /**
     * Dias restantes do trial (null se não estiver em trial)
     */
    fun diasRestantesTrial(): Int? {
        if (plano != "trial" || dataFimTrial == null) return null
        val restante = (dataFimTrial!! - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
        return maxOf(0, restante.toInt())
    }
}

data class Coordenadas(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val raio: Int = 100
)

data class Motorista(
    @DocumentId val id: String = "",
    val nome: String = "",
    val telefone: String = "",
    val pinHash: String = "", // Hash do PIN (bcrypt)
    val papel: String = "motorista", // motorista, admin, auxiliar, superadmin
    val modalidade: String = "FROTA", // FROTA, PASSEIO, DEDICADO, UTILITARIO
    val baseId: String = "",
    val ativo: Boolean = true,
    val fcmToken: String? = null, // Firebase Cloud Messaging token para push notifications
    val criadoPor: String? = null, // ID do admin/auxiliar que criou
    val criadoEm: Long = System.currentTimeMillis()
)

data class Escala(
    @DocumentId val id: String = "",
    val baseId: String = "",
    val data: String = "", // YYYY-MM-DD
    val turno: String = "", // AM, PM
    val ondas: List<Onda> = emptyList(),
    val criadoEm: Long = System.currentTimeMillis()
)

data class Onda(
    val nome: String = "",
    val horario: String = "", // Horário de referência da onda
    val tipo: String = "NORMAL", // NORMAL ou DEDICADO
    val itens: List<OndaItem> = emptyList()
)

data class OndaItem(
    val motoristaId: String = "",
    val nome: String = "", // Desnormalizado para facilitar exibição
    val vaga: String = "",
    val rota: String = "",
    val horario: String = "", // Horário específico do motorista
    val hasPdf: Boolean = false,
    val sacas: Int? = null, // Quantidade de sacas (ex: 1, 2, 3, 9...)
    val modalidade: String = "FROTA" // FROTA, PASSEIO, DEDICADO, UTILITARIO
)

data class StatusMotorista(
    @DocumentId val id: String = "",
    val motoristaId: String = "",
    val baseId: String = "",
    val estado: String = "A_CAMINHO", // A_CAMINHO, CHEGUEI, PROXIMO, IR_ESTACIONAMENTO, ESTACIONAMENTO, CARREGANDO, CONCLUIDO
    val mensagem: String = "",
    val vagaAtual: String? = null,
    val rotaAtual: String? = null,
    val inicioCarregamento: Long? = null, // Timestamp de quando começou o carregamento
    val fimCarregamento: Long? = null, // Timestamp de quando terminou o carregamento
    val confirmadoEm: Long? = null, // Timestamp de quando o motorista confirmou que entendeu a chamada
    val atualizadoEm: Long = System.currentTimeMillis()
)

data class ConfiguracaoBase(
    @DocumentId val id: String = "",
    val baseId: String = "",
    val galpao: GeofenceConfig = GeofenceConfig(),
    val estacionamento: GeofenceConfig = GeofenceConfig()
)

data class GeofenceConfig(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val raio: Int = 100,
    val ativo: Boolean = true
)

data class MotoristaLocation(
    @DocumentId val id: String = "",
    val motoristaId: String = "",
    val nome: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val distanceToGalpao: Double = 0.0, // metros
    val distanceToParking: Double = 0.0, // metros
    val onda: String = "",
    val data: String = "",
    val atualizadoEm: Long = System.currentTimeMillis()
)

data class AdminMotoristaCardData(
    val id: String = "",
    val nome: String,
    val telefone: String,
    val statusAtual: String,
    val turno: String,
    val nomeOnda: String,
    val horarioOnda: String,
    val vagaPlanejada: String,
    val rotaPlanejada: String,
    val hasPdf: Boolean,
    val papel: String = "motorista",
    val modalidade: String = "FROTA"
)

/**
 * Data classes para conversão com UI
 */

// Converter Motorista para AdminMotoristaCardData
fun Motorista.toAdminMotoristaCardData(
    statusAtual: String = "A CAMINHO",
    turno: String = "AM",
    nomeOnda: String = "",
    horarioOnda: String = "",
    vagaPlanejada: String = "",
    rotaPlanejada: String = "",
    hasPdf: Boolean = false
) = AdminMotoristaCardData(
    id = this.id,
    nome = this.nome,
    telefone = this.telefone,
    statusAtual = statusAtual,
    turno = turno,
    nomeOnda = nomeOnda,
    horarioOnda = horarioOnda,
    vagaPlanejada = vagaPlanejada,
    rotaPlanejada = rotaPlanejada,
    hasPdf = hasPdf,
    papel = this.papel,
    modalidade = this.modalidade
)

// Converter Escala para DriverEscalaInfo
fun Escala.toDriverEscalaInfo(motoristaId: String): com.controleescalas.app.ui.screens.DriverEscalaInfo? {
    val motoristaIdTrimmed = motoristaId.trim()
    println("🔍 Escala.toDriverEscalaInfo: Procurando motoristaId='$motoristaIdTrimmed' na escala com ${ondas.size} ondas (turno: ${this.turno})")
    
    val ondaEncontrada = ondas.firstOrNull { onda ->
        val found = onda.itens.any { item ->
            val itemIdTrimmed = item.motoristaId.trim()
            val matches = itemIdTrimmed == motoristaIdTrimmed
            if (!matches && item.motoristaId.isNotEmpty()) {
                println("    🔄 Comparando: '$itemIdTrimmed' == '$motoristaIdTrimmed' = $matches")
            }
            matches
        }
        if (found) {
            println("  ✅ Motorista encontrado na onda: ${onda.nome}")
        }
        found
    }
    
    if (ondaEncontrada == null) {
        println("  ⚠️ Motorista '$motoristaIdTrimmed' não encontrado em nenhuma onda")
        // Listar todos os IDs disponíveis para debug
        ondas.forEachIndexed { index, onda ->
            println("    📋 Onda $index (${onda.nome}):")
            onda.itens.forEach { item ->
                println("      - '${item.motoristaId.trim()}' (${item.nome})")
            }
        }
        return null
    }
    
    val item = ondaEncontrada.itens.first { it.motoristaId.trim() == motoristaIdTrimmed }
    println("  ✅ Item encontrado: vaga=${item.vaga}, rota=${item.rota}, sacas=${item.sacas}")
    
    return com.controleescalas.app.ui.screens.DriverEscalaInfo(
        turno = this.turno,
        nomeOnda = ondaEncontrada.nome,
        horarioPlanejado = ondaEncontrada.horario,
        vagaPlanejada = item.vaga,
        rotaCodigo = item.rota,
        hasPdf = item.hasPdf,
        sacas = item.sacas
    )
}

// Converter StatusMotorista para DriverStatusInfo
fun StatusMotorista.toDriverStatusInfo() = com.controleescalas.app.ui.screens.DriverStatusInfo(
    estado = this.estado,
    mensagem = this.mensagem,
    vagaAtual = this.vagaAtual,
    rotaAtual = this.rotaAtual,
    confirmadoEm = this.confirmadoEm
)

/**
 * ========================================
 * FUNÇÕES DE ORDENAÇÃO INTELIGENTE
 * ========================================
 */

/**
 * Prioridade de modalidade para ordenação
 * FROTA (1) -> UTILITÁRIO (2) -> PASSEIO (3) -> DEDICADO (4)
 */
fun getModalidadePrioridade(modalidade: String): Int {
    return when (modalidade) {
        "FROTA" -> 1
        "UTILITARIO" -> 2
        "PASSEIO" -> 3
        "DEDICADO" -> 4
        else -> 5
    }
}

/**
 * Ordenar itens de uma onda por vaga (01, 02, 03...)
 */
fun List<OndaItem>.sortedByModalidade(): List<OndaItem> {
    return this.sortedWith(compareBy(
        { it.vaga.toIntOrNull() ?: Int.MAX_VALUE } // Ordenar numericamente por vaga (01, 02, 03...)
    ))
}

/**
 * Ordenar ondas: ondas NORMAIS primeiro, depois DEDICADO
 */
fun List<Onda>.sortedByTipo(): List<Onda> {
    return this.sortedWith(compareBy(
        { if (it.tipo == "DEDICADO") 1 else 0 }, // DEDICADO vai para o final
        { it.horario } // Ordenação secundária por horário
    ))
}

/**
 * Organizar escala completa de forma inteligente
 * - Ordena ondas (NORMAL antes de DEDICADO)
 * - Ordena motoristas dentro de cada onda (FROTA -> UTILITARIO -> PASSEIO -> DEDICADO)
 */
fun Escala.organizado(): Escala {
    val ondasOrganizadas = this.ondas
        .sortedByTipo()
        .map { onda ->
            onda.copy(itens = onda.itens.sortedByModalidade())
        }
    
    return this.copy(ondas = ondasOrganizadas)
}

/**
 * Separar ondas normais e dedicadas
 */
fun List<Onda>.separarPorTipo(): Pair<List<Onda>, List<Onda>> {
    val normais = this.filter { it.tipo != "DEDICADO" }.sortedBy { it.horario }
    val dedicadas = this.filter { it.tipo == "DEDICADO" }.sortedBy { it.horario }
    return Pair(normais, dedicadas)
}

