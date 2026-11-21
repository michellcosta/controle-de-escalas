package com.controleescalas.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.PreEscalaViewModel
import com.controleescalas.app.utils.BrasilDate
import com.controleescalas.app.utils.WhatsAppShare
import java.util.Calendar

/**
 * Atribuição rápida por rota: motoristaId -> rota + nota
 */
data class RotaAtribuida(val rota: String, val nota: String = "")

/**
 * Pré-escala rápida: quem vai trabalhar no turno + rota, sem ondas.
 * Passo 1: escolhe a rota (chip) e toca nos motoristas.
 * Passo 2: horário do turno (opcional), notas e prévia → WhatsApp.
 * Ao compartilhar, salva na escala do dia (onda "PRÉ-ESCALA").
 */
@Composable
fun PreEscalaRapidaDialog(
    baseId: String,
    turno: String,
    dataPadrao: String,
    onDismiss: () -> Unit,
    viewModel: PreEscalaViewModel
) {
    val context = LocalContext.current
    val motoristas by viewModel.motoristas.collectAsState()
    val respostas by viewModel.disponibilidadeRespostas.collectAsState()
    val rotasRecentes by viewModel.rotasRecentes.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    var dataSelecionada by remember { mutableStateOf(dataPadrao) }
    var rotaSelecionada by remember { mutableStateOf<String?>(null) }
    var rotaDigitada by remember { mutableStateOf("") }
    var rotasExtras by remember { mutableStateOf(listOf<String>()) }
    val atribuicoes: SnapshotStateMap<String, RotaAtribuida> = remember { mutableStateMapOf() }
    var horario by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.carregar(baseId, dataPadrao) }

    // Prefill ao abrir e ao TROCAR O DIA: carrega a pré-escala salva da data
    // selecionada. Só quando não há atribuições em andamento (não apaga trabalho).
    LaunchedEffect(dataSelecionada) {
        if (atribuicoes.isEmpty()) {
            val salva = viewModel.carregarPreEscalaSalva(baseId, dataSelecionada, turno)
            if (salva != null) {
                if (salva.horario.isNotBlank()) horario = salva.horario
                val novas = mutableListOf<String>()
                salva.itens.forEach { item ->
                    if (item.motoristaId.isNotBlank() && item.rota.isNotBlank()) {
                        atribuicoes[item.motoristaId] = RotaAtribuida(item.rota, item.nota ?: "")
                        val r = item.rota.uppercase()
                        if (r !in rotasExtras) novas.add(r)
                    }
                }
                if (novas.isNotEmpty()) rotasExtras = rotasExtras + novas
            }
        }
    }

    // Trocar a data recarrega os badges de disponibilidade
    fun selecionarData(data: String) {
        dataSelecionada = data
        viewModel.selecionarData(baseId, data)
    }

    // Ordena por disponibilidade no turno (disponível primeiro) e nome
    val listaOrdenada = remember(motoristas, respostas, turno) {
        motoristas.sortedWith(
            compareBy<AdminMotoristaCardData> {
                when (respostas[it.id]?.estadoNoTurno(turno)) {
                    true -> 0
                    null -> 1
                    false -> 2
                }
            }.thenBy { it.nome }
        )
    }
    val motoristaPorId = remember(motoristas) { motoristas.associateBy { it.id } }

    val todasRotas = remember(rotasRecentes, rotasExtras) {
        (rotasRecentes + rotasExtras).distinct()
    }
    val atribuidos = listaOrdenada.filter { it.id in atribuicoes }

    // Rotas já atribuídas a algum motorista (chips ficam desabilitados)
    val rotasEmUso = remember(atribuicoes.size) {
        atribuicoes.values.map { it.rota.uppercase() }.toSet()
    }

    // Rota -> primeiro nome do dono (para mostrar no chip desabilitado).
    // distinctBy mantém o PRIMEIRO dono mesmo com dados antigos duplicados.
    val donosPorRota = remember(atribuicoes.size, motoristas) {
        atribuicoes.entries
            .distinctBy { it.value.rota.uppercase() }
            .associate { (id, a) ->
                a.rota.uppercase() to (motoristaPorId[id]?.nome?.trim()?.substringBefore(" ") ?: "")
            }
    }

    fun adicionarRotaDigitada() {
        val rota = rotaDigitada.trim().uppercase()
        if (rota.isBlank()) return
        if (rota !in todasRotas) rotasExtras = rotasExtras + rota
        // Só seleciona se a rota não estiver já ocupada por outro motorista
        if (rota !in rotasEmUso) rotaSelecionada = rota
        rotaDigitada = ""
    }

    fun toggleAtribuicao(id: String) {
        // Motorista já atribuído: toque remove (o chip da rota dele está desabilitado)
        if (atribuicoes.containsKey(id)) {
            atribuicoes.remove(id)
            return
        }
        val rota = rotaSelecionada ?: return
        // Segurança: rota ocupada por outro motorista não recebe segundo dono
        if (atribuicoes.values.any { it.rota.uppercase() == rota.uppercase() }) return
        atribuicoes[id] = RotaAtribuida(rota, "")
    }

    fun gerarMensagem(): String {
        val sb = StringBuilder("📢 ESCALA $turno")
        if (horario.isNotBlank()) sb.append(" - ").append(horario)

        // Grupos por modalidade, na ordem: 🚐 Frota → 🛻 Utilitário → 🚗 Passeio → 🚛 Dedicado (VUC)
        val grupos = listOf(
            "🚐" to setOf("FROTA"),
            "🛻" to setOf("UTILITARIO"),
            "🚗" to setOf("PASSEIO"),
            "🚛" to setOf("DEDICADO")
        )
        grupos.forEach { (emoji, modalidades) ->
            val doGrupo = atribuidos.filter { m ->
                val mod = m.modalidade.uppercase()
                if (modalidades.contains("FROTA")) {
                    // Grupo padrão: Frota e qualquer modalidade não reconhecida
                    mod !in setOf("DEDICADO", "PASSEIO", "UTILITARIO")
                } else {
                    mod in modalidades
                }
            }
            if (doGrupo.isNotEmpty()) {
                sb.append("\n\n").append(emoji)
                doGrupo.forEach { m ->
                    val a = atribuicoes[m.id] ?: return@forEach
                    sb.append("\n• ").append(m.nome.trim()).append(" → ")
                        .append(a.rota.uppercase().trim()).append("_").append(turno)
                    if (a.nota.isNotBlank()) sb.append(" (").append(a.nota.trim()).append(")")
                }
            }
        }
        return sb.toString()
    }

    fun confirmar(compartilhar: Boolean) {
        val itens = atribuidos.mapNotNull { m ->
            val a = atribuicoes[m.id] ?: return@mapNotNull null
            OndaItem(
                motoristaId = m.id,
                nome = m.nome,
                rota = a.rota.uppercase().trim(),
                vaga = "", // Vaga fica para a montagem das ondas
                horario = "",
                modalidade = m.modalidade,
                nota = a.nota.trim().ifBlank { null }
            )
        }
        viewModel.salvarPreEscala(baseId, dataSelecionada, turno, horario, itens)
        if (compartilhar) {
            WhatsAppShare.compartilharTextoNoWhatsApp(context, gerarMensagem())
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Escala rápida — $turno",
                        color = TextBlack,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (step == 1) "Passo 1 de 2 • Quem vai trabalhar (${BrasilDate.yyyyMmDdParaDdMmYyyy(dataSelecionada)})"
                        else "Passo 2 de 2 • Revisar e compartilhar",
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextGrayLightMode)
                }
            }
        },
        text = {
            if (step == 1) {
                StepAtribuicao(
                    listaOrdenada = listaOrdenada,
                    respostas = respostas,
                    turno = turno,
                    dataSelecionada = dataSelecionada,
                    onSelecionarData = ::selecionarData,
                    todasRotas = todasRotas,
                    rotaSelecionada = rotaSelecionada,
                    onSelecionarRota = { rotaSelecionada = it },
                    rotaDigitada = rotaDigitada,
                    onRotaDigitadaChange = { rotaDigitada = it },
                    onAdicionarRota = ::adicionarRotaDigitada,
                    rotasEmUso = rotasEmUso,
                    donosPorRota = donosPorRota,
                    atribuicoes = atribuicoes,
                    onToggle = ::toggleAtribuicao,
                    isLoading = motoristas.isEmpty()
                )
            } else {
                StepRevisao(
                    atribuidos = atribuidos,
                    atribuicoes = atribuicoes,
                    turno = turno,
                    horario = horario,
                    onHorarioChange = { horario = it },
                    showTimePicker = showTimePicker,
                    onShowTimePickerChange = { showTimePicker = it },
                    onNotaChange = { id, nota ->
                        val atual = atribuicoes[id] ?: return@StepRevisao
                        atribuicoes[id] = atual.copy(nota = nota)
                    },
                    mensagem = gerarMensagem()
                )
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    enabled = atribuicoes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeliBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text("CONTINUAR (${atribuicoes.size})", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { confirmar(compartilhar = true) },
                    enabled = !isSaving && atribuicoes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeliBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSaving) "SALVANDO..." else "COMPARTILHAR", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (step == 2) {
                TextButton(
                    onClick = { confirmar(compartilhar = false) },
                    enabled = !isSaving
                ) {
                    Text("SALVAR SÓ", color = TextGrayLightMode)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR", color = TextGrayLightMode)
                }
            }
        }
    )

    // TimePicker do horário do turno (reaproveita o componente existente)
    if (showTimePicker) {
        val (horaInicial, minutoInicial) = if (horario.matches(Regex("\\d{2}:\\d{2}"))) {
            val p = horario.split(":")
            (p[0].toIntOrNull() ?: 5) to (p[1].toIntOrNull() ?: 50)
        } else {
            Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }
        }
        TimePickerDialog(
            initialHour = horaInicial,
            initialMinute = minutoInicial,
            onTimeSelected = { h, m ->
                horario = String.format("%02d:%02d", h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun StepAtribuicao(
    listaOrdenada: List<AdminMotoristaCardData>,
    respostas: Map<String, com.controleescalas.app.data.models.DisponibilidadeMotorista>,
    turno: String,
    dataSelecionada: String,
    onSelecionarData: (String) -> Unit,
    todasRotas: List<String>,
    rotaSelecionada: String?,
    onSelecionarRota: (String?) -> Unit,
    rotaDigitada: String,
    onRotaDigitadaChange: (String) -> Unit,
    onAdicionarRota: () -> Unit,
    rotasEmUso: Set<String>,
    donosPorRota: Map<String, String>,
    atribuicoes: SnapshotStateMap<String, RotaAtribuida>,
    onToggle: (String) -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Data da pré-escala (a disponibilidade exibida é de amanhã)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Para o dia:", color = TextBlack, style = MaterialTheme.typography.bodyMedium)
            FilterChip(
                selected = dataSelecionada == BrasilDate.hoje(),
                onClick = { onSelecionarData(BrasilDate.hoje()) },
                label = { Text("Hoje", style = MaterialTheme.typography.labelMedium) },
                colors = chipColors()
            )
            FilterChip(
                selected = dataSelecionada == BrasilDate.amanha(),
                onClick = { onSelecionarData(BrasilDate.amanha()) },
                label = { Text("Amanhã", style = MaterialTheme.typography.labelMedium) },
                colors = chipColors()
            )
        }

        // Chips de rotas recentes + campo livre
        Text(
            "Toque numa rota e depois nos motoristas:",
            color = TextGrayLightMode,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (todasRotas.isEmpty() && rotaDigitada.isBlank()) {
                Text(
                    "Nenhuma rota recente — digite abaixo",
                    color = TextDisabled,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            todasRotas.forEach { rota ->
                val ocupada = rota.uppercase() in rotasEmUso
                FilterChip(
                    selected = rotaSelecionada == rota,
                    onClick = { onSelecionarRota(if (rotaSelecionada == rota) null else rota) },
                    enabled = !ocupada,
                    label = {
                        val dono = donosPorRota[rota.uppercase()]
                        Text(
                            if (dono.isNullOrBlank()) rota else "$rota • $dono",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = chipColors()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = rotaDigitada,
                onValueChange = { onRotaDigitadaChange(it.uppercase()) },
                label = { Text("Nova rota") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                modifier = Modifier.weight(1f),
                colors = textFieldColors()
            )
            FilledIconButton(
                onClick = onAdicionarRota,
                enabled = rotaDigitada.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MeliBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "Adicionar rota")
            }
        }

        Divider(color = CardBorderLight)

        // Motoristas
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MeliBlue)
            }
        } else {
            listaOrdenada.forEach { motorista ->
                val atribuida = atribuicoes[motorista.id]
                MotoristaRotaRow(
                    motorista = motorista,
                    disponibilidade = respostas[motorista.id]?.estadoNoTurno(turno),
                    rotaAtribuida = atribuida?.rota,
                    onToggle = { onToggle(motorista.id) }
                )
            }
        }
    }
}

@Composable
private fun MotoristaRotaRow(
    motorista: AdminMotoristaCardData,
    disponibilidade: Boolean?,
    rotaAtribuida: String?,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                motorista.nome,
                color = TextBlack,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (disponibilidade) {
                    true -> "✓ Disponível neste turno"
                    false -> "✕ Indisponível neste turno"
                    null -> "Sem resposta"
                },
                color = when (disponibilidade) {
                    true -> NeonGreen
                    false -> StatusError
                    else -> TextGrayLightMode
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (rotaAtribuida != null) {
            Surface(
                color = MeliBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    rotaAtribuida,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = TextDisabled.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StepRevisao(
    atribuidos: List<AdminMotoristaCardData>,
    atribuicoes: SnapshotStateMap<String, RotaAtribuida>,
    turno: String,
    horario: String,
    onHorarioChange: (String) -> Unit,
    showTimePicker: Boolean,
    onShowTimePickerChange: (Boolean) -> Unit,
    onNotaChange: (String, String) -> Unit,
    mensagem: String
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Horário do turno (opcional)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowTimePickerChange(true) }
        ) {
            OutlinedTextField(
                value = horario,
                onValueChange = onHorarioChange,
                label = { Text("Horário do turno (opcional)") },
                placeholder = { Text("indefinido") },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
        }

        // Notas por motorista
        atribuidos.forEach { m ->
            val a = atribuicoes[m.id] ?: return@forEach
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.nome,
                        color = TextBlack,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MeliBlue,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${a.rota}_$turno",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = a.nota,
                    onValueChange = { onNotaChange(m.id, it) },
                    label = { Text("Observação (opcional) — ex: vuc, saca") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
            }
        }

        // Prévia da mensagem
        Text(
            "Prévia da mensagem:",
            color = TextGrayLightMode,
            style = MaterialTheme.typography.bodySmall
        )
        Surface(
            color = LightSurfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                mensagem,
                color = TextBlack,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MeliBlue,
    selectedLabelColor = Color.White,
    containerColor = LightSurfaceVariant,
    labelColor = TextBlack
)

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextBlack,
    unfocusedTextColor = TextBlack,
    focusedBorderColor = MeliBlue,
    unfocusedBorderColor = CardBorderLight,
    focusedLabelColor = MeliBlue,
    unfocusedLabelColor = TextGrayLightMode,
    disabledTextColor = TextGrayLightMode,
    disabledBorderColor = CardBorderLight,
    disabledLabelColor = TextGrayLightMode.copy(alpha = 0.7f)
)
