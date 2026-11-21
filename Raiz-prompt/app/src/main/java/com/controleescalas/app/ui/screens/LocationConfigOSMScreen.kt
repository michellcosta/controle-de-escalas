package com.controleescalas.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.LocationConfigViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import com.controleescalas.app.data.models.GeoPonto
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

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
            AppTopBar(
                title = "Configurar Localização",
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = onVoltar
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
                                color = android.graphics.Color.parseColor("#3483FA")
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
                    
                    desenharArea(
                        map = map,
                        context = context,
                        titulo = "Galpão de Carregamento",
                        pontos = galpaoState.pontos,
                        centroLat = galpaoState.lat,
                        centroLng = galpaoState.lng,
                        raio = galpaoState.raioM,
                        ativo = selectedMode == "galpao",
                        fillRgb = Triple(16, 185, 129)
                    )

                    estacionamentoState?.let { est ->
                        desenharArea(
                            map = map,
                            context = context,
                            titulo = "Estacionamento",
                            pontos = est.pontos,
                            centroLat = est.lat,
                            centroLng = est.lng,
                            raio = est.raioM,
                            ativo = selectedMode == "estacionamento",
                            fillRgb = Triple(124, 58, 237)
                        )
                    }
                    
                    // Overlay para capturar clicks
                    val tapOverlay = object : org.osmdroid.views.overlay.Overlay() {
                        override fun onSingleTapConfirmed(
                            e: android.view.MotionEvent,
                            mapView: MapView
                        ): Boolean {
                            val projection = mapView.projection
                            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                            
                            viewModel.addPonto(selectedMode, geoPoint.latitude, geoPoint.longitude)
                            return true
                        }
                    }
                    map.overlays.add(0, tapOverlay)
                    
                    map.invalidate()
                }
            )
            
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = LightSurfaceVariant,
                        selectedContainerColor = MeliBlue,
                        labelColor = TextGrayLightMode,
                        selectedLabelColor = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedMode == "galpao",
                            onClick = { selectedMode = "galpao" },
                            modifier = Modifier.weight(1f),
                            label = { Text("Galpão", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = selectedMode == "estacionamento",
                            onClick = { selectedMode = "estacionamento" },
                            modifier = Modifier.weight(1f),
                            label = { Text("Estacionamento", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(Icons.Default.LocalParking, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            colors = chipColors
                        )
                    }

                    val pontosAtivos = if (selectedMode == "galpao") {
                        galpaoState.pontos
                    } else {
                        estacionamentoState?.pontos.orEmpty()
                    }
                    Text(
                        if (selectedMode == "galpao") "Desenhar galpão" else "Desenhar estacionamento",
                        color = TextBlack,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            pontosAtivos.size >= 3 -> "Área pronta. Toque para incluir outro canto ou salve."
                            pontosAtivos.isEmpty() -> "Toque nos cantos da área no mapa (mínimo 3 pontos)."
                            else -> "Faltam ${3 - pontosAtivos.size} ponto(s) para fechar a área."
                        },
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { viewModel.desfazerPonto(selectedMode) },
                            enabled = pontosAtivos.isNotEmpty()
                        ) {
                            Text("Desfazer")
                        }
                        TextButton(
                            onClick = { viewModel.limparArea(selectedMode) },
                            enabled = pontosAtivos.isNotEmpty() ||
                                (selectedMode == "galpao" && galpaoState.lat != 0.0) ||
                                (selectedMode == "estacionamento" && (estacionamentoState?.lat ?: 0.0) != 0.0)
                        ) {
                            Text("Limpar")
                        }
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
                containerColor = Color.White,
                contentColor = MeliBlue
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Minha Localização")
            }
            
            val pontosParaSalvar = if (selectedMode == "galpao") {
                galpaoState.pontos
            } else {
                estacionamentoState?.pontos.orEmpty()
            }
            Button(
                onClick = {
                    if (selectedMode == "galpao") {
                        viewModel.saveGalpao(baseId)
                    } else {
                        viewModel.saveEstacionamento(baseId)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeliBlue,
                    contentColor = Color.White
                ),
                enabled = pontosParaSalvar.size >= 3
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar ${if (selectedMode == "galpao") "Galpão" else "Estacionamento"}", fontWeight = FontWeight.Bold)
            }
            
            // Loading
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MeliBlue
                )
            }
            
            // Mensagem
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                    containerColor = MeliBlue,
                    contentColor = Color.White
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

private fun desenharArea(
    map: MapView,
    context: android.content.Context,
    titulo: String,
    pontos: List<GeoPonto>,
    centroLat: Double,
    centroLng: Double,
    raio: Int,
    ativo: Boolean,
    fillRgb: Triple<Int, Int, Int>
) {
    val (r, g, b) = fillRgb
    val alpha = if (ativo) 80 else 50
    val stroke = if (ativo) 5f else 3f

    if (pontos.isNotEmpty()) {
        val geoPontos = pontos.map { GeoPoint(it.lat, it.lng) }
        if (pontos.size >= 3) {
            val fechado = ArrayList(geoPontos).apply { add(GeoPoint(geoPontos.first().latitude, geoPontos.first().longitude)) }
            val area = Polygon(map).apply {
                this.points = fechado
                fillPaint.color = android.graphics.Color.argb(alpha, r, g, b)
                outlinePaint.color = android.graphics.Color.argb(255, r, g, b)
                outlinePaint.strokeWidth = stroke
                title = titulo
            }
            map.overlays.add(area)
        } else if (pontos.size == 2) {
            val linha = Polyline().apply {
                setPoints(geoPontos)
                outlinePaint.color = android.graphics.Color.argb(255, r, g, b)
                outlinePaint.strokeWidth = stroke
            }
            map.overlays.add(linha)
        }
        pontos.forEachIndexed { index, ponto ->
            val marker = Marker(map).apply {
                position = GeoPoint(ponto.lat, ponto.lng)
                title = "$titulo ${index + 1}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                val icon = android.graphics.Bitmap.createBitmap(28, 28, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(icon)
                val fill = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(255, r, g, b)
                    isAntiAlias = true
                }
                canvas.drawCircle(14f, 14f, 10f, fill)
                val borda = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3f
                    isAntiAlias = true
                }
                canvas.drawCircle(14f, 14f, 10f, borda)
                setIcon(android.graphics.drawable.BitmapDrawable(context.resources, icon))
            }
            map.overlays.add(marker)
        }
        return
    }

    if (centroLat != 0.0 && centroLng != 0.0) {
        val marker = Marker(map).apply {
            position = GeoPoint(centroLat, centroLng)
            title = titulo
            snippet = "Raio: ${raio}m"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marker)
        val circle = Polygon(map).apply {
            points = Polygon.pointsAsCircle(GeoPoint(centroLat, centroLng), raio.toDouble())
            fillPaint.color = android.graphics.Color.argb(alpha, r, g, b)
            outlinePaint.color = android.graphics.Color.argb(255, r, g, b)
            outlinePaint.strokeWidth = stroke
        }
        map.overlays.add(circle)
    }
}
