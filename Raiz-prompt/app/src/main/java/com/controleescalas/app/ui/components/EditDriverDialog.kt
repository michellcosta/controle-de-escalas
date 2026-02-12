package com.controleescalas.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.ui.theme.*

/**
 * Valida se a vaga já está sendo usada na mesma onda
 */
fun validarVagaNaOnda(
    ondas: List<Onda>,
    ondaIndex: Int,
    vaga: String,
    motoristaIdAtual: String? = null
): Pair<Boolean, String?> {
    if (vaga.isBlank()) return Pair(true, null)
    
    val onda = ondas.getOrNull(ondaIndex) ?: return Pair(true, null)
    
    val vagaJaExiste = onda.itens.any { item ->
        item.vaga == vaga && item.motoristaId != motoristaIdAtual
    }
    
    return if (vagaJaExiste) {
        Pair(false, "A vaga $vaga já está sendo usada por outro motorista nesta onda")
    } else {
        Pair(true, null)
    }
}

/**
 * Valida se a rota já está sendo usada em qualquer onda do turno
 */
fun validarRotaNoTurno(
    ondas: List<Onda>,
    rota: String,
    motoristaIdAtual: String? = null
): Pair<Boolean, String?> {
    if (rota.isBlank()) return Pair(true, null)
    
    val rotaFormatada = rota.trim().uppercase()
    val rotaJaExiste = ondas.flatMap { it.itens }.any { item ->
        item.rota.trim().uppercase() == rotaFormatada && item.motoristaId != motoristaIdAtual
    }
    
    return if (rotaJaExiste) {
        Pair(false, "A rota $rota já está sendo usada por outro motorista neste turno")
    } else {
        Pair(true, null)
    }
}

/**
 * Formata a rota automaticamente: converte para maiúsculas e adiciona traço entre letra e número
 * Ex: "f9" -> "F-9", "T15" -> "T-15"
 */
private fun formatarRota(input: String): String {
    if (input.isBlank()) return ""
    
    val trimmed = input.trim().uppercase()
    
    // Se já está no formato correto (letra(s)-número(s)), manter como está
    val formatoCorreto = Regex("^[A-Z]+-\\d+$")
    if (formatoCorreto.matches(trimmed)) {
        return trimmed
    }
    
    // Remover traços e espaços existentes, mas preservar a ordem dos caracteres
    val cleanInput = trimmed.replace("-", "").replace(" ", "")
    
    // Separar letras (início) e números (fim), preservando a ordem
    val letters = StringBuilder()
    val numbers = StringBuilder()
    
    var encontrouNumero = false
    for (char in cleanInput) {
        when {
            char.isLetter() -> {
                if (encontrouNumero) {
                    // Se já encontrou número e aparece letra, ignorar (formato inválido)
                    continue
                }
                letters.append(char)
            }
            char.isDigit() -> {
                encontrouNumero = true
                numbers.append(char) // Preserva a ordem dos dígitos
            }
        }
    }
    
    return when {
        letters.isNotEmpty() && numbers.isNotEmpty() -> "$letters-$numbers"
        letters.isNotEmpty() -> letters.toString()
        numbers.isNotEmpty() -> numbers.toString()
        else -> cleanInput
    }
}

@Composable
fun EditDriverDialog(
    motorista: OndaItem,
    ondas: List<Onda>,
    ondaIndex: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?) -> Unit // Adicionado parâmetro sacas
) {
    var vaga by remember { mutableStateOf(motorista.vaga) }
    var rota by remember { mutableStateOf(motorista.rota) } // Não formatar inicialmente
    var sacas by remember { mutableStateOf(motorista.sacas?.toString() ?: "") }
    var erroVaga by remember { mutableStateOf<String?>(null) }
    var erroRota by remember { mutableStateOf<String?>(null) }
    
    // Formatar rota inicial se não estiver vazia
    LaunchedEffect(Unit) {
        if (rota.isNotBlank() && !rota.contains("-")) {
            rota = formatarRota(rota)
        }
    }
    
    // Detectar foco para aplicar formatação
    val rotaInteractionSource = remember { MutableInteractionSource() }
    val rotaFocused by rotaInteractionSource.collectIsFocusedAsState()
    
    // Aplicar formatação quando perde o foco
    LaunchedEffect(rotaFocused) {
        if (!rotaFocused && rota.isNotBlank()) {
            rota = formatarRota(rota)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Editar Motorista", color = TextWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    motorista.nome,
                    color = NeonGreen,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Campo Vaga com validação
                OutlinedTextField(
                    value = vaga,
                    onValueChange = { 
                        vaga = it
                        // Validar vaga em tempo real
                        val (valida, mensagem) = validarVagaNaOnda(ondas, ondaIndex, it, motorista.motoristaId)
                        erroVaga = mensagem
                    },
                    label = { Text("Vaga", color = TextGray) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, null, tint = NeonGreen)
                    },
                    isError = erroVaga != null,
                    supportingText = erroVaga?.let { { Text(it, color = Color(0xFFEF4444)) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else NeonGreen,
                        unfocusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else TextGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Usar OutlinedTextField com InteractionSource para detectar foco
                OutlinedTextField(
                    value = if (rotaFocused) rota else formatarRota(rota),
                    onValueChange = { newValue ->
                        rota = newValue // Permite digitar livremente enquanto focado
                        // Validar rota em tempo real (após formatação)
                        if (!rotaFocused && newValue.isNotBlank()) {
                            val rotaFormatada = formatarRota(newValue)
                            val (valida, mensagem) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
                            erroRota = mensagem
                        }
                    },
                    interactionSource = rotaInteractionSource,
                    label = { Text("Rota", color = TextGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Info, null, tint = NeonBlue)
                    },
                    isError = erroRota != null,
                    supportingText = erroRota?.let { { Text(it, color = Color(0xFFEF4444)) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else NeonBlue,
                        unfocusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else TextGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                CustomTextField(
                    value = sacas,
                    onValueChange = { sacas = it.filter { c -> c.isDigit() } },
                    label = "Sacas (opcional)",
                    leadingIcon = Icons.Default.ShoppingCart,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            NeonButton(
                text = "Salvar",
                onClick = {  
                    // Validar antes de salvar
                    val rotaFormatada = formatarRota(rota)
                    val (vagaValida, mensagemVaga) = validarVagaNaOnda(ondas, ondaIndex, vaga, motorista.motoristaId)
                    val (rotaValida, mensagemRota) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
                    
                    erroVaga = mensagemVaga
                    erroRota = mensagemRota
                    
                    if (vagaValida && rotaValida) {
                        val sacasInt = sacas.toIntOrNull()
                        onSave(vaga, rotaFormatada, sacasInt)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextGray)
            }
        }
    )
}
