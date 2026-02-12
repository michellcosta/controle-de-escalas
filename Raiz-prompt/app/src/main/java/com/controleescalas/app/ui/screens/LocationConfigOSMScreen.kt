package com.controleescalas.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.LocationConfigViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationConfigOSMScreen(
    baseId: String,
    onVoltar: () -> Unit,
    viewModel: LocationConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val galpaoState by viewModel.galpao.collectAsState()
    val estacionamentoState by viewModel.estacionamento.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    
    var selectedMode by remember { mutableStateOf("galpao") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    // Estado para armazenar localização do usuário
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    
    // Configurar OSMDroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.loadConfig(baseId)
    }
    
    // Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            getCurrentLocationAndCenter(context, fusedLocationClient, mapView) { location ->
                userLocation = location
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Pegar localização automaticamente ao abrir o mapa
            getCurrentLocationAndCenter(context, fusedLocationClient, mapView) { location ->
                userLocation = location
            }
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configurar Localização", color = TextWhite) },
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
        ) {
            // OpenStreetMap
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        
                        // Posição inicial (São Paulo)
                        val initialPos = if (galpaoState.lat != 0.0) {
                            GeoPoint(galpaoState.lat, galpaoState.lng)
                        } else {
                            GeoPoint(-23.550520, -46.633308)
                        }
                        controller.setCenter(initialPos)
                        
                        mapView = this
                        
                        // Click no mapa
                        setOnClickListener {
                            // OSMDroid usa overlay para click
                            false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    map.overlays.clear()
                    
                    // Adicionar marcador da localização do usuário (se disponível)
                    userLocation?.let { userLoc ->
                        val markerUser = Marker(map).apply {
                            position = userLoc
                            title = "Minha Localização"
                            snippet = "Você está aqui"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            
                            // Criar um ícone personalizado (ponto azul com borda branca)
                            val icon = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(icon)
                            
                            // Desenhar círculo azul
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#2196F3") // Azul
                                isAntiAlias = true
                            }
                            canvas.drawCircle(25f, 25f, 18f, paint)
                            
                            // Adicionar borda branca
                            val borderPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 4f
                                isAntiAlias = true
                            }
                            canvas.drawCircle(25f, 25f, 18f, borderPaint)
                            
                            // Adicionar ponto central branco
                            val centerPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                isAntiAlias = true
                            }
                            canvas.drawCircle(25f, 25f, 6f, centerPaint)
                            
                            setIcon(android.graphics.drawable.BitmapDrawable(context.resources, icon))
                        }
                        map.overlays.add(markerUser)
                    }
                    
                    // Adicionar marcador e círculo do Galpão
                    if (galpaoState.lat != 0.0) {
                        // Marcador
                        val markerGalpao = Marker(map).apply {
                            position = GeoPoint(galpaoState.lat, galpaoState.lng)
                            title = "Galpão de Carregamento"
                            snippet = "Raio: ${galpaoState.raioM}m"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(markerGalpao)
                        
                        // Círculo
                        val circleGalpao = Polygon(map).apply {
                            points = Polygon.pointsAsCircle(
                                GeoPoint(galpaoState.lat, galpaoState.lng),
                                galpaoState.raioM.toDouble()
                            )
                            // Destaque visual se estiver no modo de edição
                            val alpha = if (selectedMode == "galpao") 80 else 50
                            fillPaint.color = android.graphics.Color.argb(alpha, 0, 255, 0)
                            outlinePaint.color = android.graphics.Color.argb(255, 0, 255, 0)
                            outlinePaint.strokeWidth = if (selectedMode == "galpao") 5f else 3f
                        }
                        map.overlays.add(circleGalpao)
                    }
                    
                    // Adicionar marcador e círculo do Estacionamento
                    estacionamentoState?.let { est ->
                        if (est.lat != 0.0) {
                            // Marcador
                            val markerEst = Marker(map).apply {
                                position = GeoPoint(est.lat, est.lng)
                                title = "Estacionamento"
                                snippet = "Raio: ${est.raioM}m"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(markerEst)
                            
                            // Círculo
                            val circleEst = Polygon(map).apply {
                                points = Polygon.pointsAsCircle(
                                    GeoPoint(est.lat, est.lng),
                                    est.raioM.toDouble()
                                )
                                // Destaque visual se estiver no modo de edição
                                val alpha = if (selectedMode == "estacionamento") 80 else 50
                                fillPaint.color = android.graphics.Color.argb(alpha, 138, 43, 226)
                                outlinePaint.color = android.graphics.Color.argb(255, 138, 43, 226)
                                outlinePaint.strokeWidth = if (selectedMode == "estacionamento") 5f else 3f
                            }
                            map.overlays.add(circleEst)
                        }
                    }
                    
                    // Overlay para capturar clicks
                    val tapOverlay = object : org.osmdroid.views.overlay.Overlay() {
                        override fun onSingleTapConfirmed(
                            e: android.view.MotionEvent,
                            mapView: MapView
                        ): Boolean {
                            val projection = mapView.projection
                            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                            
                            if (selectedMode == "galpao") {
                                viewModel.updateGalpaoUi(geoPoint.latitude, geoPoint.longitude)
                            } else {
                                viewModel.updateEstacionamentoUi(geoPoint.latitude, geoPoint.longitude)
                            }
                            return true
                        }
                    }
                    map.overlays.add(0, tapOverlay)
                    
                    map.invalidate()
                }
            )
            
            // Controles superiores
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Seletor de modo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { selectedMode = "galpao" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == "galpao") NeonGreen else DarkSurface,
                            contentColor = if (selectedMode == "galpao") Color.Black else TextWhite
                        )
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galpão", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { selectedMode = "estacionamento" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == "estacionamento") NeonPurple else DarkSurface,
                            contentColor = if (selectedMode == "estacionamento") Color.Black else TextWhite
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Estacionamento", fontWeight = FontWeight.Bold)
                    }
                }
                
                // Info card
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (selectedMode == "galpao") "📍 Marcar Galpão" else "🅿️ Marcar Estacionamento",
                            color = if (selectedMode == "galpao") NeonGreen else NeonPurple,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Toque no mapa para marcar a localização",
                            color = TextGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        // Adicionar informação sobre o outro marcador
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (selectedMode == "galpao") {
                                if (estacionamentoState?.lat != 0.0) "✓ Estacionamento já marcado" else "ℹ️ Marque o estacionamento também"
                            } else {
                                if (galpaoState.lat != 0.0) "✓ Galpão já marcado" else "ℹ️ Marque o galpão também"
                            },
                            color = TextGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Botão de Minha Localização
            FloatingActionButton(
                onClick = {
                    getCurrentLocationAndCenter(context, fusedLocationClient, mapView) { location ->
                        userLocation = location
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 90.dp),
                containerColor = NeonBlue,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Minha Localização")
            }
            
            // Botão de salvar
            Button(
                onClick = {
                    if (selectedMode == "galpao") {
                        viewModel.saveGalpao(
                            baseId,
                            galpaoState.lat,
                            galpaoState.lng,
                            galpaoState.raioM
                        )
                    } else {
                        estacionamentoState?.let {
                            viewModel.saveEstacionamento(
                                baseId,
                                it.lat,
                                it.lng,
                                it.raioM
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == "galpao") NeonGreen else NeonPurple,
                    contentColor = Color.Black
                ),
                // Desabilitar botão se não houver coordenadas marcadas
                enabled = if (selectedMode == "galpao") {
                    galpaoState.lat != 0.0 && galpaoState.lng != 0.0
                } else {
                    estacionamentoState?.lat != 0.0 && estacionamentoState?.lng != 0.0
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar ${if (selectedMode == "galpao") "Galpão" else "Estacionamento"}", fontWeight = FontWeight.Bold)
            }
            
            // Loading
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonGreen
                )
            }
            
            // Mensagem
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.clearMessage()
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
        }
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentLocationAndCenter(
    context: android.content.Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    mapView: MapView?,
    onLocationFound: (GeoPoint) -> Unit = {} // Callback opcional para retornar a localização
) {
    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    mapView?.controller?.apply {
                        setCenter(geoPoint)
                        setZoom(15.0)
                    }
                    // Chamar callback com a localização encontrada
                    onLocationFound(geoPoint)
                }
            }
    }
}
