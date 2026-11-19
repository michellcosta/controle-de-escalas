package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/** Callback para aplicar ação "adicionar à escala" vinda do assistente. */
typealias OnAddToScaleAction = (motoristaId: String, nome: String, ondaIndex: Int, vaga: String, rota: String, sacas: Int?) -> Unit

/** Callback para aplicar ação "atualizar na escala" (vaga/rota/sacas) quando o motorista já está escalado. */
typealias OnUpdateInScaleAction = (motoristaId: String, ondaIndex: Int, vaga: String?, rota: String?, sacas: Int?) -> Unit

/** Callback para aplicar múltiplas ações vinda do assistente em massa (previne race conditions). */
typealias OnBulkScaleActions = (List<BulkScaleAction>) -> Unit

/** Callback para enviar notificações push via assistente. (motoristaId, nome, mensagem) */
typealias OnSendNotification = (motoristaId: String, nome: String, body: String) -> Unit

/** Callback para remover motorista da escala vinda do assistente. (motoristaId, ondaIndex) */
typealias OnRemoveFromScaleAction = (motoristaId: String, ondaIndex: Int) -> Unit

class AssistenteViewModel(application: Application) : AndroidViewModel(application) {
    private val locationRepo = LocationRequestRepository()
    private val motoristaRepo = MotoristaRepository()
    private var persistentImageBase64: String? = null // Visão persistente durante a sessão do chat
    private var _currentTurno = "AM"
    fun setCurrentTurno(newTurno: String) { _currentTurno = newTurno }

