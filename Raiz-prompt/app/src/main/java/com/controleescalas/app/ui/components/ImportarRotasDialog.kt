package com.controleescalas.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.PreEscalaViewModel
import com.controleescalas.app.utils.BrasilDate
import com.controleescalas.app.utils.OndaTableParser

/**
 * Importa a tabela de ondas (foto da planilha) por OCR:
 * lê "Onda | Rota otimizada | Rota original | Doca" e mostra a lista
 * para o admin conferir antes de aplicar.
 *
 * Aplicar reorganiza as ondas do TURNO SELECIONADO pela tabela: cada rota
 * entra na sua onda e vaga com o motorista já escalado para ela (pré-escala)
 * ou como slot LIVRE. O sufixo da rota (AM1 em I13_AM1) faz parte do código
 * da rota — não é turno.
 */
@Composable
fun ImportarRotasDialog(
    baseId: String,
    data: String,
    turno: String,
    viewModel: PreEscalaViewModel,
    // true = só extrai as rotas da foto para os chips (escala rápida);
    // false = cria ondas com slots no dia (operacoes do dia)
    modoSomenteRotas: Boolean = false,
    // Depois de aplicar: no modo "só rotas" recebe os códigos lidos; no modo
    // ondas recebe os turnos aplicados (sempre o selecionado)
    onAplicado: (aplicados: List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val linhasImportadas by viewModel.linhasImportadas.collectAsState()
    val rotasQrLidas by viewModel.rotasQrLidas.collectAsState()
    val isProcessingOcr by viewModel.isProcessingOcr.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val escalados by viewModel.escaladosPorRota.collectAsState()

    // Prévia do modo ondas: quem já está escalado em cada rota da tabela
    LaunchedEffect(baseId, data, turno, modoSomenteRotas) {
        if (!modoSomenteRotas) viewModel.carregarEscalados(baseId, data, turno)
    }

    val galeriaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = context.contentResolver.openInputStream(it)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
            if (bitmap != null) viewModel.processarImagemTabela(redimensionar(bitmap))
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
                        if (modoSomenteRotas) "Importar rotas" else "Importar ondas",
                        color = TextBlack,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (modoSomenteRotas) "Foto da planilha (QR ROTA)" else "Foto da tabela de ondas",
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Captura (somente galeria — a tabela vem por WhatsApp/planilha)
                Button(
                    onClick = { galeriaLauncher.launch("image/*") },
                    enabled = !isProcessingOcr,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeliBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Escolher foto da tabela")
                }

                if (isProcessingOcr) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = MeliBlue, modifier = Modifier.size(24.dp))
                        Text("Lendo a tabela...", color = TextGrayLightMode, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                error?.let { msg ->
                    Text(
                        msg,
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Prévia das rotas lidas
                if (modoSomenteRotas) {
                    // Colar texto da planilha (copia a coluna QR ROTA no Excel)
                    var textoColado by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = textoColado,
                        onValueChange = { textoColado = it },
                        label = { Text("Ou cole o texto da planilha (coluna QR ROTA)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors()
                    )
                    TextButton(
                        onClick = {
                            val rotas = OndaTableParser.regexQrRota.findAll(textoColado.uppercase())
                                .map { it.value }
                                .distinct()
                                .toList()
                            if (rotas.isNotEmpty()) viewModel.definirRotasQr(rotas)
                        },
                        enabled = textoColado.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Extrair do texto")
                    }

                    // Escala rápida: os chips guardam o código completo (H4_AM1),
                    // que é o que vai na mensagem e casa com a tabela de ondas
                    if (rotasQrLidas.isNotEmpty()) {
                        Text(
                            "Confira as rotas lidas (${rotasQrLidas.size}):",
                            color = TextBlack,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(rotasQrLidas.size) { index ->
                                val rota = rotasQrLidas[index]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        rota,
                                        color = TextBlack,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.removerRotaQr(index) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remover $rota",
                                            tint = StatusError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "✓ ${rotasQrLidas.size} rotas serão adicionadas aos chips do turno $turno.",
                            color = NeonGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        // Sufixo lido (AM1 em H4_AM1): parte do código da rota, não do turno
                        val sufixosLidos = rotasQrLidas
                            .map { it.substringAfter('_', "") }
                            .filter { it.isNotBlank() }
                            .distinct()
                        if (sufixosLidos.isNotEmpty()) {
                            Text(
                                "Sufixo das rotas lidas: ${sufixosLidos.joinToString(", ")}.",
                                color = TextGrayLightMode,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (linhasImportadas.isNotEmpty()) {
                    Text(
                        "Confira as rotas lidas (${linhasImportadas.size}):",
                        color = TextBlack,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    // Quem já está escalado em cada rota (pré-escala): vai direto
                    // para a onda e a vaga da tabela
                    val comMotorista = linhasImportadas.count { linha ->
                        escalados.keys.any { OndaTableParser.rotasCasa(linha.rota, it) }
                    }
                    if (comMotorista > 0) {
                        Text(
                            "✓ $comMotorista de ${linhasImportadas.size} rotas já têm motorista e vão para a onda e a vaga da tabela.",
                            color = NeonGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(linhasImportadas.size) { index ->
                            val linha = linhasImportadas[index]
                            val dono = escalados.entries
                                .firstOrNull { OndaTableParser.rotasCasa(linha.rota, it.key) }
                                ?.value
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append("Onda ${linha.onda ?: "—"} · ")
                                        append(linha.rota)
                                        linha.doca?.let { append(" · vaga ${it.toString().padStart(2, '0')}") }
                                        if (linha.onda == null) append(" · sem número de onda, não entra")
                                    },
                                    color = TextBlack,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (dono != null) {
                                    Text(
                                        "✓ $dono",
                                        color = NeonGreen,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(onClick = { viewModel.removerLinhaImportada(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remover ${linha.rota}",
                                        tint = StatusError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "⚠️ Aplicar reorganiza as ondas de ${BrasilDate.yyyyMmDdParaDdMmYyyy(data)} no turno $turno " +
                            "pela tabela: quem já tem rota vai para a onda e a vaga dela, e rota sem motorista fica livre.",
                        color = NeonOrange,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (modoSomenteRotas) {
                        // Só chips: TODAS as rotas QR lidas, com o código completo.
                        // Vão para o turno selecionado na tela — o sufixo da rota
                        // (AM1 em H4_AM1) é parte do código, não um turno
                        onAplicado(rotasQrLidas)
                        viewModel.limparRotasQr()
                        onDismiss()
                    } else {
                        val linhasParaAplicar = linhasImportadas
                        // onAplicado chega depois do save
                        viewModel.aplicarRotasImportadas(
                            baseId, data, turno, linhasParaAplicar,
                            onConcluido = { turnosAplicados -> onAplicado(turnosAplicados) }
                        )
                        viewModel.limparLinhasImportadas()
                        onDismiss()
                    }
                },
                enabled = (if (modoSomenteRotas) rotasQrLidas.isNotEmpty() else linhasImportadas.isNotEmpty()) && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeliBlue,
                    contentColor = Color.White
                )
            ) {
                val contagem = if (modoSomenteRotas) rotasQrLidas.size else linhasImportadas.size
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("APLICAR ($contagem)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.limparLinhasImportadas()
                onDismiss()
            }) {
                Text("CANCELAR", color = TextGrayLightMode)
            }
        }
    )
}

/**
 * Prepara a imagem para o OCR: planilhas têm texto pequeno, então AMPLIA
 * imagens estreitas (alvo ~3400px de largura); fotos de câmera grandes são
 * reduzidas para no máximo 4200px (evita OOM sem perder legibilidade).
 */
private fun redimensionar(bitmap: Bitmap): Bitmap {
    val larguraAlvo = 3400
    val maxLado = 4200
    val maior = maxOf(bitmap.width, bitmap.height)
    val escala = when {
        bitmap.width < larguraAlvo -> larguraAlvo.toFloat() / bitmap.width
        maior > maxLado -> maxLado.toFloat() / maior
        else -> 1f
    }
    if (escala == 1f) return bitmap
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * escala).toInt(),
        (bitmap.height * escala).toInt(),
        true
    )
}
