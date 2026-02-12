package com.controleescalas.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.yalantis.ucrop.UCrop
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.CNHImageManager
import com.controleescalas.app.data.CNHQRCodeManager
import com.controleescalas.app.data.MotoristaQRCodeManager
import com.controleescalas.app.data.models.MotoristaQRCode
import com.controleescalas.app.ui.components.BarcodeScannerScreen
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.utils.QRCodeGenerator
import com.controleescalas.app.utils.QRCodeImageReader
import com.controleescalas.app.utils.QRCodeValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverIdentificacaoScreen(
    motoristaId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // Gerador de som para feedback quando QR Code for validado
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) // Volume 80%
    }
    
    // Estados
    var selectedTab by remember { mutableStateOf(0) } // 0 = CNH/QR code, 1 = QR do Motorista
    var showScanner by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Manager e QR Code
    val qrCodeManager = remember { MotoristaQRCodeManager(context) }
    val qrCodeFlow = qrCodeManager.qrCodeFlow
    val qrCode by qrCodeFlow.collectAsState(initial = null)
    
    // Flow do JSON do QR Code (para geração rápida)
    val qrCodeJsonFlow = qrCodeManager.qrCodeJsonFlow
    val qrCodeJson by qrCodeJsonFlow.collectAsState(initial = null)
    
    // QR Code gerado (Bitmap)
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Launcher para permissão de câmera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        }
    }
    
    // Launcher para selecionar imagem da galeria
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    if (bitmap != null) {
                        val qrCodeText = QRCodeImageReader.readQRCodeFromBitmap(bitmap)
                        if (qrCodeText != null) {
                            processQRCodeText(qrCodeText, qrCodeManager, coroutineScope, toneGenerator) { error, success ->
                                errorMessage = error
                                successMessage = success
                                isLoading = false
                            }
                        } else {
                            errorMessage = "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
                            isLoading = false
                        }
                    } else {
                        errorMessage = "Erro ao carregar imagem"
                        isLoading = false
                    }
                } catch (e: Exception) {
                    errorMessage = "Erro ao processar imagem: ${e.message}"
                    isLoading = false
                }
            }
        }
    }
    
    // Carregar e gerar QR Code quando houver um salvo (usando JSON diretamente - mais rápido)
    LaunchedEffect(qrCodeJson) {
        val currentQRCodeJson = qrCodeJson
        if (currentQRCodeJson != null && currentQRCodeJson.isNotBlank()) {
            // Obter largura da tela
            val screenWidth = with(density) {
                context.resources.displayMetrics.widthPixels.dp.toPx().toInt()
            }
            
            // Gerar QR Code em background para não bloquear a UI (usando JSON já serializado)
            qrCodeBitmap = withContext(Dispatchers.Default) {
                QRCodeGenerator.generateQRCode(currentQRCodeJson, screenWidth, screenWidth)
            }
        } else {
            qrCodeBitmap = null
        }
    }
    
    // Mostrar mensagens
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            errorMessage = null
        }
    }
    
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            successMessage = null
        }
    }
    
    var showInfoDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identificação", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Informações sobre Identificação",
                            tint = TextWhite
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                val messageText = snackbarData.visuals.message
                val isError = messageText.contains("inválido", ignoreCase = true)
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (isError) Color(0xFFEF4444) else NeonGreen,
                    contentColor = if (isError) TextWhite else Color.Black
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = TextWhite
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("CNH/QR code") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("QR do Motorista") }
                    )
                }
                
                // Conteúdo das abas
                when (selectedTab) {
                    0 -> {
                        // Aba CNH/QR code
                        CNHTab(
                            motoristaId = motoristaId,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    1 -> {
                        // Aba QR do Motorista
                        QRMotoristaTab(
                            qrCodeBitmap = qrCodeBitmap,
                            isLoading = isLoading,
                            onEscanearClick = {
                                when {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED -> {
                                        showScanner = true
                                    }
                                    else -> {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            onAbrirImagemClick = {
                                imagePickerLauncher.launch("image/*")
                            }
                        )
                    }
                }
            }
            
            // Scanner de QR Code
            if (showScanner) {
                BarcodeScannerScreen(
                    onBarcodeScanned = { barcodeValue ->
                        showScanner = false
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            processQRCodeText(barcodeValue, qrCodeManager, coroutineScope, toneGenerator) { error, success ->
                                errorMessage = error
                                successMessage = success
                                isLoading = false
                            }
                        }
                    },
                    onError = { errorMsg, _ ->
                        errorMessage = errorMsg
                    },
                    onDismiss = { showScanner = false },
                    snackbarHostState = snackbarHostState,
                    aceitarJSONCompleto = true // Passar JSON completo para identificação
                )
            }
        }
        
        // Diálogo de informação sobre Identificação
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Text(
                        "Como funciona a Identificação",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "A tela de Identificação possui duas abas:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                        Text(
                            "• CNH/QR code: Adicione a imagem da sua CNH e escaneie o QR Code da habilitação com a câmera",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                        Text(
                            "• QR do Motorista: Escaneie ou importe um QR Code para identificação pessoal",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Os dados são salvos localmente no seu dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray.copy(alpha = 0.7f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Entendi", color = NeonPurple)
                    }
                },
                containerColor = DarkBackground,
                titleContentColor = TextWhite,
                textContentColor = TextGray
            )
        }
    }
}

