package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.components.CustomTextField
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.AppButton
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*

data class CreateBaseData(
    val nomeTransportadora: String,
    val nomeBase: String,
    val telefoneAdmin: String,
    val pinAdmin: String,
    val corTema: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBaseScreen(
    onBackClick: () -> Unit,
    onCreateBaseClick: (CreateBaseData) -> Unit,
    successMessage: String? = null,
    onSuccessAcknowledged: () -> Unit = {}
) {
    var nomeTransportadora by remember { mutableStateOf("") }
    var nomeBase by remember { mutableStateOf("") }
    var telefoneAdmin by remember { mutableStateOf("") }
    var pinAdmin by remember { mutableStateOf("") }
    var corTema by remember { mutableStateOf("#00FF88") }

    val isFormValid = nomeTransportadora.isNotBlank() &&
            nomeBase.isNotBlank() &&
            telefoneAdmin.isNotBlank() &&
            pinAdmin.length == 6
    
    // Estado para controlar o diálogo de sucesso (trial ativado)
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    // Mostrar diálogo quando houver sucesso
    LaunchedEffect(successMessage) {
        if (successMessage == "SUCCESS") {
            showSuccessDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Criar Transportadora",
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = onBackClick
)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MeliYellow.copy(alpha = 0.5f), Color.White)
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MeliBlue,
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Nova Transportadora",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextBlack,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        "Preencha os dados para criar sua base de operações",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGrayLightMode,
                        textAlign = TextAlign.Center
                    )
                }

                // Formulário
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SectionHeader(title = "Dados da Empresa")
                        
                        CustomTextField(
                            value = nomeTransportadora,
                            onValueChange = { nomeTransportadora = it },
                            label = "Nome da Transportadora",
                            leadingIcon = Icons.Default.Add
                        )

                        CustomTextField(
                            value = nomeBase,
                            onValueChange = { nomeBase = it },
                            label = "Nome da Base",
                            leadingIcon = Icons.Default.LocationOn
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SectionHeader(title = "Administrador")
                        
                        Text(
                            "Estes serão os dados de acesso do administrador principal",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGrayLightMode
                        )

                        CustomTextField(
                            value = telefoneAdmin,
                            onValueChange = { telefoneAdmin = it },
                            label = "Telefone do Admin",
                            leadingIcon = Icons.Default.Phone,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        CustomTextField(
                            value = pinAdmin,
                            onValueChange = { newValue ->
                                val numericValue = newValue.filter { it.isDigit() }
                                if (numericValue.length <= 6) {
                                    pinAdmin = numericValue
                                }
                            },
                            label = "PIN (6 dígitos)",
                            leadingIcon = Icons.Default.Lock,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                    }
                }

                // Botão de Criar
                AppButton(
                    text = "CRIAR TRANSPORTADORA",
                    onClick = {
                        onCreateBaseClick(
                            CreateBaseData(
                                nomeTransportadora = nomeTransportadora,
                                nomeBase = nomeBase,
                                telefoneAdmin = telefoneAdmin,
                                pinAdmin = pinAdmin,
                                corTema = corTema
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isFormValid
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        // Diálogo de sucesso (trial 30 dias ativado)
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showSuccessDialog = false
                    onSuccessAcknowledged()
                },
                containerColor = Color.White,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeliBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "Transportadora criada!",
                            color = TextBlack,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "🎉 Versão Beta - Acesso gratuito para testes",
                            color = MeliBlue,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Sua transportadora está pronta. Faça login com o telefone e PIN do admin para começar a usar.",
                            color = TextGrayLightMode,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            "Aproveite todos os recursos durante o período de testes.",
                            color = TextGrayLightMode,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            onSuccessAcknowledged()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeliBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "ENTENDI",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                },
                shape = MaterialTheme.shapes.large
            )
        }
    }
}
