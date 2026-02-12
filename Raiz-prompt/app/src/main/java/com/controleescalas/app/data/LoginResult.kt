package com.controleescalas.app.data

/**
 * Resultado do processo de login
 */
sealed class LoginResult {
    data class Success(
        val motoristaId: String,
        val baseId: String,
        val baseName: String = "",  // Nome da transportadora (opcional por compatibilidade)
        val papel: String,
        val nome: String
    ) : LoginResult()
    
    data class Error(
        val message: String
    ) : LoginResult()
}