/**
 * Processa o texto do QR Code: valida, salva e gera o QR Code
 */
suspend fun processQRCodeText(
    qrCodeText: String,
    qrCodeManager: MotoristaQRCodeManager,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    toneGenerator: ToneGenerator,
    onResult: (String?, String?) -> Unit
) {
    // Validar QR Code
    val (qrCode, error) = QRCodeValidator.validateAndParse(qrCodeText)
    
    if (qrCode != null) {
        // Salvar QR Code
        val saved = qrCodeManager.saveQRCode(qrCode)
        if (saved) {
            // Tocar beep de sucesso
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150) // Bip de 150ms
                println("🔊 DriverIdentificacaoScreen: Beep de sucesso tocado")
            } catch (e: Exception) {
                println("⚠️ DriverIdentificacaoScreen: Erro ao tocar beep: ${e.message}")
            }
            onResult(null, "QR Code salvo com sucesso!")
        } else {
            onResult("Erro ao salvar QR Code", null)
        }
    } else {
        onResult(error ?: "QR Code inválido", null)
    }
}

/**
 * Aba QR do Motorista
 */
@Composable
fun QRMotoristaTab(
    qrCodeBitmap: Bitmap?,
    isLoading: Boolean,
    onEscanearClick: () -> Unit,
    onAbrirImagemClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // QR Code gerado (no topo)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = NeonGreen
                )
            }
        } else if (qrCodeBitmap != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Seu QR Code",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Image(
                            bitmap = qrCodeBitmap.asImageBitmap(),
                            contentDescription = "QR Code do Motorista",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GlassCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = TextGray
                        )
                        Text(
                            "Nenhum QR Code cadastrado",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Escaneie ou importe um QR Code para começar",
                            color = TextGray.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        // Botões de ação (na parte inferior)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onEscanearClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear QR Code", fontWeight = FontWeight.Bold)
            }
            
            OutlinedButton(
                onClick = onAbrirImagemClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
            ) {
                Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir Imagem")
            }
        }
    }
}

