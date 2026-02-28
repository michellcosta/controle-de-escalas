package com.controleescalas.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.controleescalas.app.ui.viewmodels.OnBulkScaleActions
import com.controleescalas.app.ui.viewmodels.OnSendNotification
import com.controleescalas.app.ui.viewmodels.BulkScaleAction
import java.util.Locale
import java.io.File
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistenteScreen(
    baseId: String,
    userId: String = "",
    userRole: String = "",
    onBack: () -> Unit = {},
    onAddToScaleAction: OnAddToScaleAction? = null,
    onUpdateInScaleAction: OnUpdateInScaleAction? = null,
    onBulkActions: OnBulkScaleActions? = null,
    onSendNotification: OnSendNotification? = null,
    onInputFocusChange: (Boolean) -> Unit = {},
    turno: String = "AM",
    viewModel: AssistenteViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(onAddToScaleAction) { viewModel.setOnAddToScaleAction(onAddToScaleAction) }
    LaunchedEffect(onUpdateInScaleAction) { viewModel.setOnUpdateInScaleAction(onUpdateInScaleAction) }
    LaunchedEffect(onBulkActions) { viewModel.setOnBulkActions(onBulkActions) }
    LaunchedEffect(onSendNotification) { viewModel.setOnSendNotification(onSendNotification) }
    LaunchedEffect(turno) { viewModel.setCurrentTurno(turno) }
    LaunchedEffect(userId, userRole) {
        viewModel.setUserIdentity(userId, userRole)
    }
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
        try {
            if (success) {
                val path = lastPhotoPath.value
                if (!path.isNullOrBlank()) {
                    selectedImageUri = Uri.parse("file://$path")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AssistenteScreen", "TakePicture result: ${e.message}", e)
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
                title = { 
                    Column {
                        Text("Assistente IA", color = TextWhite, style = MaterialTheme.typography.titleMedium)
                        Text("Online e pronto para ajudar", color = NeonGreen, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepBlue, DarkBackground, DarkBackground)
                    )
                )
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            "Assistente IA",
                            color = NeonGreen,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Pronto para organizar sua escala hoje.",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Dê comandos de voz, digite ou envie uma foto.",
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messages) { msg ->
                            MessageBubble(msg = msg)
                        }
                        if (isLoading) {
                            item {
                                TypingIndicator()
                            }
                        }
                    }
                }

                // Barra de Entrada Flutuante
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Linha de Ações (fora da barra de texto)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { launchCamera() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(40.dp)
                                .background(DarkSurface.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.CameraAlt, "Câmera", tint = if (isLoading) TextGray else TextWhite, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(40.dp)
                                .background(DarkSurface.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, "Galeria", tint = if (isLoading) TextGray else TextWhite, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { startSpeechRecognition() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(40.dp)
                                .background(DarkSurface.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                if (isListening) "Ouvindo..." else "Falar",
                                tint = if (isListening) NeonGreen else if (isLoading) TextGray else TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(32.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { onInputFocusChange(it.isFocused) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextWhite),
                                    cursorBrush = SolidColor(NeonGreen),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences
                                    ),
                                    decorationBox = { inner ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (inputText.isEmpty() && selectedImageUri == null) {
                                                Text("Fale com o assistente...", color = TextGray, style = MaterialTheme.typography.bodyLarge)
                                            }
                                            inner()
                                        }
                                    }
                                )

                                val canSend = !isLoading && (inputText.isNotBlank() || selectedImageUri != null)
                                FloatingActionButton(
                                    onClick = {
                                        if (canSend) {
                                            selectedImageUri?.let { uri ->
                                                viewModel.sendMessageWithImage(baseId, inputText.ifBlank { null }, uri)
                                                selectedImageUri = null
                                                inputText = ""
                                            } ?: run {
                                                viewModel.sendMessage(inputText, baseId)
                                                inputText = ""
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    containerColor = if (canSend) NeonGreen else DarkSurfaceVariant,
                                    contentColor = if (canSend) DarkBackground else TextGray,
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                                ) {
                                    Icon(Icons.Default.Send, "Enviar", modifier = Modifier.size(20.dp))
                                }
                            }

                            if (selectedImageUri != null) {
                                Surface(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = NeonGreen.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                        Text("Imagem anexada", color = TextWhite, style = MaterialTheme.typography.bodySmall)
                                        IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, "Remover", tint = TextGray, modifier = Modifier.size(14.dp))
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

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { if (isUser) it else -it }) + fadeIn()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                AvatarIcon(Icons.Default.AutoAwesome, NeonGreen)
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Surface(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 20.dp
                ),
                color = if (isUser) NeonGreen.copy(alpha = 0.2f) else DarkSurface.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    if (isUser) NeonGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = msg.text,
                    modifier = Modifier.padding(14.dp),
                    color = TextWhite,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        letterSpacing = 0.3.sp
                    )
                )
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                AvatarIcon(Icons.Default.Person, Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun AvatarIcon(icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    @Composable
    fun animateDot(delay: Int): Float {
        return infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -10f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot"
        ).value
    }

    Row(
        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(animateDot(0))
        Dot(animateDot(150))
        Dot(animateDot(300))
    }
}

@Composable
fun Dot(offsetY: Float) {
    Surface(
        modifier = Modifier
            .size(6.dp)
            .offset(y = offsetY.dp),
        shape = CircleShape,
        color = NeonGreen.copy(alpha = 0.6f)
    ) {}
}
