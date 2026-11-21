package com.controleescalas.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.ui.theme.*

/** Onda de pré-escala não é destino real nem entra na numeração das ondas */
private const val NOME_PRE_ESCALA = "PRÉ-ESCALA"

/**
 * Dialog de montagem rápida: seleção múltipla de motoristas com badge de
 * disponibilidade e três destinos possíveis:
 *  1. Todos na onda atual;
 *  2. Distribuição igual em N ondas novas (round-robin);
 *  3. Ondas diferentes — admin escolhe a onda de cada motorista por grupos
 *     (toca na onda alvo nos chips e depois nos motoristas).
 *
 * Passo 1: quem + destino → Passo 2: rota individual (opcional).
 * Vaga e horário ficam para a montagem posterior das ondas.
 */
@Composable
fun AddDriversMultiSelectDialog(
    motoristas: List<AdminMotoristaCardData>,
    disponibilidadeMap: Map<String, Boolean?>,
    ondaAtualIndex: Int,
    ondaAtualNome: String,
    motoristasJaEscalados: Set<String>,
    modalidadeFiltroHabilitada: Boolean = false,
    ondasExistentes: List<String> = emptyList(),
    rotasAtuais: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (
        selecionados: List<AdminMotoristaCardData>,
        destinoOndaIndex: Int,
        distribuirEm: Int?,
        rotas: Map<String, String>,
        destinosPorMotorista: Map<String, Int>
    ) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var busca by remember { mutableStateOf("") }
    var selecionados by remember { mutableStateOf(setOf<String>()) }
    var distribuir by remember { mutableStateOf(false) }
    // Modo padrão: "ondas diferentes" (escolher quem vai em cada onda)
    var ondasDiferentes by remember { mutableStateOf(true) }
    var numOndas by remember { mutableIntStateOf(2) }
    var rotas by remember { mutableStateOf(mapOf<String, String>()) }
    var modalidade by remember { mutableStateOf<String?>(null) }

    // Ondas reais = ondas do turno excluindo a PRÉ-ESCALA (que não é destino nem conta na numeração)
    val ondasReais = remember(ondasExistentes) {
        ondasExistentes.filter { !it.equals(NOME_PRE_ESCALA, ignoreCase = true) }
    }

    // Modo "ondas diferentes": onda alvo selecionada + atribuição por motorista.
    // Sem ondas reais ainda, a "1ª ONDA" já vem disponível como alvo.
    var numOndasNovas by remember { mutableIntStateOf(if (ondasReais.isEmpty()) 1 else 0) }
    var alvoSelecionado by remember { mutableIntStateOf(0) }
    val destinos: SnapshotStateMap<String, Int> = remember { mutableStateMapOf() }

    // Ordena por disponibilidade no turno (disponível primeiro) e nome
    val listaOrdenada = remember(motoristas, disponibilidadeMap) {
        motoristas.sortedWith(
            compareBy<AdminMotoristaCardData> {
                when (disponibilidadeMap[it.id]) {
                    true -> 0
                    null -> 1
                    false -> 2
                }
            }.thenBy { it.nome }
        )
    }

    // Filtro combinado: busca + modalidade (quando habilitado)
    val listaFiltrada = remember(listaOrdenada, busca, modalidade) {
        val porBusca = if (busca.isBlank()) listaOrdenada
        else listaOrdenada.filter {
            it.nome.contains(busca, ignoreCase = true) ||
                it.telefone.contains(busca, ignoreCase = true)
        }
        if (!modalidadeFiltroHabilitada || modalidade == null) porBusca
        else porBusca.filter { it.modalidade == modalidade }
    }

    // Ondas alvo do modo "ondas diferentes": reais (índice dentro das reais) + novas
    // A numeração ignora a PRÉ-ESCALA: primeira onda real = "1ª ONDA"
    val alvos = remember(ondasReais, numOndasNovas) {
        ondasReais.mapIndexed { i, nome -> i to nome } +
            (0 until numOndasNovas).map { k ->
                val idx = ondasReais.size + k
                idx to "${idx + 1}ª ONDA"
            }
    }

    // No modo "ondas diferentes", quem vai = quem tem onda atribuída
    val idsSelecionados = if (ondasDiferentes) destinos.keys else selecionados
    val selecionadosList = listaOrdenada.filter { it.id in idsSelecionados }

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
                        if (step == 1) "Adicionar Motoristas" else "Rota de cada motorista",
                        color = TextBlack,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (step == 1) "Passo 1 de 2 • Quem está escalado" else "Passo 2 de 2 • Opcional",
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
                StepSelecao(
                    listaFiltrada = listaFiltrada,
                    disponibilidadeMap = disponibilidadeMap,
                    motoristasJaEscalados = motoristasJaEscalados,
                    modalidadeFiltroHabilitada = modalidadeFiltroHabilitada,
                    modalidadeSelecionada = modalidade,
                    onModalidadeChange = { modalidade = it },
                    busca = busca,
                    onBuscaChange = { busca = it },
                    idsSelecionados = idsSelecionados,
                    onToggle = { id ->
                        if (ondasDiferentes) {
                            // Toca de novo no alvo atual = desmarca; outro alvo = move
                            if (destinos[id] == alvoSelecionado) destinos.remove(id)
                            else if (alvoSelecionado >= 0) destinos[id] = alvoSelecionado
                        } else {
                            selecionados = if (id in selecionados) selecionados - id else selecionados + id
                        }
                    },
                    onSelectAll = { selecionar ->
                        // Inclui também quem já está escalado (ao adicionar, o bulkApply move)
                        val ids = listaFiltrada.map { it.id }.toSet()
                        if (ondasDiferentes) {
                            if (alvoSelecionado >= 0) {
                                if (selecionar) ids.forEach { destinos[it] = alvoSelecionado }
                                else ids.forEach { if (destinos[it] == alvoSelecionado) destinos.remove(it) }
                            }
                        } else {
                            selecionados = if (selecionar) selecionados + ids else selecionados - ids
                        }
                    },
                    distribuir = distribuir,
                    onDistribuirChange = {
                        distribuir = it
                        if (it) {
                            ondasDiferentes = false
                            // Carrega quem já estava marcado para não perder a seleção
                            if (destinos.isEmpty() && selecionados.isNotEmpty()) {
                                selecionados.forEach { destinos[it] = 0 }
                            }
                        } else if (ondasDiferentes) {
                            // Saiu do modo manual pelo rádio "Na onda atual": mantém quem tinha onda
                            selecionados = destinos.keys.toSet()
                            ondasDiferentes = false
                        }
                    },
                    ondasDiferentes = ondasDiferentes,
                    onOndasDiferentesChange = {
                        ondasDiferentes = it
                        if (it) {
                            distribuir = false
                            if (destinos.isEmpty() && selecionados.isNotEmpty()) {
                                selecionados.forEach { destinos[it] = 0 }
                            }
                            if (alvoSelecionado < 0 && alvos.isNotEmpty()) alvoSelecionado = 0
                        } else {
                            // Sai do modo manual mantendo quem tem onda atribuída
                            selecionados = destinos.keys.toSet()
                        }
                    },
                    numOndas = numOndas,
                    onNumOndasChange = { numOndas = it },
                    ondaAtualNome = ondaAtualNome,
                    mostrarOndaAtual = ondasReais.isNotEmpty() &&
                        !ondaAtualNome.equals(NOME_PRE_ESCALA, ignoreCase = true),
                    alvos = alvos,
                    alvoSelecionado = alvoSelecionado,
                    onAlvoSelecionadoChange = { alvoSelecionado = it },
                    onNovaOnda = {
                        numOndasNovas++
                        alvoSelecionado = ondasExistentes.size + numOndasNovas - 1
                    },
                    destinos = destinos
                )
            } else {
                StepRotas(
                    selecionados = selecionadosList,
                    rotas = rotas,
                    onRotaChange = { id, rota -> rotas = rotas + (id to rota) },
                    destinos = if (ondasDiferentes) destinos.toMap() else emptyMap(),
                    nomesAlvos = alvos.toMap(),
                    rotasAtuais = rotasAtuais
                )
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    enabled = idsSelecionados.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeliBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text("CONTINUAR (${idsSelecionados.size})", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onConfirm(
                            selecionadosList,
                            ondaAtualIndex,
                            if (distribuir) numOndas else null,
                            rotas.filterValues { it.isNotBlank() },
                            if (ondasDiferentes) destinos.toMap() else emptyMap()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeliBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text("ADICIONAR ✓", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 2) step = 1 else onDismiss() }) {
                Text(
                    if (step == 2) "VOLTAR" else "CANCELAR",
                    color = TextGrayLightMode
                )
            }
        }
    )
}

