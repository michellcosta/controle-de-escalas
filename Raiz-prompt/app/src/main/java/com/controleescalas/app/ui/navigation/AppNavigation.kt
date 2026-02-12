package com.controleescalas.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Operation : BottomNavItem("operation", Icons.Default.Home, "Operação")
    object Availability : BottomNavItem("availability", Icons.Default.DateRange, "Disponibilidade")
    object Configuration : BottomNavItem("configuration", Icons.Default.Settings, "Configuração")
}
