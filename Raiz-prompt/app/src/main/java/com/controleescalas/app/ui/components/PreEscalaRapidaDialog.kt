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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.isSlot
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.PreEscalaViewModel
import com.controleescalas.app.utils.BrasilDate
import com.controleescalas.app.utils.WhatsAppShare
import java.util.Calendar

/**
 * Atribuição do fluxo rápido: motoristaId -> rota (+ nota)
 */
data class RotaAtribuida(val rota: String, val nota: String = "")

/**
 * Escala rápida (véspera): rota de cada motorista em uma tela única.
 *
 * - Chips de rota: preenchidos pela foto do QR ROTA (import, com o código
 *   completo, ex.: H4_AM1) ou digitados;
 * - Admin seleciona o chip da rota e toca nos motoristas (badge azul); a rota
 *   atribuída passa a ser a próxima sem dono, para escalar com 1 toque por
 *   motorista;
 * - Horário do turno opcional; ao salvar/compartilhar, quem tiver rota
 *   igual a um slot da importação preenche o slot direto (mantendo vaga),
 *   os demais vão para a onda "PRÉ-ESCALA".
 * O turno é o selecionado na tela de Disponibilidade — o import não troca.
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
    val isSaving by viewModel.isSaving.collectAsState()

    val turnoEmUso = turno
    var dataSelecionada by remember { mutableStateOf(dataPadrao) }
    var busca by remember { mutableStateOf("") }
    var rotaSelecionada by remember { mutableStateOf<String?>(null) }
    var rotaDigitada by remember { mutableStateOf("") }
    val atribuicoes: SnapshotStateMap<String, RotaAtribuida> = remember { mutableStateMapOf() }
    var horario by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var showImportar by remember { mutableStateOf(false) }

    // Chips de rota desta tela: preenchidos pela foto do QR ROTA (import, com o
    // código completo) ou digitados. Guardam o código completo (H4_AM1) — o
    // rótulo mostra só o prefixo (H4) enquanto não houver prefixo repetido.
    val chipsDeRota = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { viewModel.carregar(baseId, dataPadrao) }

    /** Próximo chip sem dono a partir de [rota] (null = todas atribuídas) */
    fun proximaRotaLivreApos(rota: String?): String? {
        val emUso = atribuicoes.values.map { it.rota.trim().uppercase() }.toSet()
        val total = chipsDeRota.size
        if (total == 0) return null
        val inicio = rota?.let { r ->
            chipsDeRota.indexOfFirst { it.trim().uppercase() == r.trim().uppercase() }
        } ?: -1
        for (i in 1..total) {
            val cand = chipsDeRota[(inicio + i) % total]
            if (cand.trim().uppercase() !in emUso) return cand
        }
        return null
    }

    // Prefill ao abrir e ao trocar o dia: carrega a pré-escala salva da data
    // (só quando não há atribuições em andamento, para não apagar trabalho).
    // Os chips das rotas salvas voltam junto e a primeira sem dono já fica selecionada.
    LaunchedEffect(dataSelecionada) {
        if (atribuicoes.isEmpty()) {
            val salva = viewModel.carregarPreEscalaSalva(baseId, dataSelecionada, turnoEmUso)
            if (salva != null) {
                if (salva.horario.isNotBlank()) horario = salva.horario
                salva.itens.forEach { item ->
                    if (item.motoristaId.isNotBlank() && !item.isSlot && item.rota.isNotBlank()) {
                        atribuicoes[item.motoristaId] = RotaAtribuida(item.rota, item.nota ?: "")
                        val rotaChip = item.rota.trim().uppercase()
                        if (rotaChip !in chipsDeRota) chipsDeRota.add(rotaChip)
                    }
                }
                rotaSelecionada = proximaRotaLivreApos(null)
            }
        }
    }

    fun selecionarData(data: String) {
        dataSelecionada = data
        viewModel.selecionarData(baseId, data)
    }

    fun toggleAtribuicao(id: String) {
        // Motorista já tem rota: toque remove (independe da rota selecionada)
        if (atribuicoes.containsKey(id)) {
            atribuicoes.remove(id)
            return
        }
        val rota = rotaSelecionada ?: return
        // 1 rota = 1 motorista: se a rota já tem dono, o toque é ignorado
        if (atribuicoes.values.any { it.rota.trim().uppercase() == rota.trim().uppercase() }) {
            rotaSelecionada = null
            return
        }
        atribuicoes[id] = RotaAtribuida(rota, "")
        // A rota acabou de ganhar dono — já arma a próxima sem dono, para escalar
        // a lista com 1 toque por motorista (sem voltar aos chips)
        rotaSelecionada = proximaRotaLivreApos(rota)
    }

    fun adicionarRotaDigitada() {
        val rota = rotaDigitada.trim().uppercase()
        if (rota.isBlank()) return
        if (rota !in chipsDeRota) chipsDeRota.add(rota)
        // Rota que já tem dono não volta para a seleção (o chip já mostra o nome dele)
        val temDono = atribuicoes.values.any { it.rota.trim().uppercase() == rota }
        if (!temDono) rotaSelecionada = rota
        rotaDigitada = ""
    }

    // Rotas já atribuídas ficam com chip desabilitado mostrando o dono
    // (sem remember: leitura de SnapshotStateMap já dispara recomposição)
    val rotasEmUso = atribuicoes.values.map { it.rota.trim().uppercase() }.toSet()
    val donosPorRota = atribuicoes.entries
        .distinctBy { it.value.rota.uppercase() }
        .associate { (id, a) ->
            a.rota.uppercase() to (motoristas.firstOrNull { it.id == id }?.nome?.trim()?.substringBefore(" ") ?: "")
        }

    // Rótulo do chip e do badge: só o prefixo (H4) enquanto os prefixos não se
    // repetem; com dois códigos de mesmo prefixo (I13_AM1 e I13_PM1), o código completo
    val rotulosComCodigoCompleto =
        chipsDeRota.map { it.substringBefore('_') }.distinct().size != chipsDeRota.size
    fun rotulo(rota: String): String =
        if (rotulosComCodigoCompleto) rota else rota.substringBefore('_')

    // Rotas duplicadas (entre as preenchidas) bloqueiam o compartilhar/salvar
    val temDuplicatas = atribuicoes.values
        .map { it.rota.trim().uppercase() }
        .filter { it.isNotBlank() }
        .groupBy { it }
        .any { it.value.size > 1 }

    val listaOrdenada = remember(motoristas, respostas, turnoEmUso) {
        motoristas.sortedWith(
            compareBy<AdminMotoristaCardData> {
                when (respostas[it.id]?.estadoNoTurno(turnoEmUso)) {
                    true -> 0
                    null -> 1
                    false -> 2
                }
            }.thenBy { it.nome }
        )
    }

    fun gerarMensagem(): String {
        val sb = StringBuilder("📢 ESCALA $turnoEmUso")
        if (horario.isNotBlank()) sb.append(" - ").append(horario)

        // Grupos por modalidade, na ordem: 🚐 Frota → 🛻 Utilitário → 🚗 Passeio → 🚛 Dedicado (VUC)
        val grupos = listOf(
            "🚐" to setOf("FROTA"),
            "🛻" to setOf("UTILITARIO"),
            "🚗" to setOf("PASSEIO"),
            "🚛" to setOf("DEDICADO")
        )
        grupos.forEach { (emoji, modalidades) ->
            val doGrupo = listaOrdenada.filter { m ->
                val mod = m.modalidade.uppercase()
                if (modalidades.contains("FROTA")) {
                    mod !in setOf("DEDICADO", "PASSEIO", "UTILITARIO")
                } else {
                    mod in modalidades
                }
            }.filter { it.id in atribuicoes }
            if (doGrupo.isNotEmpty()) {
                sb.append("\n\n").append(emoji)
                doGrupo.forEach { m ->
                    val a = atribuicoes[m.id] ?: return@forEach
                    sb.append("\n• ").append(m.nome.trim())
                    val rota = a.rota.trim().uppercase()
                    if (rota.isNotBlank()) {
                        // Rota da planilha já traz o código completo (H4_AM1); rota
                        // digitada curta recebe o turno, como sempre recebeu
                        val rotaFinal = if (rota.contains('_')) rota else "${rota}_$turnoEmUso"
                        sb.append(" → ").append(rotaFinal)
                    }
                    if (a.nota.isNotBlank()) sb.append(" (").append(a.nota.trim()).append(")")
                }
            }
        }
        return sb.toString()
    }

    // Erro do salvar mostrado dentro do dialog (o snackbar fica atrás dele)
    var erroAoSalvar by remember { mutableStateOf<String?>(null) }

    fun confirmar(compartilhar: Boolean) {
        erroAoSalvar = null
        val itens = atribuicoes.mapNotNull { (id, a) ->
            val m = listaOrdenada.firstOrNull { it.id == id } ?: return@mapNotNull null
            OndaItem(
                motoristaId = m.id,
                nome = m.nome,
                rota = a.rota.trim().uppercase(),
                vaga = "", // Vaga fica para a montagem das ondas
                horario = "",
                modalidade = m.modalidade,
                nota = a.nota.trim().ifBlank { null }
            )
        }
        // O WhatsApp só sai depois de salvar; se o salvar falhar, o dialog
        // continua aberto para tentar de novo
        viewModel.salvarPreEscala(baseId, dataSelecionada, turnoEmUso, horario, itens) { salvo ->
            if (salvo) {
                if (compartilhar) {
                    WhatsAppShare.compartilharTextoNoWhatsApp(context, gerarMensagem())
                }
                onDismiss()
            } else {
                erroAoSalvar = "Não foi possível salvar a pré-escala. Verifique a conexão e tente de novo."
            }
        }
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
                        "Escala rápida — $turnoEmUso",
                        color = TextBlack,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Quem vai trabalhar (${BrasilDate.yyyyMmDdParaDdMmYyyy(dataSelecionada)})",
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Para o dia:", color = TextBlack, style = MaterialTheme.typography.bodyMedium)
                    FilterChip(
                        selected = dataSelecionada == BrasilDate.hoje(),
                        onClick = { selecionarData(BrasilDate.hoje()) },
                        label = { Text("Hoje", style = MaterialTheme.typography.labelMedium) },
                        colors = chipColors()
                    )
                    FilterChip(
                        selected = dataSelecionada == BrasilDate.amanha(),
                        onClick = { selecionarData(BrasilDate.amanha()) },
                        label = { Text("Amanhã", style = MaterialTheme.typography.labelMedium) },
                        colors = chipColors()
                    )
                }

                // Chips de rotas (importadas via QR ROTA ou digitadas)
                Text(
                    "Toque numa rota e nos motoristas — a próxima rota livre já fica selecionada:",
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
                    if (chipsDeRota.isEmpty()) {
                        Text(
                            "Sem rotas — importe a planilha ou digite abaixo",
                            color = TextDisabled,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    chipsDeRota.forEach { rota ->
                        val ocupada = rota.uppercase() in rotasEmUso
                        FilterChip(
                            selected = rotaSelecionada == rota,
                            onClick = { rotaSelecionada = if (rotaSelecionada == rota) null else rota },
                            enabled = !ocupada,
                            label = {
                                val dono = donosPorRota[rota.uppercase()]
                                Text(
                                    if (dono.isNullOrBlank()) rotulo(rota) else "${rotulo(rota)} • $dono",
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
                        onValueChange = { rotaDigitada = it.uppercase() },
                        label = { Text("Nova rota") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors()
                    )
                    FilledIconButton(
                        onClick = ::adicionarRotaDigitada,
                        enabled = rotaDigitada.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MeliBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Adicionar rota")
                    }
                }

                // Import da planilha por foto (QR ROTA) — só preenche os chips
                OutlinedButton(
                    onClick = { showImportar = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📷 Importar rotas (QR ROTA da planilha)")
                }

                Divider(color = CardBorderLight)

                // Motoristas
                if (motoristas.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MeliBlue)
                    }
                } else {
                    // Campo de pesquisa do motorista
                    CustomTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        label = "Buscar motorista..."
                    )

                    val listaFiltrada = if (busca.isBlank()) listaOrdenada
                    else listaOrdenada.filter {
                        it.nome.contains(busca, ignoreCase = true) ||
                            it.telefone.contains(busca, ignoreCase = true)
                    }

                    listaFiltrada.forEach { motorista ->
                        val atribuida = atribuicoes[motorista.id]
                        MotoristaRotaRow(
                            motorista = motorista,
                            disponibilidade = respostas[motorista.id]?.estadoNoTurno(turnoEmUso),
                            rotaAtribuida = atribuida?.rota?.let { rotulo(it) },
                            notaAtribuida = atribuida?.nota ?: "",
                            onNotaChange = { nota ->
                                val atual = atribuicoes[motorista.id]
                                if (atual != null) atribuicoes[motorista.id] = atual.copy(nota = nota)
                            },
                            onToggle = { toggleAtribuicao(motorista.id) }
                        )
                    }

                    // Resumo. Não há "selecionar todos": cada rota é de um só
                    // motorista, então o controle só atribuía a rota ao primeiro
                    val rotasSemDono = chipsDeRota.count { it.uppercase() !in rotasEmUso }
                    Text(
                        "${atribuicoes.size} incluídos · $rotasSemDono rotas sem motorista",
                        color = TextBlack,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Horário do turno (opcional)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                ) {
                    OutlinedTextField(
                        value = horario,
                        onValueChange = { horario = it },
                        label = { Text("Horário do turno (opcional)") },
                        placeholder = { Text("indefinido") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors()
                    )
                }

                // Aviso de rotas duplicadas
                if (temDuplicatas) {
                    Text(
                        "Há rotas repetidas nas atribuições — corrija antes de compartilhar.",
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
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
                        gerarMensagem(),
                        color = TextBlack,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Erro do salvar (o dialog continua aberto para tentar de novo)
                erroAoSalvar?.let { msg ->
                    Text(
                        msg,
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { confirmar(compartilhar = true) },
                enabled = !isSaving && atribuicoes.isNotEmpty() && !temDuplicatas,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeliBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "SALVANDO..." else "COMPARTILHAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { confirmar(compartilhar = false) },
                enabled = !isSaving && !temDuplicatas
            ) {
                Text("SALVAR SÓ", color = TextGrayLightMode)
            }
        }
    )

    // Import da planilha por foto (QR ROTA) — só preenche os chips
    if (showImportar) {
        ImportarRotasDialog(
            baseId = baseId,
            data = dataSelecionada,
            turno = turnoEmUso,
            viewModel = viewModel,
            modoSomenteRotas = true,
            onAplicado = { rotas ->
                // Rotas da foto trazem o código completo (H4_AM1) — viram chips direto
                rotas.distinct().forEach { if (it !in chipsDeRota) chipsDeRota.add(it) }
                // Já seleciona a primeira rota que ainda não tem dono
                rotaSelecionada = proximaRotaLivreApos(null)
            },
            onDismiss = { showImportar = false }
        )
    }

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
private fun MotoristaRotaRow(
    motorista: AdminMotoristaCardData,
    disponibilidade: Boolean?,
    rotaAtribuida: String?,
    notaAtribuida: String,
    onNotaChange: (String) -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = rotaAtribuida != null,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MeliBlue)
        )
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
                    else -> "Sem resposta"
                },
                color = when (disponibilidade) {
                    true -> NeonGreen
                    false -> StatusError
                    else -> TextGrayLightMode
                },
                style = MaterialTheme.typography.bodySmall
            )
            // Observação opcional — só aparece quando o motorista tem rota
            if (rotaAtribuida != null) {
                OutlinedTextField(
                    value = notaAtribuida,
                    onValueChange = onNotaChange,
                    label = { Text("Obs. (opcional) — ex: vuc, saca") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = textFieldColors()
                )
            }
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
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MeliBlue,
    selectedLabelColor = Color.White,
    containerColor = LightSurfaceVariant,
    labelColor = TextBlack
)

@Composable
internal fun textFieldColors() = OutlinedTextFieldDefaults.colors(
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
