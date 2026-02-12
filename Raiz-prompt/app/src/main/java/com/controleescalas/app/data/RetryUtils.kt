package com.controleescalas.app.data

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Utilitário para retry com backoff exponencial
 * 
 * Útil para operações de escrita que podem falhar temporariamente
 * NÃO usar para listeners (eles já reconectam automaticamente)
 */
object RetryUtils {
    private const val TAG = "RetryUtils"
    
    /**
     * Executar operação com retry e backoff exponencial
     * 
     * @param maxRetries Número máximo de tentativas (padrão: 3)
     * @param initialDelayMs Delay inicial em milissegundos (padrão: 1000ms)
     * @param maxDelayMs Delay máximo em milissegundos (padrão: 10000ms)
     * @param operation Operação a ser executada
     * @return Resultado da operação
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        operation: suspend () -> T
    ): T {
        var delay = initialDelayMs
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "🔄 Tentativa ${attempt + 1}/$maxRetries")
                return operation()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "❌ Tentativa ${attempt + 1}/$maxRetries falhou: ${e.message}")
                
                // Se não for a última tentativa, esperar antes de tentar novamente
                if (attempt < maxRetries - 1) {
                    Log.d(TAG, "⏳ Aguardando ${delay}ms antes da próxima tentativa...")
                    delay(delay)
                    // Backoff exponencial: dobra o delay a cada tentativa
                    delay = (delay * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
        
        // Se todas as tentativas falharam, lançar a última exceção
        Log.e(TAG, "❌ Todas as $maxRetries tentativas falharam")
        throw lastException ?: Exception("Operação falhou após $maxRetries tentativas")
    }
    
    /**
     * Executar operação com retry apenas se for erro de rede
     */
    suspend fun <T> retryOnNetworkError(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        operation: suspend () -> T
    ): T {
        return retryWithBackoff(maxRetries, initialDelayMs) {
            try {
                operation()
            } catch (e: Exception) {
                // Verificar se é erro de rede
                if (isNetworkError(e)) {
                    throw e // Relançar para que o retry funcione
                } else {
                    // Se não for erro de rede, não fazer retry
                    throw e
                }
            }
        }
    }
    
    /**
     * Verificar se exceção é relacionada a rede
     */
    private fun isNetworkError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("network") ||
               message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("unavailable") ||
               e is java.net.SocketTimeoutException ||
               e is java.net.UnknownHostException ||
               e is java.io.IOException
    }
}