@Composable
private fun StepSelecao(
    listaFiltrada: List<AdminMotoristaCardData>,
    disponibilidadeMap: Map<String, Boolean?>,
    motoristasJaEscalados: Set<String>,
    modalidadeFiltroHabilitada: Boolean,
    modalidadeSelecionada: String?,
    onModalidadeChange: (String?) -> Unit,
    busca: String,
    onBuscaChange: (String) -> Unit,
    idsSelecionados: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    distribuir: Boolean,
    onDistribuirChange: (Boolean) -> Unit,
    ondasDiferentes: Boolean,
    onOndasDiferentesChange: (Boolean) -> Unit,
    numOndas: Int,
    onNumOndasChange: (Int) -> Unit,
    ondaAtualNome: String,
    mostrarOndaAtual: Boolean,
    alvos: List<Pair<Int, String>>,
    alvoSelecionado: Int,
    onAlvoSelecionadoChange: (Int) -> Unit,
    onNovaOnda: () -> Unit,
    destinos: SnapshotStateMap<String, Int>
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Busca
        CustomTextField(
            value = busca,
            onValueChange = onBuscaChange,
            label = "Buscar motorista..."
        )

        // Filtro de modalidade (opcional, usado no dashboard)
        if (modalidadeFiltroHabilitada) {
            val chipFiltros = listOf(
                null to "Todos",
                "FROTA" to "Frota",
                "PASSEIO" to "Passeio",
                "DEDICADO" to "Dedicado",
                "UTILITARIO" to "Utilitário"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chipFiltros.forEach { (valor, label) ->
                    FilterChip(
                        selected = modalidadeSelecionada == valor,
                        onClick = { onModalidadeChange(valor) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeliBlue,
                            selectedLabelColor = Color.White,
                            containerColor = LightSurfaceVariant,
                            labelColor = TextBlack
                        )
                    )
                }
            }
        }

        // Lista de motoristas (mais compacta no modo "ondas diferentes")
        LazyColumn(
            modifier = Modifier.heightIn(max = if (ondasDiferentes) 200.dp else 260.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (listaFiltrada.isEmpty()) {
                item {
                    Text(
                        "Nenhum motorista encontrado",
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(listaFiltrada, key = { it.id }) { motorista ->
                MotoristaSelecaoRow(
                    motorista = motorista,
                    disponibilidade = disponibilidadeMap[motorista.id],
                    jaEscalado = motorista.id in motoristasJaEscalados,
                    selecionado = motorista.id in idsSelecionados,
                    ondaAtribuida = if (ondasDiferentes) {
                        destinos[motorista.id]?.let { idx -> alvos.firstOrNull { it.first == idx }?.second }
                    } else null,
                    onToggle = { onToggle(motorista.id) }
                )
            }
        }

        // Selecionar todos (respeita o filtro de busca; inclui já-escalados = mover)
        val selecionaveis = listaFiltrada
        val todosSelecionados = selecionaveis.isNotEmpty() && selecionaveis.all { it.id in idsSelecionados }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectAll(!todosSelecionados) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = todosSelecionados,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = MeliBlue)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Selecionar todos (${idsSelecionados.size} selecionados)",
                color = TextBlack,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Divider(color = CardBorderLight)

        // Destino
        Text(
            "Para onde vão os selecionados?",
            color = TextBlack,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // Ondas diferentes: opção padrão, primeiro na lista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOndasDiferentesChange(!ondasDiferentes) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = ondasDiferentes,
                onClick = { onOndasDiferentesChange(!ondasDiferentes) },
                colors = RadioButtonDefaults.colors(selectedColor = MeliBlue)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Ondas diferentes (escolho quem vai em cada uma)",
                color = TextBlack,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Chips de ondas alvo (visível só no modo "ondas diferentes")
        if (ondasDiferentes) {
            Text(
                "Toque na onda e depois nos motoristas:",
                color = TextGrayLightMode,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alvos.forEach { (idx, nome) ->
                    FilterChip(
                        selected = alvoSelecionado == idx,
                        onClick = { onAlvoSelecionadoChange(idx) },
                        label = { Text(nome, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeliBlue,
                            selectedLabelColor = Color.White,
                            containerColor = LightSurfaceVariant,
                            labelColor = TextBlack
                        )
                    )
                }
                AssistChip(
                    onClick = onNovaOnda,
                    label = { Text("+ Nova onda", style = MaterialTheme.typography.labelMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White,
                        labelColor = MeliBlue
                    )
                )
            }
        }

        // "Na onda atual" só faz sentido se já existirem ondas
        if (mostrarOndaAtual) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDistribuirChange(false) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !distribuir && !ondasDiferentes,
                    onClick = { onDistribuirChange(false) },
                    colors = RadioButtonDefaults.colors(selectedColor = MeliBlue)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Na onda atual: $ondaAtualNome", color = TextBlack, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDistribuirChange(true) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = distribuir,
                onClick = { onDistribuirChange(true) },
                colors = RadioButtonDefaults.colors(selectedColor = MeliBlue)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Distribuir igualmente em ondas novas",
                color = TextBlack,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Stepper de N ondas (visível só no modo distribuição)
        if (distribuir) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Nº de ondas:", color = TextGrayLightMode, style = MaterialTheme.typography.bodyMedium)
                FilledIconButton(
                    onClick = { if (numOndas > 1) onNumOndasChange(numOndas - 1) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LightSurfaceVariant,
                        contentColor = TextBlack
                    )
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Menos ondas")
                }
                Text(
                    "$numOndas",
                    color = MeliBlue,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                FilledIconButton(
                    onClick = { if (numOndas < 10) onNumOndasChange(numOndas + 1) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LightSurfaceVariant,
                        contentColor = TextBlack
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Mais ondas")
                }
            }
        }
    }
}

@Composable
private fun MotoristaSelecaoRow(
    motorista: AdminMotoristaCardData,
    disponibilidade: Boolean?,
    jaEscalado: Boolean,
    selecionado: Boolean,
    ondaAtribuida: String?,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selecionado,
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
                when {
                    jaEscalado -> "Já escalado — toque para mover"
                    disponibilidade == true -> "✓ Disponível neste turno"
                    disponibilidade == false -> "✕ Indisponível neste turno"
                    else -> "Sem resposta"
                },
                color = when {
                    jaEscalado -> TextGrayLightMode
                    disponibilidade == true -> NeonGreen
                    disponibilidade == false -> StatusError
                    else -> TextGrayLightMode
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        // Etiqueta da onda atribuída (modo "ondas diferentes")
        if (ondaAtribuida != null) {
            Surface(
                color = MeliBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    ondaAtribuida,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        // Indicador de disponibilidade em destaque
        if (disponibilidade == false && !jaEscalado) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = "Indisponível",
                tint = StatusError,
                modifier = Modifier.size(18.dp)
            )
        } else if (disponibilidade == true && !jaEscalado) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Disponível",
                tint = NeonGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StepRotas(
    selecionados: List<AdminMotoristaCardData>,
    rotas: Map<String, String>,
    onRotaChange: (String, String) -> Unit,
    destinos: Map<String, Int> = emptyMap(),
    nomesAlvos: Map<Int, String> = emptyMap(),
    rotasAtuais: Map<String, String> = emptyMap()
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Vaga e horário ficam para depois, na montagem das ondas.",
            color = TextGrayLightMode,
            style = MaterialTheme.typography.bodySmall
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(selecionados, key = { it.id }) { motorista ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            motorista.nome,
                            color = TextBlack,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        // Mostra a onda escolhida no modo "ondas diferentes"
                        destinos[motorista.id]?.let { idx ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MeliBlue,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    nomesAlvos[idx] ?: "${idx + 1}ª ONDA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Rota já vem preenchida se o motorista já tem rota (ex.: da pré-escala)
                    CustomTextField(
                        value = rotas[motorista.id]
                            ?: rotasAtuais[motorista.id]?.takeIf { it.isNotBlank() }
                            ?: "",
                        onValueChange = { onRotaChange(motorista.id, it) },
                        label = "Rota (opcional)"
                    )
                }
            }
        }
    }
}
