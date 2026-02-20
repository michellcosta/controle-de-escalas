package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.Repository
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Resultado de uma linha parseada da foto (formato: Nome / Vaga / Rota)
 */
data class ParsedEscalaEntry(
    val nome: String,
    val vaga: String,
    val rota: String,
    val ondaIndex: Int,
    val motoristaId: String? = null,   // null se não encontrou
    val motoristaNome: String? = null  // nome do motorista casado
)

class EscalaPorFotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _motoristas = MutableStateFlow<List<AdminMotoristaCardData>>(emptyList())
    val motoristas: StateFlow<List<AdminMotoristaCardData>> = _motoristas.asStateFlow()

    private val _parsedEntries = MutableStateFlow<List<ParsedEscalaEntry>>(emptyList())
    val parsedEntries: StateFlow<List<ParsedEscalaEntry>> = _parsedEntries.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadMotoristas(baseId: String) {
        viewModelScope.launch {
            try {
                val list = repository.getMotoristas(baseId)
                _motoristas.value = list
            } catch (e: Exception) {
                _error.value = "Erro ao carregar motoristas: ${e.message}"
            }
        }
    }

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val result = withContext(Dispatchers.IO) {
                    textRecognizer.process(inputImage).await()
                }
                val fullText = result.text
                val entriesWithOnda = parseEscalaText(fullText)
                _parsedEntries.value = matchMotoristas(entriesWithOnda)
            } catch (e: Exception) {
                _error.value = "Erro ao processar imagem: ${e.message}"
                _parsedEntries.value = emptyList()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun parseEscalaText(text: String): List<Pair<Triple<String, String, String>, Int>> {
        val result = mutableListOf<Pair<Triple<String, String, String>, Int>>()
        var currentOnda = 0
        val hasOndaHeaders = text.contains(Regex("\\d+[aª]?\\s*onda", RegexOption.IGNORE_CASE))
        val rawEntries = mutableListOf<Triple<String, String, String>>()

        fun parseLine(line: String): Triple<String, String, String>? {
            val parts = line.split(Regex("\\s*[/xX×]\\s*"))
            if (parts.size >= 3) {
                val nome = parts[0].trim()
                val vagaRaw = parts[1].trim().replace(Regex("[^0-9]"), "")
                val vaga = when {
                    vagaRaw.length >= 2 -> vagaRaw.takeLast(2)
                    vagaRaw.length == 1 -> "0$vagaRaw"
                    else -> ""
                }
                val rota = formatarRota(parts[2].trim())
                if (nome.isNotBlank() && vaga.isNotBlank() && rota.isNotBlank()) {
                    return Triple(nome, vaga, rota)
                }
            } else if (parts.size == 2) {
                val nome = parts[0].trim()
                val rest = parts[1].trim().split(Regex("\\s+"))
                if (rest.size >= 2) {
                    val vagaRaw = rest[0].replace(Regex("[^0-9]"), "")
                    val vaga = when {
                        vagaRaw.length >= 2 -> vagaRaw.takeLast(2)
                        vagaRaw.length == 1 -> "0$vagaRaw"
                        else -> ""
                    }
                    val rota = formatarRota(rest[1])
                    if (nome.isNotBlank() && vaga.isNotBlank()) return Triple(nome, vaga, rota)
                }
            }
            return null
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val ondaMatch = Regex("""^(\d+)[aª]?\s*onda""", RegexOption.IGNORE_CASE).find(line)
            if (ondaMatch != null) {
                currentOnda = (ondaMatch.groupValues[1].toIntOrNull() ?: 1) - 1
                continue
            }
            val entry = parseLine(line)
            if (entry != null) {
                if (hasOndaHeaders) {
                    result.add(entry to currentOnda)
                } else {
                    rawEntries.add(entry)
                }
            }
        }

        return if (hasOndaHeaders) result else assignOndaIndices(rawEntries)
    }

    private fun assignOndaIndices(entries: List<Triple<String, String, String>>): List<Pair<Triple<String, String, String>, Int>> {
        val result = mutableListOf<Pair<Triple<String, String, String>, Int>>()
        var ondaIdx = 0
        var count = 0
        for (e in entries) {
            result.add(e to ondaIdx)
            count++
            if (count >= 6) {
                count = 0
                ondaIdx++
            }
        }
        return result
    }

    private fun formatarRota(rota: String): String {
        val limpo = rota.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (limpo.isEmpty()) return ""
        val letra = limpo.first().toString()
        val numero = limpo.drop(1).takeWhile { it.isDigit() }.ifBlank { "0" }
        return "$letra-$numero"
    }

    private fun matchMotoristas(entriesWithOnda: List<Pair<Triple<String, String, String>, Int>>): List<ParsedEscalaEntry> {
        val motoristasList = _motoristas.value

        return entriesWithOnda.map { (triple, ondaIdx) ->
            val (nome, vaga, rota) = triple
            val normalizado = nome.uppercase().trim()
                .replace(Regex("[ÁÀÃÂ]"), "A")
                .replace(Regex("[ÉÈÊ]"), "E")
                .replace(Regex("[ÍÌÎ]"), "I")
                .replace(Regex("[ÓÒÕÔ]"), "O")
                .replace(Regex("[ÚÙÛ]"), "U")
                .replace(Regex("[Ç]"), "C")

            val match = motoristasList.find { m ->
                val mNorm = m.nome.uppercase()
                    .replace(Regex("[ÁÀÃÂ]"), "A")
                    .replace(Regex("[ÉÈÊ]"), "E")
                    .replace(Regex("[ÍÌÎ]"), "I")
                    .replace(Regex("[ÓÒÕÔ]"), "O")
                    .replace(Regex("[ÚÙÛ]"), "U")
                    .replace(Regex("[Ç]"), "C")
                mNorm == normalizado || mNorm.contains(normalizado) || normalizado.contains(mNorm)
            }

            ParsedEscalaEntry(
                nome = nome,
                vaga = vaga,
                rota = rota,
                ondaIndex = ondaIdx,
                motoristaId = match?.id,
                motoristaNome = match?.nome
            )
        }
    }

    fun clearParsed() {
        _parsedEntries.value = emptyList()
        _error.value = null
    }
}
