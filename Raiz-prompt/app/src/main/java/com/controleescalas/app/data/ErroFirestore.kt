package com.controleescalas.app.data

/**
 * Classificador de erros do Firestore para workers e serviços em background.
 *
 * Regra da casa (medido no incidente de 19/11/2026): operação NEGADA por
 * security rules ou cota estourada CONTINUA consumindo a cota diária do
 * projeto. Worker que faz Result.retry() sobre erro de permissão/cota vira
 * tempestade de ops negadas a cada 30s, em TODOS os aparelhos — para esses
 * erros a única atitude correta é PARAR (Result.failure()), nunca retry.
 */
object ErroFirestore {

    fun cotaOuPermissaoNegada(e: Throwable?): Boolean {
        if (e == null) return false
        val mensagem = e.message.orEmpty()
        return mensagem.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            mensagem.contains("Quota exceeded", ignoreCase = true) ||
            mensagem.contains("PERMISSION_DENIED", ignoreCase = true) ||
            mensagem.contains("Missing or insufficient permissions", ignoreCase = true) ||
            mensagem.contains("UNAUTHENTICATED", ignoreCase = true)
    }
}
