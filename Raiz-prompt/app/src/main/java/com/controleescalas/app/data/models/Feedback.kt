package com.controleescalas.app.data.models

/**
 * Modelo de feedback enviado por admins para super admin
 */
data class Feedback(
    val id: String = "",
    val baseId: String = "",
    val adminId: String = "",
    val adminNome: String = "",
    val baseNome: String = "",
    val mensagem: String = "",
    val data: Long = System.currentTimeMillis(),
    val status: FeedbackStatus = FeedbackStatus.NOVO,
    val curtidoPor: String? = null,
    val curtidoEm: Long? = null
)

enum class FeedbackStatus {
    NOVO,      // 🆕 Não lido
    LIDO,      // 👁️ Lido mas não curtido
    CURTIDO    // 👍 Curtido
}

