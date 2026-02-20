package com.controleescalas.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.AssistenteViewModel
import com.controleescalas.app.ui.viewmodels.ChatMessage
import com.controleescalas.app.ui.viewmodels.OnAddToScaleAction
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistenteScreen(
    baseId: String,
    onBack: () -> Unit = {},
    onAddToScaleAction: OnAddToScaleAction? = null,
    viewModel: AssistenteViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(onAddToScaleAction) { viewModel.setOnAddToScaleAction(onAddToScaleAction) }
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lastPhotoPath = remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastPhotoPath.value?.let { path ->
                selectedImageUri = Uri.parse("file://$path")
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) selectedImageUri = uri
    }

    fun launchCamera() {
        val photoFile = File(context.cacheDir, "assistente_foto_${System.currentTimeMillis()}.jpg")
        lastPhotoPath.value = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(uri)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Assistente", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Pergunte sobre escalas e motoristas",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Ex: \"Quanto tempo para João chegar?\" ou envie uma foto com o que deseja fazer",
                            color = TextGray.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg ->
                        MessageBubble(msg = msg)
                    }
                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    color = DarkSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = NeonGreen,
                                            strokeWidth = 2.dp
                                        )
                                        Text("...", color = TextGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = DarkSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { launchCamera() },
                            enabled = !isLoading
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Câmera",
                                tint = if (isLoading) TextGray else TextWhite
                            )
                        }
                        IconButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            enabled = !isLoading
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Galeria",
                                tint = if (isLoading) TextGray else TextWhite
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Áudio em breve",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Microfone",
                                tint = if (isLoading) TextGray else TextWhite
                            )
                        }
                    }
                    if (selectedImageUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonGreen.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "Imagem anexada",
                                        color = TextWhite,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remover imagem",
                                        tint = TextGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(DarkSurfaceVariant, RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextWhite),
                            cursorBrush = SolidColor(NeonGreen),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            if (selectedImageUri != null)
                                                "Digite o que deseja fazer com a imagem..."
                                            else
                                                "Digite ou envie mídia...",
                                            color = TextGray,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        val canSend = !isLoading && (inputText.isNotBlank() || selectedImageUri != null)
                        IconButton(
                            onClick = {
                                selectedImageUri?.let { uri ->
                                    viewModel.sendMessageWithImage(baseId, inputText.ifBlank { null }, uri)
                                    selectedImageUri = null
                                    inputText = ""
                                } ?: run {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText, baseId)
                                        inputText = ""
                                    }
                                }
                            },
                            enabled = canSend
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Enviar",
                                tint = if (canSend) NeonGreen else TextGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) NeonGreen.copy(alpha = 0.3f) else DarkSurface
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(12.dp),
                color = TextWhite,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}
