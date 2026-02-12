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
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
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
            TopAppBar(
                title = { Text("Enviar Feedback", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = DarkBackground
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

            GlassCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Compartilhe suas sugestões, ideias ou problemas que você encontrou no app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
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
                                color = TextGray
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = TextGray,
                            focusedPlaceholderColor = TextGray,
                            unfocusedPlaceholderColor = TextGray
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
                            containerColor = NeonGreen,
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
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))

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
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))

                SectionHeader(title = "Meus Feedbacks")

                GlassCard {
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
                            tint = TextGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Nenhum feedback enviado ainda",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextGray
                        )
                    }
                }
            }

            // Mensagens
            message?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeonGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(16.dp),
                        color = Color.Black
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
                title = { Text("Editar Feedback", color = TextWhite) },
                text = {
                    OutlinedTextField(
                        value = textoEditado,
                        onValueChange = { textoEditado = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mensagem", color = TextGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = TextGray,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = TextGray
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Text("Salvar", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { feedbackParaEditar = null }) {
                        Text("Cancelar", color = TextGray)
                    }
                },
                containerColor = DarkSurface
            )
        }

        // Diálogo de confirmação de exclusão
        feedbackParaExcluir?.let { feedback ->
            AlertDialog(
                onDismissRequest = { feedbackParaExcluir = null },
                title = { Text("Excluir Feedback", color = TextWhite) },
                text = { Text("Tem certeza que deseja excluir este feedback?", color = TextGray) },
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
                        Text("Cancelar", color = TextGray)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

