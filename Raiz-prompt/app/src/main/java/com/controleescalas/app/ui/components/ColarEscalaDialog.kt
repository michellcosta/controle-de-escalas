package com.controleescalas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleescalas.app.data.NotificationApiService
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.utils.ParserEscalaTexto
import com.controleescalas.app.utils.SomScan
import com.google.firebase.auth.FirebaseAuth
import com.controleescalas.app.data.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Cola a escala como TEXTO (mensagem do WhatsApp) e monta as ondas do turno.
 *
 * Dois analisadores:
 * - LOCAL (ParserEscalaTexto): instantâneo, para os formatos conhecidos
 *   (ondas com docas, planejamento com traços, lista numerada);
 * - IA (Assistente): entende variações e semântica (ex.: Volumosos pertencem
 *   às ondas 1, 2, 3). A prévia aparece antes de aplicar — nada substitui as
 *   ondas do turno sem o toque em APLICAR.
 */
@Composable
fun ColarEscalaDialog(
    baseId: String,
    turno: String,
    motoristas: List<AdminMotoristaCardData>,
    onAplicar: (List<Onda>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var ondasResultado by remember { mutableStateOf<List<Onda>?>(null) }
    var fonte by remember { mutableStateOf("") }
    var analisandoLocal by remember { mutableStateOf(false) }
    var analisandoIA by remember { mutableStateOf(false) }
    var erroIA by remember { mutableStateOf<String?>(null) }
    val ocupado = analisandoLocal || analisandoIA

    fun analisarLocal() {
        analisandoIA = false
        erroIA = null
        analisandoLocal = true
        fonte = "parser local"
        // pequeno delay para o spinner aparecer em textos grandes
        ondasResultado = ParserEscalaTexto.converterEmOndas(
            ParserEscalaTexto.parse(texto),
            motoristas
        )
        analisandoLocal = false
    }

    fun analisarIA() {
        analisandoIA = true
        erroIA = null
        fonte = "IA"
        ondasResultado = null
        scope.launch {
            try {
                val idToken = FirebaseAuth.getInstance()
                    .currentUser?.getIdToken(true)?.await()?.token
                if (idToken == null) {
                    erroIA = "Sessão expirada — feche e reabra o app para continuar."
                    return@launch
                }
                val sessao = SessionManager(context).getLastActiveSession()
                val prompt = "Monte a escala do turno $turno a partir do TEXTO abaixo, " +
                    "no formato exigido pela ferramenta definir_escala " +
                    "(todas as ondas e todos os itens, sem pular nenhum):\n\n$texto"
                val chat = NotificationApiService().chatWithAssistente(
                    baseId = baseId,
                    text = prompt,
                    base64Image = null,
                    idToken = idToken,
                    userName = sessao?.userName,
                    userRole = sessao?.userRole,
                    turno = turno
                )
                val acao = chat.definirEscalaAction
                if (acao == null || acao.ondas.isEmpty()) {
                    erroIA = "A IA não entendeu a escala nesse texto. " +
                        "Confira se a mensagem tem cabeçalhos de onda e tente o parser local."
                    return@launch
                }
                ondasResultado = acao.ondas.mapIndexed { indice, ondaDef ->
                    Onda(
                        nome = "${indice + 1}ª ONDA",
                        horario = if (indice == 0) acao.inicio.orEmpty() else "",
                        itens = ondaDef.itens.map { item ->
                            if (item.motoristaId != null) {
                                val info = motoristas.firstOrNull { it.id == item.motoristaId }
                                OndaItem(
                                    motoristaId = item.motoristaId!!,
                                    nome = info?.nome ?: item.motoristaNome.orEmpty(),
                                    vaga = item.vaga.orEmpty(),
                                    rota = item.rota.orEmpty(),
                                    modalidade = info?.modalidade ?: item.modalidade ?: "FROTA",
                                    nota = item.nota
                                )
                            } else {
                                OndaItem(
                                    motoristaId = "slot_" + UUID.randomUUID().toString(),
                                    vaga = item.vaga.orEmpty(),
                                    rota = item.rota.orEmpty(),
                                    nota = item.nota
                                )
                            }
                        }.sortedBy { it.vaga.toIntOrNull() ?: Int.MAX_VALUE }
                    )
                }
                fonte = "IA"
            } catch (e: Exception) {
                erroIA = "Falha ao analisar com IA: ${e.message}"
            } finally {
                analisandoIA = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!ocupado) onDismiss },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Colar escala (texto)", color = TextBlack, fontWeight = FontWeight.Bold)
                    Text(
                        "Cole a mensagem do WhatsApp do turno $turno",
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { if (!ocupado) onDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextGrayLightMode)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Cole o texto da escala aqui") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextBlack,
                        unfocusedTextColor = TextBlack,
                        focusedBorderColor = MeliBlue,
                        unfocusedBorderColor = CardBorderLight
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { analisarLocal() },
                        enabled = texto.isNotBlank() && !ocupado,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeliBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ANALISAR TEXTO", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { analisarIA() },
                        enabled = texto.isNotBlank() && !ocupado,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (analisandoIA) {
                            CircularProgressIndicator(
                                color = MeliBlue,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ANALISAR COM IA")
                        }
                    }
                }

                erroIA?.let { erro ->
                    Text(
                        erro,
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                ondasResultado?.let { ondas ->
                    val totalItens = ondas.sumOf { it.itens.size }
                    val totalLivres = ondas.sumOf { onda -> onda.itens.count { it.nome.isBlank() } }
                    Text(
                        buildString {
                            append("${ondas.size} ondas · $totalItens itens")
                            if (totalLivres > 0) append(" · $totalLivres sem motorista definido (LIVRE)")
                            append(" · via $fonte")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGrayLightMode
                    )

                    // Prévia das ondas
                    ondas.forEachIndexed { indice, onda ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LightSurfaceVariant, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "${onda.nome} · ${onda.itens.size} itens" +
                                    (if (onda.horario.isNotBlank()) " · ${onda.horario}" else ""),
                                color = TextBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            onda.itens.forEach { item ->
                                Text(
                                    buildString {
                                        append("${item.vaga.ifBlank { "--" }} · ${item.rota}")
                                        append(if (item.nome.isNotBlank()) " · ${item.nome}" else " · LIVRE")
                                        if (!item.nota.isNullOrBlank()) append(" (${item.nota})")
                                    },
                                    fontSize = 12.sp,
                                    color = if (item.nome.isBlank()) TextGrayLightMode else TextBlack,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    "Aplicar substitui as ondas de $turno. Motoristas são vinculados pelo nome; " +
                        "sem vínculo, a rota fica LIVRE para você completar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrayLightMode
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ondasResultado?.let { ondas ->
                        onAplicar(ondas)
                        SomScan.sucesso(context)
                    }
                    onDismiss()
                },
                enabled = ondasResultado != null && ondasResultado!!.any { it.itens.isNotEmpty() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeliBlue,
                    contentColor = Color.White
                )
            ) {
                Text("APLICAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!ocupado) onDismiss() }) {
                Text("CANCELAR", color = TextGrayLightMode)
            }
        }
    )
}
