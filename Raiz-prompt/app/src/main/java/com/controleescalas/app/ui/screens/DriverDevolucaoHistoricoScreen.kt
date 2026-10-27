package com.controleescalas.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.DevolucaoImageManager
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DevolucaoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DriverDevolucaoHistoricoScreen(
    baseId: String,
    motoristaId: String,
    onDismiss: () -> Unit,
    onNavigateToDetalhes: (String) -> Unit = {},
    viewModel: DevolucaoViewModel = viewModel()
) {
    var filtroData by remember { mutableStateOf<String?>(null) } // "hoje", "semana", "mes"
    var idPacoteFiltro by remember { mutableStateOf("") }
    val devolucoes by viewModel.minhasDevolucoes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    var showConfirmDelete by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageManager = remember { DevolucaoImageManager(context) }
    
    // Estado para gerenciar seleção de fotos por devolução
    var devolucaoSelecionadaParaFoto by remember { mutableStateOf<String?>(null) }
    var refreshFotos by remember { mutableStateOf(0) } // Para forçar atualização das fotos
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) } // URI temporária para foto da câmera
    
    // Verificar se há filtros ativos
    val temFiltrosAtivos = filtroData != null || idPacoteFiltro.isNotBlank()
    
    // Debounce para busca de ID
    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Função para recarregar dados
    fun recarregarDados() {
        viewModel.carregarMinhasDevolucoesComFiltros(
            baseId = baseId,
            motoristaId = motoristaId,
            filtroData = filtroData,
            idPacoteFiltro = idPacoteFiltro.takeIf { it.isNotBlank() }
        )
    }
    
    LaunchedEffect(filtroData, idPacoteFiltro) {
        debounceJob?.cancel()
        debounceJob = launch {
            delay(300)
            recarregarDados()
        }
    }
    
    LaunchedEffect(Unit) {
        recarregarDados()
    }
    
    // Mensagens de sucesso/erro
    LaunchedEffect(message) {
        message?.let { msg ->
            if (msg.isNotBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Short
                    )
                    delay(3500)
                    viewModel.limparMensagem()
                }
            }
        }
    }
    
    LaunchedEffect(error) {
        error?.let { errorMsg ->
            if (errorMsg.isNotBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = errorMsg,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }
    
    var showFilterMenu by remember { mutableStateOf(false) }
    var showShareDropdown by remember { mutableStateOf<String?>(null) } // ID da devolução para qual o dropdown está aberto
    
    // Launcher para câmera (usa TakePicture para alta qualidade)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempPhotoUri?.let { uri ->
                devolucaoSelecionadaParaFoto?.let { devolucaoId ->
                    coroutineScope.launch {
                        val index = imageManager.addFotoFromUri(devolucaoId, uri)
                        if (index != null) {
                            imageManager.limparCacheMiniaturas()
                            refreshFotos++ // Forçar atualização
                            snackbarHostState.showSnackbar(
                                message = "Foto adicionada com sucesso",
                                duration = SnackbarDuration.Short
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                message = "Erro ao salvar foto",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
                devolucaoSelecionadaParaFoto = null
                tempPhotoUri = null
            }
        } else {
            devolucaoSelecionadaParaFoto = null
            tempPhotoUri = null
        }
    }
    
    // Launcher para galeria
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
                devolucaoSelecionadaParaFoto?.let { devolucaoId ->
                    coroutineScope.launch {
                        val index = imageManager.addFotoFromUri(devolucaoId, it)
                        if (index != null) {
                            imageManager.limparCacheMiniaturas()
                            refreshFotos++ // Forçar atualização
                        snackbarHostState.showSnackbar(
                            message = "Foto adicionada com sucesso",
                            duration = SnackbarDuration.Short
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            message = "Erro ao salvar foto",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
            devolucaoSelecionadaParaFoto = null
        }
    }
    
    // Função para compartilhar apenas as fotos (sem legenda)
    fun compartilharFotos(devolucao: com.controleescalas.app.data.models.Devolucao) {
        try {
            val fotosUris = imageManager.getAllFotosUris(devolucao.id)
            println("📤 DriverDevolucaoHistoricoScreen: Compartilhando apenas fotos - ${fotosUris.size} foto(s)")
            
            if (fotosUris.isNotEmpty()) {
                // Conceder permissões ANTES de criar o Intent
                fotosUris.forEachIndexed { index, uri ->
                    try {
                        context.grantUriPermission(
                            "com.whatsapp",
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        println("⚠️ Erro ao conceder permissão para WhatsApp: ${e.message}")
                    }
                }
                
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    setPackage("com.whatsapp")
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fotosUris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    // Fallback genérico
                    fotosUris.forEach { uri ->
                        try {
                            context.grantUriPermission(null, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) {}
                    }
                    
                    val intentGenerico = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fotosUris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intentGenerico, "Compartilhar via"))
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Nenhuma foto para compartilhar",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ Erro ao compartilhar fotos: ${e.message}")
            e.printStackTrace()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Erro ao compartilhar fotos: ${e.message}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    
    // Função para compartilhar apenas a legenda (sem fotos)
    fun compartilharLegenda(devolucao: com.controleescalas.app.data.models.Devolucao) {
        try {
            // Formatar o texto conforme solicitado
            val texto = buildString {
                appendLine("PACOTES DEVOLVIDOS")
                appendLine("🗓 ${devolucao.data} - ${devolucao.hora}")
                appendLine("📦 ${devolucao.quantidade} ${if (devolucao.quantidade == 1) "Unidade" else "Unidades"}")
                appendLine("_________________________")
                devolucao.idsPacotes.forEach { id ->
                    // Adicionar espaços à esquerda para alinhar os IDs
                    appendLine("     $id")
                }
            }
            println("📤 DriverDevolucaoHistoricoScreen: Compartilhando apenas legenda")
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, texto)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val intentGenerico = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, texto)
                }
                context.startActivity(Intent.createChooser(intentGenerico, "Compartilhar via"))
            }
        } catch (e: Exception) {
            println("❌ Erro ao compartilhar legenda: ${e.message}")
            e.printStackTrace()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Erro ao compartilhar legenda: ${e.message}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    
    // Função para compartilhar no WhatsApp (mantida para compatibilidade, mas não será mais usada)
    fun compartilharNoWhatsApp(devolucao: com.controleescalas.app.data.models.Devolucao) {
        try {
            val fotosUris = imageManager.getFotosUris(devolucao.id)
            println("📤 DriverDevolucaoHistoricoScreen: Compartilhando - ${fotosUris.size} foto(s)")
            
            // Formatar o texto conforme solicitado
            val texto = buildString {
                appendLine("PACOTES DEVOLVIDOS")
                appendLine("🗓 ${devolucao.data} - ${devolucao.hora}")
                appendLine("📦 ${devolucao.quantidade} ${if (devolucao.quantidade == 1) "Unidade" else "Unidades"}")
                appendLine("_________________________")
                devolucao.idsPacotes.forEach { id ->
                    appendLine("📦 $id")
                }
            }
            println("📤 DriverDevolucaoHistoricoScreen: Texto formatado:\n$texto")
            
            if (fotosUris.isNotEmpty()) {
                // Compartilhar com fotos
                println("📤 DriverDevolucaoHistoricoScreen: Preparando compartilhamento com ${fotosUris.size} foto(s)")
                
                // Conceder permissões ANTES de criar o Intent
                fotosUris.forEachIndexed { index, uri ->
                    try {
                        println("📤 Concedendo permissão para URI $index: $uri")
                        context.grantUriPermission(
                            "com.whatsapp",
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        println("✅ Permissão concedida para WhatsApp: $uri")
                    } catch (e: Exception) {
                        println("⚠️ Erro ao conceder permissão para WhatsApp: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                // Usar ACTION_SEND_MULTIPLE para enviar as fotos
                // Nota: O WhatsApp não suporta texto associado a uma foto específica
                // quando há múltiplas imagens. O texto será aplicado a todas ou a nenhuma.
                // Vamos tentar adicionar o texto via ClipData, mas pode aparecer em todas as fotos.
                val clipData = android.content.ClipData.newUri(
                    context.contentResolver,
                    "Imagem",
                    fotosUris[0]
                )
                // Adicionar outras imagens ao ClipData
                for (i in 1 until fotosUris.size) {
                    clipData.addItem(android.content.ClipData.Item(fotosUris[i]))
                }
                // Adicionar texto ao ClipData (será associado à última imagem se possível)
                clipData.addItem(android.content.ClipData.Item(texto))
                
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    setPackage("com.whatsapp")
                    
                    // Adicionar todas as URIs das fotos
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fotosUris))
                    
                    // Adicionar ClipData com texto
                    setClipData(clipData)
                    
                    // Conceder permissões de leitura
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                println("📤 DriverDevolucaoHistoricoScreen: Intent criado, verificando se WhatsApp está disponível...")
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    println("✅ WhatsApp encontrado, iniciando compartilhamento...")
                    context.startActivity(intent)
                } else {
                    println("⚠️ WhatsApp não encontrado, usando compartilhamento genérico...")
                    // Se WhatsApp não estiver instalado, usar compartilhamento genérico
                    // Conceder permissões para qualquer app
                    fotosUris.forEach { uri ->
                        try {
                            context.grantUriPermission(
                                null,
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            println("⚠️ Erro ao conceder permissão genérica: ${e.message}")
                        }
                    }
                    
                    // Para compartilhamento genérico, usar ACTION_SEND_MULTIPLE
                    val intentGenerico = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fotosUris))
                        putExtra(Intent.EXTRA_TEXT, texto)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    val chooser = Intent.createChooser(intentGenerico, "Compartilhar via")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(chooser)
                }
            } else {
                // Se não houver fotos, compartilhar apenas texto
                println("📤 DriverDevolucaoHistoricoScreen: Compartilhando apenas texto (sem fotos)")
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, texto)
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    val intentGenerico = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, texto)
                    }
                    context.startActivity(Intent.createChooser(intentGenerico, "Compartilhar via"))
                }
            }
        } catch (e: Exception) {
            println("❌ DriverDevolucaoHistoricoScreen: Erro ao compartilhar: ${e.message}")
            e.printStackTrace()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Erro ao compartilhar: ${e.message}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Devoluções", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    // Menu de 3 pontos com filtros
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Filtros",
                                tint = TextWhite
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Hoje", color = if (filtroData == "hoje") NeonGreen else TextWhite)
                                        if (filtroData == "hoje") {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = if (filtroData == "hoje") null else "hoje"
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Esta Semana", color = if (filtroData == "semana") NeonGreen else TextWhite)
                                        if (filtroData == "semana") {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = if (filtroData == "semana") null else "semana"
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Este Mês", color = if (filtroData == "mes") NeonGreen else TextWhite)
                                        if (filtroData == "mes") {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = if (filtroData == "mes") null else "mes"
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Todos", color = if (filtroData == null) NeonGreen else TextWhite)
                                        if (filtroData == null) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = null
                                    showFilterMenu = false
                                }
                            )
                            if (temFiltrosAtivos) {
                                HorizontalDivider(color = TextGray.copy(alpha = 0.3f))
                                DropdownMenuItem(
                                    text = {
                                        Text("Limpar Filtros", color = TextGray)
                                    },
                                    onClick = {
                                        filtroData = null
                                        idPacoteFiltro = ""
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when {
                        isLoading && devolucoes.isEmpty() -> {
                            // Loading inicial - mostrar spinner centralizado
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NeonGreen)
                            }
                        }
                        devolucoes.isEmpty() && !temFiltrosAtivos -> {
                            // Estado vazio sem filtros - com campo de pesquisa no scroll
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                // Campo de pesquisa
                                item {
                                    OutlinedTextField(
                                        value = idPacoteFiltro,
                                        onValueChange = { idPacoteFiltro = it },
                                        label = { Text("Buscar por ID do Pacote", color = TextGray) },
                                        placeholder = { Text("Digite o ID...", color = TextGray.copy(alpha = 0.5f)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = "Buscar",
                                                tint = NeonBlue
                                            )
                                        },
                                        trailingIcon = {
                                            if (idPacoteFiltro.isNotBlank()) {
                                                IconButton(onClick = { idPacoteFiltro = "" }) {
                                                    Icon(
                                                        Icons.Default.Clear,
                                                        contentDescription = "Limpar busca",
                                                        tint = TextGray
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextWhite,
                                            unfocusedTextColor = TextWhite,
                                            focusedBorderColor = NeonBlue,
                                            unfocusedBorderColor = TextGray,
                                            focusedLabelColor = NeonBlue,
                                            unfocusedLabelColor = TextGray
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                
                                // Estado vazio
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 64.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = "Nenhuma devolução",
                                                modifier = Modifier.size(64.dp),
                                                tint = TextGray
                                            )
                                            Text(
                                                "Nenhuma devolução registrada",
                                                color = TextGray,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        devolucoes.isEmpty() && temFiltrosAtivos -> {
                            // Estado vazio com filtros ativos - com campo de pesquisa no scroll
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                // Campo de pesquisa
                                item {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = idPacoteFiltro,
                                            onValueChange = { idPacoteFiltro = it },
                                            label = { Text("Buscar por ID do Pacote", color = TextGray) },
                                            placeholder = { Text("Digite o ID...", color = TextGray.copy(alpha = 0.5f)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = "Buscar",
                                                    tint = NeonBlue
                                                )
                                            },
                                            trailingIcon = {
                                                if (idPacoteFiltro.isNotBlank()) {
                                                    IconButton(onClick = { idPacoteFiltro = "" }) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Limpar busca",
                                                            tint = TextGray
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = TextWhite,
                                                unfocusedTextColor = TextWhite,
                                                focusedBorderColor = NeonBlue,
                                                unfocusedBorderColor = TextGray,
                                                focusedLabelColor = NeonBlue,
                                                unfocusedLabelColor = TextGray
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        // Contador de resultados
                                        Text(
                                            "Nenhuma devolução encontrada com os filtros aplicados",
                                            color = TextGray,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                                
                                // Estado vazio
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 64.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SearchOff,
                                                contentDescription = "Nenhum resultado",
                                                modifier = Modifier.size(64.dp),
                                                tint = TextGray
                                            )
                                            Text(
                                                "Nenhuma devolução encontrada",
                                                color = TextGray,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "Tente ajustar os filtros",
                                                color = TextGray,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            // Lista de devoluções com campo de pesquisa no scroll
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                // Campo de pesquisa
                                item {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = idPacoteFiltro,
                                            onValueChange = { idPacoteFiltro = it },
                                            label = { Text("Buscar por ID do Pacote", color = TextGray) },
                                            placeholder = { Text("Digite o ID...", color = TextGray.copy(alpha = 0.5f)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = "Buscar",
                                                    tint = NeonBlue
                                                )
                                            },
                                            trailingIcon = {
                                                if (idPacoteFiltro.isNotBlank()) {
                                                    IconButton(onClick = { idPacoteFiltro = "" }) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Limpar busca",
                                                            tint = TextGray
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = TextWhite,
                                                unfocusedTextColor = TextWhite,
                                                focusedBorderColor = NeonBlue,
                                                unfocusedBorderColor = TextGray,
                                                focusedLabelColor = NeonBlue,
                                                unfocusedLabelColor = TextGray
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        // Contador de resultados
                                        if (devolucoes.isNotEmpty() || temFiltrosAtivos) {
                                            Text(
                                                if (devolucoes.isEmpty() && temFiltrosAtivos) {
                                                    "Nenhuma devolução encontrada com os filtros aplicados"
                                                } else {
                                                    "${devolucoes.size} devolução(ões) encontrada(s)"
                                                },
                                                color = if (devolucoes.isEmpty() && temFiltrosAtivos) TextGray else TextWhite,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                                
                                items(devolucoes, key = { it.id }) { devolucao ->
                                    // Miniaturas carregadas FORA da composição (IO): decodificar
                                    // foto em resolução cheia na thread principal travava o scroll.
                                    var todasFotos by remember(devolucao.id, refreshFotos) {
                                        mutableStateOf<List<Pair<Int, Bitmap>>>(emptyList())
                                    }
                                    LaunchedEffect(devolucao.id, refreshFotos) {
                                        todasFotos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            imageManager.getAllFotosMiniaturas(devolucao.id)
                                        }
                                    }
                                    
                                    GlassCard {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Linha superior: ID, data/hora e botão excluir
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onNavigateToDetalhes(devolucao.id)
                                                    },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    // Ícone indicativo
                                                    Icon(
                                                        Icons.Default.Inventory2,
                                                        contentDescription = null,
                                                        tint = NeonGreen,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            "${devolucao.quantidade} ${if (devolucao.quantidade == 1) "pacote" else "pacotes"}",
                                                            color = TextWhite,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            "${devolucao.data} - ${devolucao.hora}",
                                                            color = TextGray,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                                
                                                IconButton(
                                                    onClick = { showConfirmDelete = devolucao.id }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Excluir devolução",
                                                        tint = Color(0xFFEF4444)
                                                    )
                                                }
                                            }
                                            
                                            // Linha de botões: Câmera e Compartilhar
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Botão de adicionar foto
                                                Button(
                                                    onClick = {
                                                        devolucaoSelecionadaParaFoto = devolucao.id
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = NeonBlue,
                                                        contentColor = Color.Black
                                                    )
                                                ) {
                                                    Icon(
                                                        Icons.Default.CameraAlt,
                                                        contentDescription = "Adicionar fotos",
                                                        modifier = Modifier.size(24.dp),
                                                        tint = Color.Black
                                                    )
                                                }
                                                
                                                // Botão de compartilhar com dropdown
                                                Box(modifier = Modifier.weight(1f)) {
                                                    Button(
                                                        onClick = { 
                                                            showShareDropdown = if (showShareDropdown == devolucao.id) null else devolucao.id
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFF25D366), // Cor do WhatsApp
                                                            contentColor = Color.Black
                                                        )
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Share,
                                                            contentDescription = "Compartilhar",
                                                            modifier = Modifier.size(24.dp),
                                                            tint = Color.Black
                                                        )
                                                    }
                                                    
                                                    DropdownMenu(
                                                        expanded = showShareDropdown == devolucao.id,
                                                        onDismissRequest = { showShareDropdown = null }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Fotos") },
                                                            onClick = {
                                                                showShareDropdown = null
                                                                compartilharFotos(devolucao)
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    Icons.Default.CameraAlt,
                                                                    contentDescription = null
                                                                )
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Legenda") },
                                                            onClick = {
                                                                showShareDropdown = null
                                                                compartilharLegenda(devolucao)
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    Icons.Default.Description,
                                                                    contentDescription = null
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // Exibir todas as fotos se existirem
                                            if (todasFotos.isNotEmpty()) {
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(todasFotos.size, key = { i -> todasFotos[i].first }) { index ->
                                                        val (fotoIndex, bitmap) = todasFotos[index]
                                                        Box(
                                                            modifier = Modifier
                                                                .width(120.dp)
                                                                .height(120.dp)
                                                                .background(
                                                                    DarkSurface,
                                                                    shape = RoundedCornerShape(8.dp)
                                                                )
                                                        ) {
                                                            Image(
                                                                bitmap = bitmap.asImageBitmap(),
                                                                contentDescription = "Foto ${fotoIndex + 1}",
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            // Botão de remover foto
                                                            IconButton(
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        imageManager.removeFoto(devolucao.id, fotoIndex)
                                                                        imageManager.limparCacheMiniaturas()
                                                                        refreshFotos++
                                                                        snackbarHostState.showSnackbar("Foto removida")
                                                                    }
                                                                },
                                                                modifier = Modifier.align(Alignment.TopEnd)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Cancel,
                                                                    contentDescription = "Remover foto",
                                                                    tint = Color.Red,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // SnackbarHost para mensagens
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { snackbarData ->
                val messageText = snackbarData.visuals.message
                val isError = error != null || messageText.contains("erro", ignoreCase = true)
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (isError) Color(0xFFEF4444) else NeonGreen,
                    contentColor = if (isError) TextWhite else Color.Black
                )
            }
        }
        
        // Dialog para escolher câmera ou galeria (quando uma foto é selecionada)
        if (devolucaoSelecionadaParaFoto != null) {
            AlertDialog(
                onDismissRequest = { devolucaoSelecionadaParaFoto = null },
                containerColor = DarkSurface,
                title = {
                    Text("Escolher Foto", color = TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Como deseja adicionar a foto?",
                            color = TextWhite
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Criar URI temporária para a foto em alta qualidade
                                try {
                                    val photoFile = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
                                    val photoUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    tempPhotoUri = photoUri
                                    cameraLauncher.launch(photoUri)
                                } catch (e: Exception) {
                                    println("❌ Erro ao criar URI temporária para foto: ${e.message}")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Erro ao abrir câmera",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonBlue,
                                contentColor = TextWhite
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Câmera")
                        }
                        Button(
                            onClick = {
                                galleryLauncher.launch("image/*")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Galeria")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { devolucaoSelecionadaParaFoto = null }) {
                        Text("Cancelar", color = TextGray)
                    }
                }
            )
        }
        
        // Dialog de confirmação de exclusão
        showConfirmDelete?.let { devolucaoId ->
            val devolucao = devolucoes.find { it.id == devolucaoId }
            AlertDialog(
                onDismissRequest = { showConfirmDelete = null },
                containerColor = DarkSurface,
                title = {
                    Text("Excluir Devolução", color = TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Deseja realmente excluir esta devolução?",
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        devolucao?.let {
                            Text("${it.quantidade} ${if (it.quantidade == 1) "pacote" else "pacotes"}", color = TextGray)
                            Text("IDs: ${it.idsPacotes.joinToString(", ")}", color = TextGray)
                            Text("Data/Hora: ${it.data} - ${it.hora}", color = TextGray)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.excluirDevolucao(baseId, devolucaoId, motoristaId)
                            showConfirmDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Excluir", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDelete = null }) {
                        Text("Cancelar", color = TextGray)
                    }
                }
            )
        }
    }
}
