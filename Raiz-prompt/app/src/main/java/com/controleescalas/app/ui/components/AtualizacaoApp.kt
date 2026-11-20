package com.controleescalas.app.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleescalas.app.data.AppUpdateChecker
import com.controleescalas.app.data.AtualizacaoRemota
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Dialog "Nova Atualização Disponível": mostra as novidades, baixa o APK da
 * release do GitHub (com barra de progresso) e abre o instalador.
 *
 * Portado do MainActivity para ser reutilizado em dois lugares: o aviso
 * automático de abertura e o botão manual "Buscar atualização" (Sobre o App /
 * Configurações).
 */
@Composable
fun DialogAtualizacao(
    nova: AtualizacaoRemota,
    versaoInstaladaNome: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var baixandoAtualizacao by remember { mutableStateOf(false) }
    var progressoAtualizacao by remember { mutableIntStateOf(0) }
    var erroAtualizacao by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!baixandoAtualizacao) onDismiss()
        },
        containerColor = Color(0xFF1C1C1E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB8B8B8),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3423)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⬇️", fontSize = 30.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Nova Atualização Disponível",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Versão ${nova.versaoNome} já está disponível (Atual: $versaoInstaladaNome)",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                if (nova.novidades.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "O que há de novo:",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF5C542)
                    )
                    nova.novidades.forEach { item ->
                        Text(
                            "• $item",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (baixandoAtualizacao) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Baixando... $progressoAtualizacao%",
                        color = Color(0xFFF5C542),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressoAtualizacao / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFF5C542),
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFFF5C542),
                            strokeWidth = 2.dp
                        )
                    }
                }

                erroAtualizacao?.let { erro ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        erro,
                        color = Color(0xFFE57373),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (!baixandoAtualizacao) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                            return@Button
                        }
                        baixandoAtualizacao = true
                        progressoAtualizacao = 0
                        erroAtualizacao = null
                        escopo.launch {
                            val arquivo = AppUpdateChecker.baixar(
                                context,
                                nova.apkUrl,
                                onProgress = { pct -> progressoAtualizacao = pct },
                                sha256Esperado = nova.sha256.takeIf { it.isNotBlank() }
                            )
                            baixandoAtualizacao = false
                            if (arquivo == null) {
                                erroAtualizacao = if (nova.sha256.isNotBlank()) {
                                    "Download rejeitado (verificação de integridade falhou). Tente novamente."
                                } else {
                                    "Não foi possível baixar a atualização."
                                }
                            } else {
                                AppUpdateChecker.abrirInstalador(context, arquivo)
                            }
                        }
                    }
                ) {
                    Text("Atualizar agora")
                }
            }
        },
        dismissButton = {
            if (!baixandoAtualizacao) {
                TextButton(onClick = onDismiss) {
                    Text("Agora não")
                }
            }
        }
    )
}

/**
 * Botão "Buscar atualização" para Configurações / Sobre o App.
 *
 * Verifica o versao.json no GitHub sob demanda:
 * - versão nova disponível → abre [DialogAtualizacao] (download + instalação);
 * - já atualizado → confirma com a versão instalada;
 * - falha de rede → mensagem de erro.
 */
@Composable
fun BotaoBuscarAtualizacao(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var verificando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var atualizacaoEncontrada by remember { mutableStateOf<AtualizacaoRemota?>(null) }
    val versaoInstaladaNome = remember { AppUpdateChecker.versaoNomeInstalada(context) }

    Column(modifier = modifier) {
        Button(
            onClick = {
                if (verificando) return@Button
                verificando = true
                mensagem = null
                escopo.launch {
                    val remota = AppUpdateChecker.buscar()
                    verificando = false
                    when {
                        remota == null -> {
                            mensagem = "Não foi possível verificar. Verifique sua conexão."
                        }
                        remota.versaoCodigo > AppUpdateChecker.versaoInstalada(context) -> {
                            atualizacaoEncontrada = remota
                        }
                        else -> {
                            mensagem = "Você já está na versão mais recente ($versaoInstaladaNome)"
                        }
                    }
                }
            },
            enabled = !verificando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
        ) {
            if (verificando) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Verificando…")
            } else {
                Text("🔍 Buscar atualização")
            }
        }

        mensagem?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("Não")) Color(0xFFE57373) else TextGray,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    atualizacaoEncontrada?.let { nova ->
        DialogAtualizacao(
            nova = nova,
            versaoInstaladaNome = versaoInstaladaNome,
            onDismiss = { atualizacaoEncontrada = null }
        )
    }
}