    private var _userId = ""
    private var _userRole = ""
    fun setUserIdentity(userId: String, userRole: String) {
        _userId = userId
        _userRole = userRole
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var onAddToScaleAction: OnAddToScaleAction? = null
    private var onUpdateInScaleAction: OnUpdateInScaleAction? = null
    private var onBulkActions: OnBulkScaleActions? = null
    private var onSendNotification: OnSendNotification? = null
    private var onRemoveFromScaleAction: OnRemoveFromScaleAction? = null

    fun setOnAddToScaleAction(callback: OnAddToScaleAction?) { onAddToScaleAction = callback }
    fun setOnUpdateInScaleAction(callback: OnUpdateInScaleAction?) { onUpdateInScaleAction = callback }
    fun setOnBulkActions(callback: OnBulkScaleActions?) { onBulkActions = callback }
    fun setOnSendNotification(callback: OnSendNotification?) { onSendNotification = callback }
    fun setOnRemoveFromScaleAction(callback: OnRemoveFromScaleAction?) { onRemoveFromScaleAction = callback }

    /**
     * Extrai nome do motorista de frases como:
     * "quanto tempo para joão chegar?", "em quanto tempo o michell chega no galpão?", "quando o pedro chega"
     */
    private fun extractMotoristaNameFromQuery(text: String): String? {
        val lower = text.lowercase().trim()
        val patterns = listOf(
            Regex("""em quanto tempo (?:o|a)?\s*(.+?)\s+chega""", RegexOption.IGNORE_CASE),
            Regex("""quanto tempo (?:para|do|da|o|a)?\s*(.+?)\s+chega""", RegexOption.IGNORE_CASE),
            Regex("""quanto tempo (?:para|do|da)?\s+(.+?)\s+chegar""", RegexOption.IGNORE_CASE),
            Regex("""tempo (?:para|do|da)?\s+(.+?)\s+chegar""", RegexOption.IGNORE_CASE),
            Regex("""quando\s+(?:o|a)?\s*(.+?)\s+chega""", RegexOption.IGNORE_CASE),
            Regex("""(?:onde|localização)\s+(?:está|do|da)?\s*(.+?)(?:\s|$|[?.!])""", RegexOption.IGNORE_CASE),
            Regex("""(.+?)\s+chega\s+quando""", RegexOption.IGNORE_CASE),
            Regex("""tempo (?:do|da|para)\s+(.+?)(?:\s|$|[?.!])""", RegexOption.IGNORE_CASE)
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

    private fun findMotoristaByName(baseId: String, searchName: String, preFetched: List<com.controleescalas.app.data.models.AdminMotoristaCardData>? = null): Pair<String, String>? {
        val searchNorm = normalizeForCompare(searchName).trim()
        if (searchNorm.isEmpty()) return null
        val motoristas = preFetched ?: return null
        
        // Removemos o filtro de papel "motorista" pois Laura pode ser "auxiliar" ou "admin"
        // Pegaremos todos que tiverem nome
        val candidates = motoristas.map { it to normalizeForCompare(it.nome).trim() }

        // 1. Busca Exata (Normalizada) - Prioridade Máxima
        candidates.firstOrNull { it.second == searchNorm }?.let { 
            android.util.Log.d("AssistenteVM", "Match exato: '$searchName' -> '${it.first.nome}' (${it.first.papel})")
            return it.first.id to it.first.nome 
        }

        // 2. Busca por Início do Nome Completo
        candidates.firstOrNull { (m, norm) ->
            searchNorm.length >= 3 && norm.startsWith(searchNorm)
        }?.let { (m, _) ->
            android.util.Log.d("AssistenteVM", "Match início total: '$searchName' -> '${m.nome}'")
            return m.id to m.nome
        }

        // 3. Busca por Partes Relevantes (mínimo 4 caracteres para ser bem rigoroso)
        val match = candidates.firstOrNull { (m, norm) ->
            val searchWords = searchNorm.split(" ").filter { it.length >= 4 }
            val nameWords = norm.split(" ").filter { it.length >= 4 }
            
            // Se as palavras principais baterem (ambas precisam ser longas)
            searchWords.any { sw -> 
                nameWords.any { nw -> nw.startsWith(sw) || sw.startsWith(nw) }
            }
        }
        
        if (match != null) {
            android.util.Log.d("AssistenteVM", "Match parcial rigoroso (4+ chars): '$searchName' -> '${match.first.nome}'")
            return match.first.id to match.first.nome
        }
        
        android.util.Log.w("AssistenteVM", "Nenhum motorista encontrado para: '$searchName' (Buscados ${candidates.size} candidatos)")
        return null
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
                        val originalBitmap = BitmapFactory.decodeStream(input)
                        if (originalBitmap == null) return@withContext null
                        
                        // Redimensionar se for muito grande (max 1280px em maior dimensão) para evitar erro de 5MB
                        val maxDim = 1200
                        val scaledBitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                            val (newW, newH) = if (ratio > 1) (maxDim to (maxDim / ratio).toInt()) else ((maxDim * ratio).toInt() to maxDim)
                            Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
                        } else originalBitmap

                        ByteArrayOutputStream().use { output ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
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
            persistentImageBase64 = base64Image // Salva para seguir no contexto em msgs de texto
            val displayText = if (text?.isNotBlank() == true) {
                "[Turno Selecionado: $_currentTurno] ${text}"
            } else {
                "[Turno Selecionado: $_currentTurno] Monte a escala conforme esta imagem."
            }
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
            val historyImg = _messages.value.map { it.role to it.text }

            // Buscar dados do usuário para identidade (fallback se não vier da navegação)
            val db = FirebaseManager.firestore
            val userDoc = try {
                db.collection("usuarios").document(user.uid).get().await()
            } catch (e: Exception) { null }
            val userName = userDoc?.getString("nome")
            val roleToSend = _userRole.ifBlank { userDoc?.getString("papel") }

            var chatResult = api.chatWithAssistente(
                baseId = baseId,
                text = displayText,
                base64Image = base64Image,
                idToken = idToken,
                history = historyImg,
                userName = userName,
                userRole = roleToSend,
                userId = _userId.takeIf { it.isNotBlank() },
                turno = _currentTurno
            )
            val firstError = chatResult.error
            if (!chatResult.success && firstError != null && "401" in firstError && "Token" in firstError) {
                runCatching { user.getIdToken(true).await() }.getOrNull()?.token?.let { freshToken ->
                    chatResult = api.chatWithAssistente(
                        baseId = baseId,
                        text = displayText,
                        base64Image = base64Image,
                        idToken = freshToken,
                        history = historyImg,
                        userName = userName,
                        userRole = roleToSend,
                        userId = _userId.takeIf { it.isNotBlank() },
                        turno = _currentTurno
                    )
                }
            }
            _isLoading.value = false
            val text = chatResult.text
            val error = chatResult.error
            if (chatResult.success && text != null) {
                _messages.value = _messages.value + ChatMessage("assistant", text)
                android.util.Log.d("AssistenteVM", "Recebidas ${chatResult.addToScaleActions.size} adições e ${chatResult.updateInScaleActions.size} atualizações")
                
                viewModelScope.launch {
                    val motoristas = motoristaRepo.getMotoristas(baseId)
                    val bulkActions = mutableListOf<BulkScaleAction>()
                    
                    // Processar Adições
                    val addActions = chatResult.addToScaleActions.toMutableList()
                    if (addActions.isEmpty() && chatResult.addToScaleAction != null) {
                        addActions.add(chatResult.addToScaleAction!!)
                    }
                    
                    for (act in addActions) {
                        val found = findMotoristaByName(baseId, act.motoristaNome, motoristas)
                        if (found != null) {
                            bulkActions.add(BulkScaleAction(
                                isAdd = true,
                                motoristaId = found.first,
                                nome = found.second,
                                ondaIndex = act.ondaIndex,
                                vaga = act.vaga,
                                rota = act.rota,
                                sacas = act.sacas
                            ))
                        }
                    }
                    
                    // Processar Atualizações
                    val updateActions = chatResult.updateInScaleActions.toMutableList()
                    if (updateActions.isEmpty() && chatResult.updateInScaleAction != null) {
                        updateActions.add(chatResult.updateInScaleAction!!)
                    }
                    
                    for (act in updateActions) {
                        val found = findMotoristaByName(baseId, act.motoristaNome, motoristas)
                        if (found != null) {
                            bulkActions.add(BulkScaleAction(
                                isAdd = false,
                                motoristaId = found.first,
                                nome = found.second,
                                ondaIndex = act.ondaIndex,
                                vaga = act.vaga,
                                rota = act.rota,
                                sacas = act.sacas
                            ))
                        }
                    }
                    
                    if (bulkActions.isNotEmpty()) {
                        val ids = bulkActions.map { it.motoristaId }
                        if (ids.size != ids.distinct().size) {
                            android.util.Log.w("AssistenteVM", "ATENÇÃO: Colisão de IDs detectada no lote! ${ids.size} ações para ${ids.distinct().size} motoristas únicos.")
                            bulkActions.forEach { 
                                android.util.Log.d("AssistenteVM", "   - Ação para: ${it.nome} (ID: ${it.motoristaId})")
                            }
                        }
                        
                        android.util.Log.d("AssistenteVM", "Aplicando ${bulkActions.size} ações em massa")
                        onBulkActions?.invoke(bulkActions)
                    }
                }
            } else {
                val errorMsg = when {
                    error != null && "401" in error && "Token" in error ->
                        "Sessão expirada. Faça logout e login novamente para continuar."
                    error != null && (error.contains("500") || error.contains("Internal Server") || error.contains("<")) ->
                        "Não foi possível analisar a imagem. Tente novamente ou use uma foto menor e mais nítida."
                    else ->
                        error ?: "Não foi possível processar a imagem. Tente novamente."
                }
                _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
            }
        }
    }

    /** Aplica ação add_to_scale: resolve motorista (id do backend quando presente) e invoca callback. */
    private fun applyAddToScaleAction(baseId: String, action: NotificationApiService.AddToScaleAction, preFetched: List<com.controleescalas.app.data.models.AdminMotoristaCardData>? = null) {
        viewModelScope.launch {
            val resolved = action.motoristaId?.let { it to action.motoristaNome }
                ?: findMotoristaByName(baseId, action.motoristaNome, preFetched)
            if (resolved != null) {
                val (motoristaId, nome) = resolved
                onAddToScaleAction?.invoke(motoristaId, nome, action.ondaIndex, action.vaga, action.rota, action.sacas)
            }
        }
    }

    /** Aplica ação update_in_scale: resolve motorista e invoca callback (só atualiza, não adiciona). */
    private fun applyUpdateInScaleAction(baseId: String, action: NotificationApiService.UpdateInScaleAction, preFetched: List<com.controleescalas.app.data.models.AdminMotoristaCardData>? = null) {
        viewModelScope.launch {
            val resolved = action.motoristaId?.let { it to action.motoristaNome }
                ?: findMotoristaByName(baseId, action.motoristaNome, preFetched)
            if (resolved != null) {
                val (motoristaId, _) = resolved
                onUpdateInScaleAction?.invoke(motoristaId, action.ondaIndex, action.vaga, action.rota, action.sacas)
            }
        }
    }

    /** Aplica ação remove_from_scale: resolve motorista e invoca callback de remoção. */
    private fun applyRemoveFromScaleAction(baseId: String, action: NotificationApiService.RemoveFromScaleAction, preFetched: List<com.controleescalas.app.data.models.AdminMotoristaCardData>? = null) {
        viewModelScope.launch {
            val resolved = action.motoristaId?.let { it to action.motoristaNome }
                ?: findMotoristaByName(baseId, action.motoristaNome, preFetched)
            if (resolved != null) {
                val (motoristaId, _) = resolved
                val ondaIndex = action.ondaIndex ?: 0
                android.util.Log.d("AssistenteVM", "➖ Removendo motorista ${resolved.second} da onda ${ondaIndex + 1}")
                onRemoveFromScaleAction?.invoke(motoristaId, ondaIndex)
            } else {
                _messages.value = _messages.value + ChatMessage(
                    "assistant",
                    "Não encontrei o motorista \"${action.motoristaNome}\" na base para remover."
                )
            }
        }
    }

    private fun applySendNotificationAction(baseId: String, action: NotificationApiService.SendNotificationAction, preFetched: List<com.controleescalas.app.data.models.AdminMotoristaCardData>? = null) {
        viewModelScope.launch {
            if (action.baseInteira) {
                // Aviso para todos os motoristas da base (tratado no OperationalViewModel)
                android.util.Log.d("AssistenteVM", "🔔 Solicitando notificação para a BASE INTEIRA")
                onSendNotification?.invoke("ALL", "Base inteira", action.body)
            } else if (action.motoristaNome != null) {
                // Notificação individual
                val match = action.motoristaId?.let { it to action.motoristaNome }
                    ?: findMotoristaByName(baseId, action.motoristaNome, preFetched)
                if (match != null) {
                    android.util.Log.d("AssistenteVM", "🔔 Enviando notificação individual para ${match.second}")
                    onSendNotification?.invoke(match.first, match.second, action.body)
                }
            } else if (action.ondaIndex != null) {
                // Notificação para a ONDA (será tratada no OperationalViewModel)
                android.util.Log.d("AssistenteVM", "🔔 Solicitando notificação para ONDA ${action.ondaIndex + 1}")
                onSendNotification?.invoke("WAVE_${action.ondaIndex}", "Onda ${action.ondaIndex + 1}", action.body)
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
                            // Ignorar respostas VELHAS: um doc 'ready' de horas
                            // atrás mostrava distância desatualizada (o motorista
                            // pode já ter se movido). Exige resposta gravada
                            // DEPOIS do início desta consulta.
                            val inicioConsulta = System.currentTimeMillis()
                            val resp = withTimeoutOrNull(30_000) {
                                locationRepo.listenToLocationResponse(baseId, motoristaId)
                                    .first {
                                        (it.status == "ready" || it.status == "error") &&
                                            (it.atualizadoEmMillis ?: 0L) >= inicioConsulta
                                    }
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
                                    val texto = if (resp.metodo == "estimativa") {
                                        "Estimativa aproximada (sem rota de estrada no momento): " +
                                            "$motoristaNome está a ~$dist km em linha reta, chegada em ~$eta minuto(s)."
                                    } else {
                                        "$motoristaNome está a $dist km por estrada, deve chegar em aproximadamente $eta minuto(s)."
                                    }
                                    _messages.value = _messages.value + ChatMessage("assistant", texto)
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
                            val msg = e.message.orEmpty()
                            val friendly = when {
                                msg.contains("500") || msg.contains("404") || msg.contains("UNREGISTERED") || msg.contains("NOT_FOUND") ->
                                    "Não foi possível obter o tempo de chegada do $motoristaNome. O app pode estar fechado no celular dele ou o dispositivo sem internet. Peça para ele abrir o app e tentar novamente."
                                else ->
                                    "Não foi possível consultar a localização de $motoristaNome. Tente novamente em instantes."
                            }
                            _messages.value = _messages.value + ChatMessage("assistant", friendly)
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
                val history = _messages.value.map { it.role to it.text }

                // Buscar dados do usuário para identidade (fallback se não vier da navegação)
                val db = FirebaseManager.firestore
                val userDoc = try {
                    db.collection("usuarios").document(user.uid).get().await()
                } catch (e: Exception) { null }
                val userName = userDoc?.getString("nome")
                val roleToSend = _userRole.ifBlank { userDoc?.getString("papel") }

                // Envia a imagem persistente se houver, para manter o contexto visual
                var chatResult = api.chatWithAssistente(
                    baseId = baseId,
                    text = text,
                    base64Image = persistentImageBase64,
                    idToken = idToken,
                    history = history,
                    userName = userName,
                    userRole = roleToSend,
                    userId = _userId.takeIf { it.isNotBlank() },
                    turno = _currentTurno
                )
                val firstError = chatResult.error
                if (!chatResult.success && firstError != null && "401" in firstError && "Token" in firstError) {
                    runCatching { user.getIdToken(true).await() }.getOrNull()?.token?.let { fresh ->
                        chatResult = api.chatWithAssistente(
                            baseId = baseId,
                            text = text,
                            base64Image = persistentImageBase64,
                            idToken = fresh,
                            history = history,
                            userName = userName,
                            userRole = roleToSend,
                            userId = _userId.takeIf { it.isNotBlank() },
                            turno = _currentTurno
                        )
                    }
                }
                
                // Buscar lista de motoristas para resolver nomes das ações
                val motoristaList = motoristaRepo.getMotoristas(baseId)
                
                _isLoading.value = false
                val resText = chatResult.text
                val error = chatResult.error
                if (chatResult.success && !resText.isNullOrBlank()) {
                    _messages.value = _messages.value + ChatMessage("assistant", resText)
                    android.util.Log.d("AssistenteVM", "Recebidas ${chatResult.addToScaleActions.size} adições, ${chatResult.updateInScaleActions.size} atualizações, ${chatResult.removeFromScaleActions.size} remoções e ${chatResult.sendNotificationActions.size} avisos")
                    chatResult.addToScaleActions.forEach { applyAddToScaleAction(baseId, it, motoristaList) }
                    chatResult.updateInScaleActions.forEach { applyUpdateInScaleAction(baseId, it, motoristaList) }
                    chatResult.removeFromScaleActions.forEach { applyRemoveFromScaleAction(baseId, it, motoristaList) }
                    chatResult.sendNotificationActions.forEach { applySendNotificationAction(baseId, it, motoristaList) }
                    
                    // Retrocompatibilidade
                    if (chatResult.addToScaleActions.isEmpty() && chatResult.updateInScaleActions.isEmpty()) {
                        chatResult.addToScaleAction?.let { applyAddToScaleAction(baseId, it, motoristaList) }
                        chatResult.updateInScaleAction?.let { applyUpdateInScaleAction(baseId, it, motoristaList) }
                    }
                } else {
                    val errorMsg = when {
                        error != null && "401" in error && "Token" in error ->
                            "Sessão expirada. Faça logout e login novamente para continuar."
                        error != null && (error.contains("500") || error.contains("Internal Server") || error.contains("<")) ->
                            "Serviço temporariamente indisponível. Tente novamente em instantes."
                        else ->
                            error ?: "Não foi possível obter resposta. Tente novamente."
                    }
                    _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
                }
            }
        }
    }
}
