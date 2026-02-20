package com.controleescalas.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Serviço para chamar a API Python de notificações
 */
class NotificationApiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(NotificationApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NotificationApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NotificationApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    /**
     * Envia notificação push para um motorista específico via API Python
     * 
     * @param baseId ID da base
     * @param motoristaId ID do motorista
     * @param title Título da notificação
     * @param body Corpo da notificação
     * @param data Dados adicionais (opcional)
     * @return Pair<Boolean, String?> onde Boolean indica sucesso e String é mensagem de erro (se houver)
     */
    suspend fun notifyMotorista(
        baseId: String,
        motoristaId: String,
        title: String,
        body: String,
        data: Map<String, String>? = null
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val url = "${NotificationApiConfig.BASE_URL}${NotificationApiConfig.Endpoints.NOTIFY_MOTORISTA}"
            
            // Construir JSON body
            val jsonBody = JSONObject().apply {
                put("baseId", baseId)
                put("motoristaId", motoristaId)
                put("title", title)
                put("body", body)
                if (data != null) {
                    val dataJson = JSONObject()
                    data.forEach { (key, value) ->
                        dataJson.put(key, value)
                    }
                    put("data", dataJson)
                }
            }
            
            val requestBody = jsonBody.toString().toRequestBody(jsonMediaType)
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            Log.d(TAG, "📤 Enviando notificação via API Python: $url")
            Log.d(TAG, "📤 Body: ${jsonBody.toString()}")
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "📥 Resposta da API: ${response.code} - $responseBody")
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Notificação enviada com sucesso via API Python")
                Pair(true, null)
            } else {
                val errorMsg = "Erro ${response.code}: $responseBody"
                Log.e(TAG, "❌ Erro ao enviar notificação: $errorMsg")
                Pair(false, errorMsg)
            }
            
        } catch (e: Exception) {
            val errorMsg = "Erro ao chamar API Python: ${e.message}"
            Log.e(TAG, "❌ $errorMsg", e)
            Pair(false, errorMsg)
        }
    }
    
    /**
     * Solicita localização do motorista (admin/assistente).
     * Chama o backend Python em vez de Cloud Functions.
     */
    suspend fun requestDriverLocation(baseId: String, motoristaId: String, idToken: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val url = "${NotificationApiConfig.BASE_URL}${NotificationApiConfig.Endpoints.LOCATION_REQUEST}"
            val jsonBody = org.json.JSONObject().apply {
                put("baseId", baseId)
                put("motoristaId", motoristaId)
            }
            val requestBody = jsonBody.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $idToken")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            when {
                response.isSuccessful -> Pair(true, null)
                else -> Pair(false, "Erro ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Pair(false, "Erro: ${e.message}")
        }
    }

    /**
     * Envia coordenadas do motorista para o backend Python.
     */
    suspend fun receiveDriverLocation(baseId: String, motoristaId: String, lat: Double, lng: Double, idToken: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val url = "${NotificationApiConfig.BASE_URL}${NotificationApiConfig.Endpoints.LOCATION_RECEIVE}"
            val jsonBody = org.json.JSONObject().apply {
                put("baseId", baseId)
                put("motoristaId", motoristaId)
                put("lat", lat)
                put("lng", lng)
            }
            val requestBody = jsonBody.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $idToken")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            when {
                response.isSuccessful -> Pair(true, null)
                else -> Pair(false, "Erro ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Pair(false, "Erro: ${e.message}")
        }
    }

    /**
     * Chat com Assistente (Gemini) - texto e/ou imagem.
     * @return Pair(sucesso, textoResposta ou mensagem de erro)
     */
    suspend fun chatWithAssistente(
        baseId: String,
        text: String,
        base64Image: String?,
        idToken: String
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val url = "${NotificationApiConfig.BASE_URL}${NotificationApiConfig.Endpoints.ASSISTENTE_CHAT}"
            val jsonBody = JSONObject().apply {
                put("baseId", baseId)
                put("text", text)
                if (base64Image != null) put("imageBase64", base64Image)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $idToken")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            when {
                response.isSuccessful -> {
                    val json = body?.let { JSONObject(it) }
                    val textRes = json?.optString("text") ?: json?.optString("response") ?: body ?: ""
                    Pair(true, textRes)
                }
                else -> Pair(false, "Erro ${response.code}: $body")
            }
        } catch (e: Exception) {
            Pair(false, "Erro: ${e.message}")
        }
    }

    /**
     * Verifica se a API está funcionando (health check)
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${NotificationApiConfig.BASE_URL}${NotificationApiConfig.Endpoints.HEALTH}"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val isHealthy = response.isSuccessful
            
            if (isHealthy) {
                Log.d(TAG, "✅ API Python está funcionando")
            } else {
                Log.w(TAG, "⚠️ API Python retornou erro: ${response.code}")
            }
            
            isHealthy
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar saúde da API: ${e.message}", e)
            false
        }
    }
    
    companion object {
        private const val TAG = "NotificationApiService"
    }
}
