package com.controleescalas.app.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.controleescalas.app.data.repositories.EscalaRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker para resetar dados diariamente à meia-noite
 * - Limpa todas as ondas do dia anterior
 * - Reseta status de todos os motoristas para A_CAMINHO
 */
class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "DailyResetWorker"
        const val WORK_NAME = "daily_reset_work"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🕛 DailyResetWorker: Iniciando reset diário à meia-noite")
            println("🕛 DailyResetWorker: Iniciando reset diário")
            
            val escalaRepository = EscalaRepository()
            val motoristaRepository = MotoristaRepository()
            
            // 1. Limpar todas as ondas do dia anterior
            Log.d(TAG, "🧹 Limpando ondas do dia anterior...")
            println("🧹 DailyResetWorker: Limpando ondas do dia anterior")
            val ondasLimpas = escalaRepository.limparOndasDeTodasBases()
            
            if (ondasLimpas) {
                Log.d(TAG, "✅ Ondas limpas com sucesso")
                println("✅ DailyResetWorker: Ondas limpas com sucesso")
            } else {
                Log.w(TAG, "⚠️ Erro ao limpar ondas")
                println("⚠️ DailyResetWorker: Erro ao limpar ondas")
            }
            
            // 2. Resetar status de todos os motoristas
            Log.d(TAG, "🔄 Resetando status de todos os motoristas...")
            println("🔄 DailyResetWorker: Resetando status de todos os motoristas")
            val statusResetados = motoristaRepository.resetarTodosStatusDeTodasBases()
            
            if (statusResetados) {
                Log.d(TAG, "✅ Status resetados com sucesso")
                println("✅ DailyResetWorker: Status resetados com sucesso")
            } else {
                Log.w(TAG, "⚠️ Erro ao resetar status")
                println("⚠️ DailyResetWorker: Erro ao resetar status")
            }
            
            if (ondasLimpas && statusResetados) {
                Log.d(TAG, "✅ DailyResetWorker: Reset diário concluído com sucesso")
                println("✅ DailyResetWorker: Reset diário concluído com sucesso")
            } else {
                Log.w(TAG, "⚠️ DailyResetWorker: Reset diário concluído com alguns erros")
                println("⚠️ DailyResetWorker: Reset diário concluído com alguns erros")
            }
            
            // Re-agendar para a próxima meia-noite
            reagendarProximaMeiaNoite()
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao executar reset diário: ${e.message}", e)
            println("❌ DailyResetWorker: Erro - ${e.message}")
            e.printStackTrace()
            Result.retry() // Tentar novamente em caso de erro
        }
    }
    
    /**
     * Re-agenda o Worker para a próxima meia-noite
     */
    private fun reagendarProximaMeiaNoite() {
        try {
            val calendar = Calendar.getInstance()
            val agora = Calendar.getInstance()
            
            // Definir para meia-noite de amanhã
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            
            val delayMillis = calendar.timeInMillis - agora.timeInMillis
            val delayHours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(delayMillis)
            
            Log.d(TAG, "🕛 Re-agendando reset para próxima meia-noite (em $delayHours horas)")
            println("🕛 DailyResetWorker: Re-agendando para próxima meia-noite (em $delayHours horas)")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val nextWork = OneTimeWorkRequestBuilder<DailyResetWorker>()
                .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(applicationContext).enqueue(nextWork)
            
            Log.d(TAG, "✅ Reset re-agendado com sucesso")
            println("✅ DailyResetWorker: Reset re-agendado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao re-agendar reset: ${e.message}", e)
            println("❌ DailyResetWorker: Erro ao re-agendar - ${e.message}")
        }
    }
}

