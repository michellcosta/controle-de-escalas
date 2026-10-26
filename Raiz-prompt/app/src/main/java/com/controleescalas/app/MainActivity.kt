package com.controleescalas.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.controleescalas.app.data.AppUpdateChecker
import com.controleescalas.app.data.AtualizacaoRemota
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.navigation.AppNavHost
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.theme.ControleEscalasTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity
 *
 * Sobe a navegação Compose.
 * Solicita permissões (notificação + localização) na primeira entrada para todos os usuários.
 * Telas principais:
 * - LoginScreen
 * - DriverHomeScreen (Motorista)
 * - AdminPanelScreen (Admin/Ajudante)
 * - ScaleScreen (Escala do Dia / Ondas AM/PM)
 * - LocationConfigScreen (pinos de GPS)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showBackgroundLocationDialog by remember { mutableStateOf(false) }
            var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

            val batteryOptimizationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { /* resultado ignorado — o usuário pode ter permitido ou não */ }

            val locationForegroundLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val hasBackground = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasBackground) showBackgroundLocationDialog = true
                }
            }

            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                // Só pedir localização DEPOIS que o usuário respondeu à notificação
                // (evita que o sistema não mostre o 2º diálogo enquanto o 1º está aberto)
                val hasLocation = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                if (!hasLocation) {
                    locationForegroundLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val hasBackground = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasBackground) showBackgroundLocationDialog = true
                }
            }

            val backgroundLocationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { showBackgroundLocationDialog = false }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@LaunchedEffect
                    }
                }
                // Se já tem notificação ou Android < 13, pedir localização direto
                val hasLocation = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                if (!hasLocation) {
                    locationForegroundLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val hasBackground = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasBackground) showBackgroundLocationDialog = true
                }
            }

            // Verificar isenção de bateria separadamente, com delay para não sobrepor os outros diálogos
            LaunchedEffect(Unit) {
                delay(3000L)
                val pm = getSystemService(PowerManager::class.java)
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    showBatteryOptimizationDialog = true
                }
            }

            val sessionManager = remember { SessionManager(this) }
            val themeMode by sessionManager.themeModeFlow.collectAsState(initial = "dark")
            val escopo = rememberCoroutineScope()
            var atualizacao by remember { mutableStateOf<AtualizacaoRemota?>(null) }
            var baixandoAtualizacao by remember { mutableStateOf(false) }
            var progressoAtualizacao by remember { mutableStateOf(0) }
            var erroAtualizacao by remember { mutableStateOf<String?>(null) }
            val versaoInstaladaNome = remember { AppUpdateChecker.versaoNomeInstalada(this@MainActivity) }

            LaunchedEffect(Unit) {
                val remota = AppUpdateChecker.buscar() ?: return@LaunchedEffect
                val instalada = AppUpdateChecker.versaoInstalada(this@MainActivity)
                if (remota.versaoCodigo > instalada) {
                    atualizacao = remota
                }
            }

            ControleEscalasTheme(darkTheme = themeMode == "dark") {
                PremiumBackground {
                    AppNavHost()
                }
                if (showBackgroundLocationDialog) {
                    AlertDialog(
                        onDismissRequest = { showBackgroundLocationDialog = false },
                        title = {
                            Text(
                                "Localização em segundo plano",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Para o app funcionar corretamente (detectar entrada/saída do galpão, chamadas para vagas), permita o acesso à localização mesmo quando o app estiver em segundo plano.")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Selecione \"Permitir o tempo todo\" na próxima tela.",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Quando seu status estiver concluído, o app para de acessar a localização em segundo plano automaticamente.")
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showBackgroundLocationDialog = false
                                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }) { Text("Continuar") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBackgroundLocationDialog = false }) {
                                Text("Depois")
                            }
                        }
                    )
                }

                if (showBatteryOptimizationDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryOptimizationDialog = false },
                        title = {
                            Text(
                                "Manter app ativo em segundo plano",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Seu celular pode desligar o app automaticamente para economizar bateria.")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Isso faz você perder chamadas para carregar mesmo com o celular na mão.",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Permitir que o app rode sem restrições garante que você receba todas as notificações.")
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showBatteryOptimizationDialog = false
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                batteryOptimizationLauncher.launch(intent)
                            }) { Text("Permitir") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                                Text("Depois")
                            }
                        }
                    )
                }

                atualizacao?.let { nova ->
                    AlertDialog(
                        onDismissRequest = {
                            if (!baixandoAtualizacao) atualizacao = null
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
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
                                            !packageManager.canRequestPackageInstalls()
                                        ) {
                                            startActivity(
                                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                                    data = Uri.parse("package:$packageName")
                                                }
                                            )
                                            return@Button
                                        }
                                        baixandoAtualizacao = true
                                        progressoAtualizacao = 0
                                        erroAtualizacao = null
                                        escopo.launch {
                                            val arquivo = AppUpdateChecker.baixar(
                                                this@MainActivity,
                                                nova.apkUrl
                                            ) { pct -> progressoAtualizacao = pct }
                                            baixandoAtualizacao = false
                                            if (arquivo == null) {
                                                erroAtualizacao = "Não foi possível baixar a atualização."
                                            } else {
                                                AppUpdateChecker.abrirInstalador(this@MainActivity, arquivo)
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
                                TextButton(onClick = { atualizacao = null }) {
                                    Text("Agora não")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}


