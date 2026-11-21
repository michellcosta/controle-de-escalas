package com.controleescalas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.MeliYellow
import com.controleescalas.app.ui.theme.TextGrayLightMode

/**
 * TopAppBar padrão do app (tema claro estilo Meli): topo amarelo, texto preto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            if (subtitle != null) {
                Column {
                    Text(
                        text = title,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = subtitle,
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = title,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        navigationIcon = {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = null,
                        tint = Color.Black
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MeliYellow,
            scrolledContainerColor = MeliYellow,
            navigationIconContentColor = Color.Black,
            titleContentColor = Color.Black,
            actionIconContentColor = Color.Black
        ),
        modifier = modifier
    )
}

/**
 * Cabeçalho amarelo simples (sem TopAppBar), para telas que montam o próprio header em Box/Column.
 */
@Composable
fun MeliHeader(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MeliYellow)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        content()
    }
}