/**
 * Aba CNH/QR code com HorizontalPager (2 páginas)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CNHTab(
    motoristaId: String,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Managers
    val cnhImageManager = remember { CNHImageManager(context) }
    val cnhQRCodeManager = remember { CNHQRCodeManager(context) }
    
    // Estados
    var cnhImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showQRCodeScanner by remember { mutableStateOf(false) }
    
    // Pager state
    val pagerState = rememberPagerState()
    
    // Flows
    val hasCnhImage by cnhImageManager.hasCnhImageFlow.collectAsState(initial = false)
    val hasQRCode by cnhQRCodeManager.hasQRCodeFlow.collectAsState(initial = false)
    
    // ToneGenerator para beep
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    }
    
    // Launcher para crop de imagem (uCrop) - definido primeiro para ser usado depois
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                val resultUri = UCrop.getOutput(data)
                if (resultUri != null) {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        val saved = cnhImageManager.saveCnhImageFromUri(resultUri)
                        if (saved) {
                            successMessage = "Imagem da CNH salva com sucesso!"
                            cnhImageBitmap = cnhImageManager.loadCnhImage()
                        } else {
                            errorMessage = "Erro ao salvar imagem da CNH"
                        }
                        isLoading = false
                    }
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            result.data?.let { data ->
                val cropError = UCrop.getError(data)
                errorMessage = "Erro ao recortar imagem: ${cropError?.message}"
            }
        }
    }
    
    // Launcher para selecionar imagem da galeria
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // Iniciar crop com uCrop
            val destinationUri = Uri.fromFile(
                java.io.File(context.cacheDir, "cnh_cropped.jpg")
            )
            
            // Configurar opções do uCrop para permitir ajuste livre pelos cantos
            val options = com.yalantis.ucrop.UCrop.Options()
            options.setFreeStyleCropEnabled(true) // Permite ajuste livre pelos cantos
            
            // Não definir opções de proporção - isso oculta os botões de proporção (1:1, 3:4, etc)
            // mas mantém os botões de ação visíveis (Girar, ✖ Cancelar, ✔ Confirmar)
            
            // Configurar toolbar
            options.setToolbarTitle("Recortar")
            options.setToolbarColor(android.graphics.Color.parseColor("#1A1A1A"))
            options.setStatusBarColor(android.graphics.Color.parseColor("#1A1A1A"))
            // Definir cor clara para os botões da toolbar (✖ Cancelar, ✔ Confirmar, Girar)
            // para que fiquem visíveis no fundo escuro
            options.setToolbarWidgetColor(android.graphics.Color.WHITE)
            
            val uCrop = UCrop.of(uri, destinationUri)
                .withMaxResultSize(2000, 2000)
                .withOptions(options)
            
            cropImageLauncher.launch(uCrop.getIntent(context))
        }
    }
    
    // Launcher para crop de imagem do QR Code (uCrop)
    val qrCodeCropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                val resultUri = UCrop.getOutput(data)
                if (resultUri != null) {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            // Ler QR Code da imagem cortada
                            val inputStream = context.contentResolver.openInputStream(resultUri)
                            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            
                            if (bitmap == null) {
                                errorMessage = "Erro ao decodificar imagem"
                                isLoading = false
                                return@launch
                            }
                            
                            // Ler o QR Code da imagem
                            val qrCodeText = com.controleescalas.app.utils.QRCodeImageReader.readQRCodeFromBitmap(bitmap)
                            
                            if (qrCodeText == null || qrCodeText.isBlank()) {
                                errorMessage = "Nenhum QR Code encontrado na imagem"
                                isLoading = false
                                return@launch
                            }
                            
                            // Salvar o texto do QR Code
                            val saved = cnhQRCodeManager.saveQRCode(qrCodeText)
                            if (!saved) {
                                errorMessage = "Erro ao salvar QR Code"
                                isLoading = false
                                return@launch
                            }
                            
                            // Gerar QR Code dinamicamente a partir do texto salvo
                            generateQRCodeBitmap(qrCodeText, density, context) { generatedBitmap ->
                                coroutineScope.launch {
                                    if (generatedBitmap != null) {
                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                        successMessage = "QR Code da habilitação salvo com sucesso!"
                                        qrCodeBitmap = generatedBitmap
                                    } else {
                                        errorMessage = "Erro ao gerar QR Code"
                                    }
                                    isLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ DriverIdentificacaoScreen: Erro ao processar QR Code: ${e.message}")
                            e.printStackTrace()
                            errorMessage = "Erro ao processar QR Code: ${e.message}"
                            isLoading = false
                        }
                    }
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            result.data?.let { data ->
                val cropError = UCrop.getError(data)
                errorMessage = "Erro ao recortar imagem: ${cropError?.message}"
            }
        }
    }
    
    // Launcher para selecionar imagem do QR Code da galeria
    val qrCodeImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // Iniciar crop com uCrop
            val destinationUri = Uri.fromFile(
                java.io.File(context.cacheDir, "cnh_qr_code_cropped.jpg")
            )
            
            // Configurar opções do uCrop para permitir ajuste livre pelos cantos
            val options = com.yalantis.ucrop.UCrop.Options()
            options.setFreeStyleCropEnabled(true) // Permite ajuste livre pelos cantos
            
            // Configurar toolbar
            options.setToolbarTitle("Recortar")
            options.setToolbarColor(android.graphics.Color.parseColor("#1A1A1A"))
            options.setStatusBarColor(android.graphics.Color.parseColor("#1A1A1A"))
            // Definir cor clara para os botões da toolbar (✖ Cancelar, ✔ Confirmar, Girar)
            options.setToolbarWidgetColor(android.graphics.Color.WHITE)
            
            val uCrop = UCrop.of(uri, destinationUri)
                .withMaxResultSize(2000, 2000)
                .withOptions(options)
            
            qrCodeCropImageLauncher.launch(uCrop.getIntent(context))
        }
    }
    
    // Launcher para permissão de câmera (para escanear QR Code)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQRCodeScanner = true
        } else {
            errorMessage = "Permissão de câmera negada"
        }
    }
    
    // Carregar imagem da CNH quando houver
    LaunchedEffect(hasCnhImage) {
        if (hasCnhImage) {
            cnhImageBitmap = cnhImageManager.loadCnhImage()
        } else {
            cnhImageBitmap = null
        }
    }
    
    // Carregar QR Code quando houver (gerar dinamicamente a partir do texto salvo)
    LaunchedEffect(hasQRCode) {
        if (hasQRCode) {
            coroutineScope.launch {
                val qrCodeText = cnhQRCodeManager.getQRCodeText()
                if (qrCodeText != null && qrCodeText.isNotBlank()) {
                    generateQRCodeBitmap(qrCodeText, density, context) { bitmap ->
                        coroutineScope.launch {
                            qrCodeBitmap = bitmap
                        }
                    }
                } else {
                    qrCodeBitmap = null
                }
            }
        } else {
            qrCodeBitmap = null
        }
    }
    
    // Mostrar mensagens
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            errorMessage = null
        }
    }
    
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            successMessage = null
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // HorizontalPager com 2 páginas
        HorizontalPager(
            count = 2,
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    // Página 1: Imagem da CNH
                    CNHImagePage(
                        cnhImageBitmap = cnhImageBitmap,
                        isLoading = isLoading,
                        onSelectImageClick = {
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                }
                1 -> {
                    // Página 2: QR Code da habilitação
                    CNHQRCodePage(
                        qrCodeBitmap = qrCodeBitmap,
                        isLoading = isLoading,
                        onSelectImageClick = {
                            qrCodeImagePickerLauncher.launch("image/*")
                        },
                        onScanWithCameraClick = {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    showQRCodeScanner = true
                                }
                                else -> {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }
                    )
                }
            }
        }
        
        // Texto "Arraste para o lado" com seta que muda conforme a página
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPage1 = pagerState.currentPage == 0
            
            if (!isPage1) {
                // Página 2: seta para esquerda antes do texto
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextGray
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            Text(
                text = "Arraste para o lado",
                color = TextGray,
                style = MaterialTheme.typography.bodySmall
            )
            
            if (isPage1) {
                // Página 1: seta para direita depois do texto
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextGray
                )
            }
        }
        
        // Scanner de QR Code da habilitação
        if (showQRCodeScanner) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcodeValue ->
                    showQRCodeScanner = false
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            // Salvar o texto do QR Code
                            val saved = cnhQRCodeManager.saveQRCode(barcodeValue)
                            if (!saved) {
                                errorMessage = "Erro ao salvar QR Code"
                                isLoading = false
                                return@launch
                            }
                            
                            // Gerar QR Code dinamicamente a partir do texto salvo
                            generateQRCodeBitmap(barcodeValue, density, context) { generatedBitmap ->
                                coroutineScope.launch {
                                    if (generatedBitmap != null) {
                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                        successMessage = "QR Code da habilitação salvo com sucesso!"
                                        qrCodeBitmap = generatedBitmap
                                    } else {
                                        errorMessage = "Erro ao gerar QR Code"
                                    }
                                    isLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ DriverIdentificacaoScreen: Erro ao processar QR Code: ${e.message}")
                            e.printStackTrace()
                            errorMessage = "Erro ao processar QR Code: ${e.message}"
                            isLoading = false
                        }
                    }
                },
                onError = { errorMsg, _ ->
                    errorMessage = errorMsg
                    showQRCodeScanner = false
                },
                onDismiss = { showQRCodeScanner = false },
                snackbarHostState = snackbarHostState,
                aceitarJSONCompleto = true // Aceitar qualquer texto/JSON para QR Code da habilitação
            )
        }
    }
    
}

/**
 * Página 1: Imagem da CNH
 */
