package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.data.models.Feedback
import com.controleescalas.app.ui.components.FeedbackCard
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.FeedbackViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeedbackScreen(
    baseId: String,
    adminId: String,
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = viewModel()
) {
    val feedbacks by viewModel.feedbacks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val error by viewModel.error.collectAsState()

    var mensagemTexto by remember { mutableStateOf("") }
    var adminNome by remember { mutableStateOf<String?>(null) }
    var baseNome by remember { mutableStateOf<String?>(null) }
    
    // Estados para edição/exclusão
    var feedbackParaEditar by remember { mutableStateOf<Feedback?>(null) }
    var feedbackParaExcluir by remember { mutableStateOf<Feedback?>(null) }
    var textoEditado by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Carregar nome do admin e base
    LaunchedEffect(baseId, adminId) {
        scope.launch {
            try {
                val baseRepository = BaseRepository()
                val motoristaRepository = MotoristaRepository()

                val base = baseRepository.getBase(baseId)
                baseNome = base?.nome ?: "Transportadora"

                val motoristaNome = motoristaRepository.getMotoristaNome(adminId, baseId)
                adminNome = motoristaNome ?: "Admin"

                // Carregar feedbacks
                viewModel.carregarMeusFeedbacks(adminId)
            } catch (e: Exception) {
                println("❌ Erro ao carregar dados: ${e.message}")
            }
        }
    }

    // Limpar mensagens
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Enviar Feedback",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
)
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seção de enviar feedback
            SectionHeader(title = "Enviar Feedback")

            AppCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Compartilhe suas sugestões, ideias ou problemas que você encontrou no app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGrayLightMode
                    )

                    OutlinedTextField(
                        value = mensagemTexto,
                        onValueChange = { mensagemTexto = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = {
                            Text(
                                "Sua mensagem...",
                                color = TextGrayLightMode
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextBlack,
                            unfocusedTextColor = TextBlack,
                            focusedBorderColor = MeliBlue,
                            unfocusedBorderColor = CardBorderLight,
                            focusedPlaceholderColor = TextDisabled,
                            unfocusedPlaceholderColor = TextDisabled
                        ),
                        maxLines = 5
                    )

                    Button(
                        onClick = {
                            if (adminNome != null && baseNome != null) {
                                viewModel.criarFeedback(
                                    baseId = baseId,
                                    adminId = adminId,
                                    adminNome = adminNome!!,
                                    baseNome = baseNome!!,
                                    mensagem = mensagemTexto
                                )
                                mensagemTexto = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = mensagemTexto.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeliBlue,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enviar Feedback", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Seção de meus feedbacks
            if (feedbacks.isNotEmpty()) {
                HorizontalDivider(color = TextGrayLightMode.copy(alpha = 0.2f))

                SectionHeader(title = "Meus Feedbacks")

                feedbacks.forEach { feedback ->
                    FeedbackCard(
                        feedback = feedback,
                        isSuperAdmin = false,
                        isOwner = feedback.adminId == adminId,
                        onEditar = {
                            feedbackParaEditar = feedback
                            textoEditado = feedback.mensagem
                        },
                        onExcluir = {
                            feedbackParaExcluir = feedback
                        }
                    )
                }
            } else if (!isLoading) {
                HorizontalDivider(color = TextGrayLightMode.copy(alpha = 0.2f))

                SectionHeader(title = "Meus Feedbacks")

                AppCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Feedback,
                            contentDescription = null,
                            tint = TextGrayLightMode,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Nenhum feedback enviado ainda",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextGrayLightMode
                        )
                    }
                }
            }

            // Mensagens
            message?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeliBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(16.dp),
                        color = Color.White
                    )
                }
            }

            error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(16.dp),
                        color = Color.White
                    )
                }
            }
        }

        // Diálogo de edição
        feedbackParaEditar?.let { feedback ->
            AlertDialog(
                onDismissRequest = { feedbackParaEditar = null },
                title = { Text("Editar Feedback", color = TextBlack) },
                text = {
                    OutlinedTextField(
                        value = textoEditado,
                        onValueChange = { textoEditado = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mensagem", color = TextGrayLightMode) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextBlack,
                            unfocusedTextColor = TextBlack,
                            focusedBorderColor = MeliBlue,
                            unfocusedBorderColor = CardBorderLight,
                            focusedLabelColor = MeliBlue,
                            unfocusedLabelColor = TextGrayLightMode
                        ),
                        maxLines = 5
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.editarFeedback(
                                feedback.id,
                                textoEditado,
                                adminId,
                                false
                            )
                            feedbackParaEditar = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeliBlue)
                    ) {
                        Text("Salvar", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { feedbackParaEditar = null }) {
                        Text("Cancelar", color = TextGrayLightMode)
                    }
                },
                containerColor = Color.White
            )
        }

        // Diálogo de confirmação de exclusão
        feedbackParaExcluir?.let { feedback ->
            AlertDialog(
                onDismissRequest = { feedbackParaExcluir = null },
                title = { Text("Excluir Feedback", color = TextBlack) },
                text = { Text("Tem certeza que deseja excluir este feedback?", color = TextGrayLightMode) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.excluirFeedback(feedback.id, adminId, false)
                            feedbackParaExcluir = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Excluir", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { feedbackParaExcluir = null }) {
                        Text("Cancelar", color = TextGrayLightMode)
                    }
                },
                containerColor = Color.White
            )
        }
    }
}

