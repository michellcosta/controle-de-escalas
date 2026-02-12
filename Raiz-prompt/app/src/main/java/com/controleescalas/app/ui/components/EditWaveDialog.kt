package com.controleescalas.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccessTime // ✅ NOVO: Ícone de relógio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWaveDialog(
    onda: Onda,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit // nome, horario, tipo
) {
    var nome by remember { mutableStateOf(onda.nome) }
    var horario by remember { mutableStateOf(onda.horario) }
    var horarioIndefinido by remember { mutableStateOf(onda.horario.isBlank()) }
    // ✅ Salvar estado inicial da checkbox para restaurar ao cancelar
    val horarioIndefinidoInicial = remember { onda.horario.isBlank() }
    // ✅ NOVO: Estado para controlar TimePicker
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    // ✅ NOVO: Converter horário atual para hora/minuto se existir
    val (horaAtual, minutoAtual) = remember(horario) {
        if (horario.isNotBlank() && horario.matches(Regex("\\d{2}:\\d{2}"))) {
            val partes = horario.split(":")
            partes[0].toInt() to partes[1].toInt()
        } else {
            Calendar.getInstance().let { 
                it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE)
            }
        }
    }
    
    LaunchedEffect(horarioIndefinido) {
        if (horarioIndefinido) {
            horario = ""
        }
    }
    
    // ✅ NOVO: Quando horário é selecionado no TimePicker
    LaunchedEffect(selectedTime) {
        selectedTime?.let { (hora, minuto) ->
            horario = String.format("%02d:%02d", hora, minuto)
            horarioIndefinido = false
            showTimePicker = false
        }
    }
    
    // ✅ NOVO: TimePicker Dialog
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = horaAtual,
            initialMinute = minutoAtual,
            onTimeSelected = { hora, minuto ->
                selectedTime = hora to minuto
            },
            onDismiss = {
                showTimePicker = false
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Editar Onda", color = TextWhite)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nome da Onda
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome da Onda") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = TextGray,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = TextGray
                    )
                )
                
                // Horário (OPCIONAL) com ícone de relógio
                // ✅ Box clicável envolvendo o TextField para capturar todos os toques
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // ✅ Desmarcar checkbox automaticamente ao clicar no campo
                            horarioIndefinido = false
                            showTimePicker = true
                        }
                ) {
                    OutlinedTextField(
                        value = horario,
                        onValueChange = { 
                            horario = it
                            if (it.isNotBlank()) {
                                horarioIndefinido = false
                            }
                        },
                        label = { Text("Horário (HH:MM)") },
                        placeholder = { Text("indefinido") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false, // ✅ Desabilitar completamente para que o Box capture os toques
                        readOnly = true, // ✅ Tornar somente leitura
                        trailingIcon = {
                            // ✅ Ícone de relógio que também abre o TimePicker
                            IconButton(
                                onClick = { 
                                    // ✅ Desmarcar checkbox automaticamente ao clicar no ícone
                                    horarioIndefinido = false
                                    showTimePicker = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Selecionar horário",
                                    tint = if (horarioIndefinido) TextGray.copy(alpha = 0.5f) else NeonGreen
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = TextGray,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = TextGray,
                            disabledTextColor = TextGray,
                            disabledBorderColor = TextGray.copy(alpha = 0.5f),
                            disabledLabelColor = TextGray.copy(alpha = 0.7f)
                        )
                    )
                }
                
                // ✅ NOVO: Checkbox para definir horário como indefinido
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = horarioIndefinido,
                        onCheckedChange = { 
                            horarioIndefinido = it
                            if (it) {
                                horario = "" // Limpar horário quando marcado
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonGreen,
                            uncheckedColor = TextGray
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Horário indefinido",
                        color = TextWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    // ✅ Mudança: Apenas nome é obrigatório, horário é opcional
                    if (nome.isNotBlank()) {
                        // Se checkbox marcado, garantir que horário está vazio
                        val horarioFinal = if (horarioIndefinido) "" else horario
                        onSave(nome, horarioFinal, onda.tipo)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("SALVAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // ✅ Restaurar estado original da checkbox ao cancelar
                horarioIndefinido = horarioIndefinidoInicial
                horario = if (horarioIndefinidoInicial) "" else onda.horario
                nome = onda.nome
                onDismiss()
            }) {
                Text("CANCELAR", color = TextGray)
            }
        }
    )
}

// ✅ NOVO: Componente TimePickerDialog customizado
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true // Formato 24 horas
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Selecionar Horário", color = TextWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TimePicker do Material 3
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialSelectedContentColor = Color.Black,
                        clockDialColor = NeonGreen,
                        selectorColor = NeonGreen,
                        periodSelectorBorderColor = NeonGreen,
                        periodSelectorSelectedContainerColor = NeonGreen,
                        timeSelectorSelectedContainerColor = NeonGreen,
                        timeSelectorUnselectedContainerColor = DarkSurface,
                        timeSelectorSelectedContentColor = Color.Black,
                        timeSelectorUnselectedContentColor = TextWhite
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // ✅ Usar valores diretamente do timePickerState
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = TextGray)
            }
        }
    )
}
