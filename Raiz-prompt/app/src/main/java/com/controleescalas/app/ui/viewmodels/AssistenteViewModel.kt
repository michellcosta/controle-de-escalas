package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.NotificationApiService
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.repositories.LocationRequestRepository
import com.controleescalas.app.data.repositories.LocationResponse
import com.controleescalas.app.data.repositories.MotoristaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.Base64

data class ChatMessage(val role: String, val text: String)
// role: "user" ou "assistant"

class AssistenteViewModel(application: Application) : AndroidViewModel(application) {
    private val locationRepo = LocationRequestRepository()
    private val motoristaRepo = MotoristaRepository()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Extrai nome do motorista de frases como:
     * "quanto tempo para joão chegar?", "tempo do michell", "quando o pedro chega"
     */
    private fun extractMotoristaNameFromQuery(text: String): String? {
        val lower = text.lowercase().trim()
        val patterns = listOf(
            Regex("""quanto tempo (?:para|do|da)?\s+(.+?)\s+chegar""", RegexOption.IGNORE_CASE),
            Regex("""tempo (?:para|do|da)?\s+(.+?)\s+chegar""", RegexOption.IGNORE_CASE),
            Regex("""quando\s+(?:o|a)?\s*(.+?)\s+chega""", RegexOption.IGNORE_CASE),
            Regex("""(?:onde|localização)\s+(?:está|do|da)?\s*(.+?)(?:\s|$|[?.!])""", RegexOption.IGNORE_CASE),
            Regex("""(.+?)\s+chega\s+quando""", RegexOption.IGNORE_CASE)
        )
        for (re in patterns) {
            val m = re.find(lower)
            if (m != null) {
                val nome = m.groupValues[1].trim()
                if (nome.length >= 2) return nome
            }
        }
        return null
    }

    private fun normalizeForCompare(s: String): String {
        return Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    private suspend fun findMotoristaByName(baseId: String, searchName: String): Pair<String, String>? {
        val motoristas = motoristaRepo.getMotoristas(baseId)
        val searchNorm = normalizeForCompare(searchName)
        return motoristas
            .filter { it.papel == "motorista" }
            .map { it to normalizeForCompare(it.nome) }
            .firstOrNull { (m, norm) ->
                norm.contains(searchNorm) || searchNorm.contains(norm) ||
                    m.nome.lowercase().split(" ").any { part ->
                        part.contains(searchNorm) || searchNorm.contains(part)
                    }
            }
            ?.let { (m, _) -> m.id to m.nome }
    }

    /**
     * Envia mensagem com imagem (foto ou galeria) para o Assistente via Gemini.
     */
    fun sendMessageWithImage(baseId: String, text: String?, imageUri: Uri) {
        if (baseId.isBlank()) return
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val contentResolver = context.contentResolver
            val bytes = try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { input ->
                        ByteArrayOutputStream().use { output ->
                            input.copyTo(output)
                            output.toByteArray()
                        }
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    "assistant",
                    "Erro ao ler imagem: ${e.message}"
                )
                return@launch
            } ?: run {
                _messages.value = _messages.value + ChatMessage("assistant", "Erro ao ler imagem.")
                return@launch
            }
            val base64Image = Base64.getEncoder().encodeToString(bytes)
            val displayText = text?.takeIf { it.isNotBlank() } ?: "O que há nesta imagem?"
            _messages.value = _messages.value + ChatMessage("user", displayText)
            _isLoading.value = true

            val user = FirebaseManager.auth.currentUser ?: run {
                _messages.value = _messages.value + ChatMessage("assistant", "Usuário não autenticado.")
                _isLoading.value = false
                return@launch
            }
            val tokenResult = runCatching { user.getIdToken(true).await() }.getOrNull()
            val idToken = tokenResult?.token ?: run {
                _messages.value = _messages.value + ChatMessage("assistant", "Erro ao obter token.")
                _isLoading.value = false
                return@launch
            }

            val api = NotificationApiService()
            val (success, result) = api.chatWithAssistente(baseId, displayText, base64Image, idToken)
            _isLoading.value = false
            if (success && result != null) {
                _messages.value = _messages.value + ChatMessage("assistant", result)
            } else {
                _messages.value = _messages.value + ChatMessage(
                    "assistant",
                    result ?: "Não foi possível processar a imagem. Tente novamente."
                )
            }
        }
    }

    fun sendMessage(text: String, baseId: String) {
        if (text.isBlank() || baseId.isBlank()) return
        viewModelScope.launch {
            _messages.value = _messages.value + ChatMessage("user", text)
            _isLoading.value = true

            val nome = extractMotoristaNameFromQuery(text)
            if (nome != null) {
                val found = findMotoristaByName(baseId, nome)
                if (found != null) {
                    val (motoristaId, motoristaNome) = found
                    locationRepo.requestDriverLocation(baseId, motoristaId).fold(
                        onSuccess = {
                            _messages.value = _messages.value + ChatMessage(
                                "assistant",
                                "Consultando localização de $motoristaNome..."
                            )
                            val resp = withTimeoutOrNull(30_000) {
                                locationRepo.listenToLocationResponse(baseId, motoristaId)
                                    .first { it.status == "ready" || it.status == "error" }
                            }
                            when {
                                resp == null -> {
                                    _messages.value = _messages.value + ChatMessage(
                                        "assistant",
                                        "Tempo esgotado. O motorista pode estar com o app fechado ou sem localização."
                                    )
                                }
                                resp.status == "ready" -> {
                                    val dist = resp.distanceKm?.let { "%.1f".format(it) } ?: "?"
                                    val eta = resp.etaMinutes ?: 0
                                    _messages.value = _messages.value + ChatMessage(
                                        "assistant",
                                        "$motoristaNome está a $dist km, deve chegar em aproximadamente $eta minuto(s)."
                                    )
                                }
                                else -> {
                                    _messages.value = _messages.value + ChatMessage(
                                        "assistant",
                                        resp.error ?: "Não foi possível obter a localização."
                                    )
                                }
                            }
                            _isLoading.value = false
                        },
                        onFailure = { e ->
                            _messages.value = _messages.value + ChatMessage(
                                "assistant",
                                "Erro ao solicitar localização: ${e.message}"
                            )
                            _isLoading.value = false
                        }
                    )
                } else {
                    _messages.value = _messages.value + ChatMessage(
                        "assistant",
                        "Não encontrei motorista com esse nome. Verifique se está correto."
                    )
                    _isLoading.value = false
                }
            } else {
                // Pergunta genérica: enviar ao Gemini
                val user = FirebaseManager.auth.currentUser ?: run {
                    _messages.value = _messages.value + ChatMessage("assistant", "Usuário não autenticado.")
                    _isLoading.value = false
                    return@launch
                }
                val tokenResult = runCatching { user.getIdToken(true).await() }.getOrNull()
                val idToken = tokenResult?.token ?: run {
                    _messages.value = _messages.value + ChatMessage("assistant", "Erro ao obter token.")
                    _isLoading.value = false
                    return@launch
                }
                val api = NotificationApiService()
                val (success, result) = api.chatWithAssistente(baseId, text, null, idToken)
                _isLoading.value = false
                if (success && !result.isNullOrBlank()) {
                    _messages.value = _messages.value + ChatMessage("assistant", result)
                } else {
                    _messages.value = _messages.value + ChatMessage(
                        "assistant",
                        result ?: "Não foi possível obter resposta. Tente novamente."
                    )
                }
            }
        }
    }
}
