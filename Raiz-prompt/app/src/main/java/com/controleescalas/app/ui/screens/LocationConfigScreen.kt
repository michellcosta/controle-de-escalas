package com.controleescalas.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.CustomTextField
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.LocationConfigViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

data class GeofenceUi(
    val lat: Double,
    val lng: Double,
    val raioM: Int,
    val ativo: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationConfigScreen(
    galpao: GeofenceUi, // Ignorado em favor do ViewModel
    estacionamento: GeofenceUi?, // Ignorado em favor do ViewModel
    onSalvarGalpao: () -> Unit, // Ignorado
    onSalvarEstacionamento: () -> Unit, // Ignorado
    onDesativarEstacionamento: () -> Unit, // Ignorado
    onVoltar: () -> Unit,
    viewModel: LocationConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val galpaoState by viewModel.galpao.collectAsState()
    val estacionamentoState by viewModel.estacionamento.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    
    // Campos de texto locais para edição antes de salvar
    var galpaoLat by remember(galpaoState) { mutableStateOf(galpaoState.lat.toString()) }
    var galpaoLng by remember(galpaoState) { mutableStateOf(galpaoState.lng.toString()) }
    var galpaoRaio by remember(galpaoState) { mutableStateOf(galpaoState.raioM.toString()) }
    
    var estLat by remember(estacionamentoState) { mutableStateOf(estacionamentoState?.lat?.toString() ?: "") }
    var estLng by remember(estacionamentoState) { mutableStateOf(estacionamentoState?.lng?.toString() ?: "") }
    var estRaio by remember(estacionamentoState) { mutableStateOf(estacionamentoState?.raioM?.toString() ?: "") }

    // Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            // Permissão concedida, tentar pegar localização novamente se necessário
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onLocationFound: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationFound(location.latitude, location.longitude)
                } else {
                    // Tentar última localização conhecida se a atual falhar
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                        if (lastLocation != null) {
                            onLocationFound(lastLocation.latitude, lastLocation.longitude)
                        }
                    }
                }
            }
    }

    LaunchedEffect(Unit) {
        // TODO: Pegar baseId real
        viewModel.loadConfig("base_id_placeholder")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configuração de Local", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // GALPÃO
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader(title = "Galpão / Doca")
                        Text(
                            "Motorista marcado como CHEGUEI quando entra neste raio.",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        CustomTextField(
                            value = galpaoLat,
                            onValueChange = { galpaoLat = it },
                            label = "Latitude",
                            leadingIcon = Icons.Default.LocationOn
                        )
                        
                        CustomTextField(
                            value = galpaoLng,
                            onValueChange = { galpaoLng = it },
                            label = "Longitude",
                            leadingIcon = Icons.Default.LocationOn
                        )
                        
                        CustomTextField(
                            value = galpaoRaio,
                            onValueChange = { galpaoRaio = it },
                            label = "Raio (metros)",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    getCurrentLocation { lat, lng ->
                                        galpaoLat = lat.toString()
                                        galpaoLng = lng.toString()
                                        viewModel.updateGalpaoUi(lat, lng)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                            ) {
                                Text("Usar GPS Atual")
                            }
                            
                            NeonButton(
                                text = "Salvar",
                                onClick = {
                                    viewModel.saveGalpao(
                                        "base_id_placeholder",
                                        galpaoLat.toDoubleOrNull() ?: 0.0,
                                        galpaoLng.toDoubleOrNull() ?: 0.0,
                                        galpaoRaio.toIntOrNull() ?: 100
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // ESTACIONAMENTO
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader(title = "Estacionamento")
                        Text(
                            "Motorista marcado como ESTACIONAMENTO quando entra neste raio.",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        CustomTextField(
                            value = estLat,
                            onValueChange = { estLat = it },
                            label = "Latitude",
                            leadingIcon = Icons.Default.LocationOn
                        )
                        
                        CustomTextField(
                            value = estLng,
                            onValueChange = { estLng = it },
                            label = "Longitude",
                            leadingIcon = Icons.Default.LocationOn
                        )
                        
                        CustomTextField(
                            value = estRaio,
                            onValueChange = { estRaio = it },
                            label = "Raio (metros)",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    getCurrentLocation { lat, lng ->
                                        estLat = lat.toString()
                                        estLng = lng.toString()
                                        viewModel.updateEstacionamentoUi(lat, lng)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                            ) {
                                Text("Usar GPS Atual")
                            }
                            
                            NeonButton(
                                text = "Salvar",
                                onClick = {
                                    viewModel.saveEstacionamento(
                                        "base_id_placeholder",
                                        estLat.toDoubleOrNull() ?: 0.0,
                                        estLng.toDoubleOrNull() ?: 0.0,
                                        estRaio.toIntOrNull() ?: 50
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonGreen
                )
            }
            
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
            }
        }
    }
}
