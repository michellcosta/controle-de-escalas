package com.controleescalas.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.navigation.AppNavHost
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.theme.ControleEscalasTheme

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

            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* concedida ou negada */ }

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
                    }
                }
                kotlinx.coroutines.delay(300)
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

            val sessionManager = remember { SessionManager(this) }
            val themeMode by sessionManager.themeModeFlow.collectAsState(initial = "dark")

            ControleEscalasTheme(darkTheme = themeMode == "dark") {
                PremiumBackground {
                    AppNavHost()
                }
            }
        }
    }
}


