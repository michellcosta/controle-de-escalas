package com.controleescalas.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.AssistenteViewModel
import com.controleescalas.app.ui.viewmodels.ChatMessage
import com.controleescalas.app.ui.viewmodels.OnAddToScaleAction
import com.controleescalas.app.ui.viewmodels.OnUpdateInScaleAction
import java.util.Locale
import java.io.File
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistenteScreen(
    baseId: String,
    onBack: () -> Unit = {},
    onAddToScaleAction: OnAddToScaleAction? = null,
    onUpdateInScaleAction: OnUpdateInScaleAction? = null,
    viewModel: AssistenteViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(onAddToScaleAction) { viewModel.setOnAddToScaleAction(onAddToScaleAction) }
    LaunchedEffect(onUpdateInScaleAction) { viewModel.setOnUpdateInScaleAction(onUpdateInScaleAction) }
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lastPhotoPath = remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var requestFocusAfterSpeech by remember { mutableStateOf(false) }

    fun runRecognition() {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                recognizer.destroy()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala reconhecida. Tente novamente."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo esgotado. Fale novamente."
                    SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio."
                    SpeechRecognizer.ERROR_CLIENT -> "Erro no reconhecimento."
                    else -> "Não foi possível ouvir. Tente novamente."
                }
                scope.launch { snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short) }
            }
            override fun onResults(bundle: Bundle?) {
                val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = results?.firstOrNull()?.trim()
                recognizer.destroy()
                scope.launch {
                    isListening = false
                    if (!text.isNullOrBlank()) inputText = text
                    requestFocusAfterSpeech = true
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = results?.firstOrNull()?.trim()
                if (!partial.isNullOrBlank()) {
                    scope.launch { inputText = partial }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    LaunchedEffect(requestFocusAfterSpeech) {
        if (requestFocusAfterSpeech) {
            requestFocusAfterSpeech = false
            delay(100)
            focusRequester.requestFocus()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runRecognition()
        else scope.launch { snackbarHostState.showSnackbar(message = "Permissão de microfone necessária para falar.", duration = SnackbarDuration.Short) }
    }

    fun startSpeechRecognition() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            SpeechRecognizer.isRecognitionAvailable(context) -> runRecognition()
            else -> scope.launch { snackbarHostState.showSnackbar(message = "Reconhecimento de voz não disponível neste dispositivo.", duration = SnackbarDuration.Short) }
        }
    }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(bottom = 12.dp),
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
                            onClick = { startSpeechRecognition() },
                            enabled = !isLoading
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = if (isListening) "Ouvindo..." else "Falar",
                                tint = when {
                                    isLoading -> TextGray
                                    isListening -> NeonGreen
                                    else -> TextWhite
                                }
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
                                .focusRequester(focusRequester)
                                .background(DarkSurfaceVariant, RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextWhite),
                            cursorBrush = SolidColor(NeonGreen),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            when {
                                                selectedImageUri != null -> "Digite o que deseja fazer com a imagem..."
                                                isListening -> "Ouvindo... (o que você disser aparecerá aqui)"
                                                else -> "Digite ou envie mídia..."
                                            },
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
            modifier = Modifier.widthIn(max = 320.dp),
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