@Composable
fun CNHImagePage(
    cnhImageBitmap: Bitmap?,
    isLoading: Boolean,
    onSelectImageClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = NeonGreen
            )
        } else if (cnhImageBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Imagem da CNH ocupando todo o card
                GlassCard(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = cnhImageBitmap.asImageBitmap(),
                        contentDescription = "CNH",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Botão "Atualizar CNH" discreto no canto inferior esquerdo
                IconButton(
                    onClick = onSelectImageClick,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Edit,
                        "Atualizar CNH",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Description,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = TextGray
                )
                Text(
                    "Nenhuma imagem da CNH cadastrada",
                    color = TextGray,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Selecione uma imagem da galeria",
                    color = TextGray.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                
                Button(
                    onClick = onSelectImageClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Selecionar imagem da CNH", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Página 2: QR Code da habilitação
 */
@Composable
fun CNHQRCodePage(
    qrCodeBitmap: Bitmap?,
    isLoading: Boolean,
    onSelectImageClick: () -> Unit,
    onScanWithCameraClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = NeonGreen
            )
        } else if (qrCodeBitmap != null) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // QR Code enorme ocupando todo o card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Texto "QR Code CNH" acima do QR Code
                        Text(
                            text = "QR Code CNH",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        
                        // QR Code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 16.dp), // Mais espaço ao redor para melhor legibilidade
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrCodeBitmap.asImageBitmap(),
                                contentDescription = "QR Code da Habilitação",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                // Botão de ação na parte inferior
                Button(
                    onClick = onScanWithCameraClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Escanear", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.QrCode,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = TextGray
                )
                Text(
                    "Nenhum QR Code cadastrado",
                    color = TextGray,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Escaneie com a câmera para adicionar",
                    color = TextGray.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                
                // Botão para escanear com câmera
                Button(
                    onClick = onScanWithCameraClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear com Câmera", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Função auxiliar para gerar QR Code bitmap em background
 */
suspend fun generateQRCodeBitmap(
    text: String,
    density: androidx.compose.ui.unit.Density,
    context: android.content.Context,
    onResult: (Bitmap?) -> Unit
) {
    kotlinx.coroutines.withContext(Dispatchers.Default) {
        val screenWidth = with(density) {
            context.resources.displayMetrics.widthPixels.dp.toPx().toInt()
        }
        
        // Calcular tamanho ideal: usar 1.5x da tela, mas limitado entre 800 e 1200 pixels
        // Isso garante boa legibilidade sem consumir muita memória
        val desiredSize = (screenWidth * 1.5).toInt()
        val minSize = 800  // Tamanho mínimo para boa legibilidade
        val maxSize = 1200 // Tamanho máximo para evitar OutOfMemory
        val qrCodeSize = desiredSize.coerceIn(minSize, maxSize)
        
        // Detectar se é Base64 e decodificar para bytes originais
        val bitmap = try {
            // Tentar decodificar como Base64
            val decodedBytes = android.util.Base64.decode(text, android.util.Base64.NO_WRAP)
            println("📦 generateQRCodeBitmap: Detectado Base64, decodificando ${decodedBytes.size} bytes")
            // Se decodificou com sucesso, usar bytes originais para gerar QR Code
            QRCodeGenerator.generateQRCodeFromBytes(decodedBytes, qrCodeSize, qrCodeSize)
        } catch (e: Exception) {
            // Se não for Base64 válido, tratar como texto normal
            println("📝 generateQRCodeBitmap: Não é Base64, tratando como texto normal")
            QRCodeGenerator.generateQRCode(text, qrCodeSize, qrCodeSize)
        }
        
        onResult(bitmap)
    }
}

