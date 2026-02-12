
package com.controleescalas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.controleescalas.app.navigation.AppNavHost
import com.controleescalas.app.ui.theme.ControleEscalasTheme

/**
 * MainActivity
 *
 * Sobe a navegação Compose.
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
            ControleEscalasTheme {
                AppNavHost()
            }
        }
    }
}


