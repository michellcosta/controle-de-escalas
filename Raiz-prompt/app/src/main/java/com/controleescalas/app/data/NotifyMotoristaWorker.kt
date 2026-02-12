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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Worker para enviar notificação push via API Python.
 * Garante que a requisição complete mesmo se o usuário fechar o app imediatamente,
 * importante para Render cold start (até ~50s).
 */
class NotifyMotoristaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotifyMotoristaWorker"
        const val KEY_BASE_ID = "base_id"
        const val KEY_MOTORISTA_ID = "motorista_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_DATA_JSON = "data_json"
        const val WORK_NAME = "notify_motorista"

        /**
         * Enfileira trabalho para enviar notificação.
         * Usa WORK_NAME único para evitar duplicatas ao chamar rapidamente o mesmo motorista.
         */
        fun enqueue(
            context: Context,
            baseId: String,
            motoristaId: String,
            title: String,
            body: String,
            data: Map<String, String>? = null
        ) {
            val dataJson = data?.let { JSONObject(it).toString() } ?: "{}"
            val inputData = workDataOf(
                KEY_BASE_ID to baseId,
                KEY_MOTORISTA_ID to motoristaId,
                KEY_TITLE to title,
                KEY_BODY to body,
                KEY_DATA_JSON to dataJson
            )
            val request = OneTimeWorkRequestBuilder<NotifyMotoristaWorker>()
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
                .addTag("notify_push")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_${baseId}_$motoristaId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "📤 Work enfileirado: $title para motorista $motoristaId")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val baseId = inputData.getString(KEY_BASE_ID)
            val motoristaId = inputData.getString(KEY_MOTORISTA_ID)
            val title = inputData.getString(KEY_TITLE)
            val body = inputData.getString(KEY_BODY)
            val dataJson = inputData.getString(KEY_DATA_JSON)

            if (baseId.isNullOrBlank() || motoristaId.isNullOrBlank() || title.isNullOrBlank() || body.isNullOrBlank()) {
                Log.e(TAG, "❌ Dados inválidos: baseId=$baseId, motoristaId=$motoristaId")
                return@withContext Result.failure()
            }

            val dataMap = if (!dataJson.isNullOrBlank()) {
                val obj = JSONObject(dataJson)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } else null

            val service = NotificationApiService()
            val (success, error) = service.notifyMotorista(
                baseId = baseId,
                motoristaId = motoristaId,
                title = title,
                body = body,
                data = dataMap
            )

            if (success) {
                Log.d(TAG, "✅ Notificação enviada via Worker para $motoristaId")
                Result.success()
            } else {
                Log.e(TAG, "❌ Falha ao enviar: $error")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no Worker: ${e.message}", e)
            Result.retry()
        }
    }
}
