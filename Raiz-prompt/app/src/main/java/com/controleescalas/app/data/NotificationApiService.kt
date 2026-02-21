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
                response.code == 403 && !responseBody.isNullOrBlank() -> {
                    val msg = try {
                        val json = org.json.JSONObject(responseBody)
                        json.optString("hint").takeIf { it.isNotEmpty() }
                            ?: json.optString("error", responseBody)
                    } catch (_: Exception) { null }
                    Pair(false, msg ?: "Erro 403: $responseBody")
                }
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
     * Resultado do chat com assistente (pode incluir ação para aplicar na escala).
     */
    data class ChatAssistenteResult(
        val success: Boolean,
        val text: String?,
        val error: String?,
        // Ação única (retrocompatibilidade)
        val addToScaleAction: AddToScaleAction? = null,
        val updateInScaleAction: UpdateInScaleAction? = null,
        // Lista de ações (quando há múltiplos motoristas, ex.: foto de escala)
        val addToScaleActions: List<AddToScaleAction> = emptyList(),
        val updateInScaleActions: List<UpdateInScaleAction> = emptyList()
    ) {
        /** Ação de adicionar (retrocompatibilidade). */
        val action: AddToScaleAction? get() = addToScaleAction
    }

    /**
     * Ação retornada pelo assistente para adicionar motorista à escala.
     */
    data class AddToScaleAction(
        val motoristaNome: String,
        val ondaIndex: Int,
        val vaga: String,
        val rota: String,
        val sacas: Int?
    )

    /**
     * Ação para atualizar vaga/rota/sacas de motorista já escalado (não adicionar de novo).
     */
    data class UpdateInScaleAction(
        val motoristaNome: String,
        val ondaIndex: Int,
        val vaga: String?,
        val rota: String?,
        val sacas: Int?
    )

    /**
     * Chat com Assistente - texto e/ou imagem, com histórico para manter contexto.
     * @param history últimas mensagens [{role, content}] (ex.: user/assistant) para continuar a conversa.
     * @return ChatAssistenteResult com texto e opcionalmente action (add_to_scale).
     */
    suspend fun chatWithAssistente(
        baseId: String,
        text: String,
        base64Image: String?,
        idToken: String,
        history: List<Pair<String, String>> = emptyList()
    ): ChatAssistenteResult = withContext(Dispatchers.IO) {
        try {
            val url = "${NotificationApiConfig.BASE_URL}${NotificationApiConfig.Endpoints.ASSISTENTE_CHAT}"
            val historyArray = org.json.JSONArray().apply {
                history.takeLast(20).forEach { (role, content) ->
                    put(org.json.JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }
            }
            val jsonBody = JSONObject().apply {
                put("baseId", baseId)
                put("text", text)
                if (base64Image != null) put("imageBase64", base64Image)
                if (historyArray.length() > 0) put("history", historyArray)
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

                    // Parsear lista de ações (novo campo "actions": [...])
                    val addList = mutableListOf<AddToScaleAction>()
                    val updateList = mutableListOf<UpdateInScaleAction>()
                    val actionsArray = json?.optJSONArray("actions")
                    if (actionsArray != null) {
                        for (i in 0 until actionsArray.length()) {
                            val a = actionsArray.optJSONObject(i) ?: continue
                            when (a.optString("type")) {
                                "add_to_scale" -> {
                                    val nome = a.optString("motoristaNome").trim(); if (nome.isBlank()) continue
                                    addList.add(AddToScaleAction(
                                        motoristaNome = nome,
                                        ondaIndex = a.optInt("ondaIndex", 0).coerceAtLeast(0),
                                        vaga = a.optString("vaga").trim().ifBlank { "01" }.let { v -> if (v.length == 1) "0$v" else v },
                                        rota = a.optString("rota").trim().uppercase(),
                                        sacas = if (a.has("sacas") && !a.isNull("sacas")) a.optInt("sacas", 0).takeIf { it > 0 } else null
                                    ))
                                }
                                "update_in_scale" -> {
                                    val nome = a.optString("motoristaNome").trim(); if (nome.isBlank()) continue
                                    updateList.add(UpdateInScaleAction(
                                        motoristaNome = nome,
                                        ondaIndex = a.optInt("ondaIndex", 0).coerceAtLeast(0),
                                        vaga = a.optString("vaga").trim().takeIf { it.isNotBlank() }?.let { v -> if (v.length == 1) "0$v" else v },
                                        rota = a.optString("rota").trim().uppercase().takeIf { it.isNotBlank() },
                                        sacas = if (a.has("sacas") && !a.isNull("sacas")) a.optInt("sacas", 0).takeIf { it >= 0 } else null
                                    ))
                                }
                            }
                        }
                    }

                    // Retrocompatibilidade: ler campo "action" único caso "actions" não exista
                    val actionObj = if (actionsArray == null) json?.optJSONObject("action") else null
                    val addAction = addList.firstOrNull() ?: actionObj?.let { a ->
                        if (a.optString("type") == "add_to_scale") {
                            val nome = a.optString("motoristaNome").trim().ifBlank { return@let null } ?: return@let null
                            AddToScaleAction(
                                motoristaNome = nome,
                                ondaIndex = a.optInt("ondaIndex", 0).coerceAtLeast(0),
                                vaga = a.optString("vaga").trim().ifBlank { "01" }.let { v -> if (v.length == 1) "0$v" else v },
                                rota = a.optString("rota").trim().uppercase(),
                                sacas = if (a.has("sacas") && !a.isNull("sacas")) a.optInt("sacas", 0).takeIf { it > 0 } else null
                            )
                        } else null
                    }
                    val updateAction = updateList.firstOrNull() ?: actionObj?.let { a ->
                        if (a.optString("type") == "update_in_scale") {
                            val nome = a.optString("motoristaNome").trim().ifBlank { return@let null } ?: return@let null
                            UpdateInScaleAction(
                                motoristaNome = nome,
                                ondaIndex = a.optInt("ondaIndex", 0).coerceAtLeast(0),
                                vaga = a.optString("vaga").trim().takeIf { it.isNotBlank() }?.let { v -> if (v.length == 1) "0$v" else v },
                                rota = a.optString("rota").trim().uppercase().takeIf { it.isNotBlank() },
                                sacas = if (a.has("sacas") && !a.isNull("sacas")) a.optInt("sacas", 0).takeIf { it >= 0 } else null
                            )
                        } else null
                    }
                    ChatAssistenteResult(
                        success = true, text = textRes, error = null,
                        addToScaleAction = addAction, updateInScaleAction = updateAction,
                        addToScaleActions = addList, updateInScaleActions = updateList
                    )
                }
                else -> {
                    val errorMsg = when {
                        body.isNullOrBlank() -> "Erro ${response.code}"
                        body.contains("<") || body.contains("Internal Server Error") ->
                            if (response.code == 500)
                                "Serviço temporariamente indisponível. Tente novamente ou use uma foto menor."
                            else
                                "Erro ${response.code}. Tente novamente."
                        else -> {
                            try {
                                org.json.JSONObject(body).optString("error").takeIf { it.isNotBlank() }
                                    ?: "Erro ${response.code}: ${body.take(200)}"
                            } catch (_: Exception) {
                                "Erro ${response.code}: ${body.take(200)}"
                            }
                        }
                    }
                    ChatAssistenteResult(success = false, text = null, error = errorMsg)
                }
            }
        } catch (e: Exception) {
            ChatAssistenteResult(success = false, text = null, error = "Erro: ${e.message}")
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
