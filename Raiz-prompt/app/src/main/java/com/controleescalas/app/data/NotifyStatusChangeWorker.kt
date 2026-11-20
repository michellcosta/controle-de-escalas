package com.controleescalas.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker para notificar mudança de status via backend Python.
 * Envia push ao motorista e aos admins (quando CHEGUEI ou CONCLUIDO).
 * Substitui Cloud Function onMotoristaStatusChanged.
 */
class NotifyStatusChangeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotifyStatusChangeWorker"
        const val KEY_BASE_ID = "base_id"
        const val KEY_MOTORISTA_ID = "motorista_id"
        const val KEY_STATUS = "status"
        const val KEY_MOTORISTA_NOME = "motorista_nome"
        const val WORK_NAME = "notify_status_change"

        /**
         * Enfileira notificação de mudança de status.
         * Usa WORK_NAME único por base+motorista para evitar duplicatas.
         */
        fun enqueue(
            context: Context,
            baseId: String,
            motoristaId: String,
            status: String,
            motoristaNome: String = ""
        ) {
            val inputData = workDataOf(
                KEY_BASE_ID to baseId,
                KEY_MOTORISTA_ID to motoristaId,
                KEY_STATUS to status,
                KEY_MOTORISTA_NOME to motoristaNome
            )
            val request = OneTimeWorkRequestBuilder<NotifyStatusChangeWorker>()
                .setInputData(inputData)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.LINEAR,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("notify_status")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_${baseId}_${motoristaId}_$status",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "📤 Work enfileirado: status $status para motorista $motoristaId")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val baseId = inputData.getString(KEY_BASE_ID)
            val motoristaId = inputData.getString(KEY_MOTORISTA_ID)
            val status = inputData.getString(KEY_STATUS)
            val motoristaNome = inputData.getString(KEY_MOTORISTA_NOME) ?: ""

            if (baseId.isNullOrBlank() || motoristaId.isNullOrBlank() || status.isNullOrBlank()) {
                Log.e(TAG, "❌ Dados inválidos: baseId=$baseId, motoristaId=$motoristaId, status=$status")
                return@withContext Result.failure()
            }

            val service = NotificationApiService()
            val (success, error) = service.notifyStatusChange(
                baseId = baseId,
                motoristaId = motoristaId,
                status = status,
                motoristaNome = motoristaNome
            )

            if (success) {
                Log.d(TAG, "✅ Notificação de status $status enviada para $motoristaId")
                Result.success()
            } else {
                Log.e(TAG, "❌ Falha ao enviar: $error")
                // Achado 14 da auditoria: retry sem teto (HTTP fora por horas =
                // worker martelando para sempre). Teto de 5 tentativas.
                if (runAttemptCount >= 5) Result.failure() else Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no Worker: ${e.message}", e)
            if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }
    }
}
