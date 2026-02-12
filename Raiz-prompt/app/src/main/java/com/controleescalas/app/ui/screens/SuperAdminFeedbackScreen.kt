package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.Feedback
import com.controleescalas.app.data.models.FeedbackStatus
import com.controleescalas.app.ui.components.FeedbackCard
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.FeedbackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminFeedbackScreen(
    superAdminId: String,
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = viewModel()
) {
    val feedbacks by viewModel.feedbacks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val error by viewModel.error.collectAsState()

    var filtroSelecionado by remember { mutableStateOf<FeedbackStatus?>(null) }
    
    // Estados para edição/exclusão
    var feedbackParaEditar by remember { mutableStateOf<Feedback?>(null) }
    var feedbackParaExcluir by remember { mutableStateOf<Feedback?>(null) }
    var textoEditado by remember { mutableStateOf("") }

    // Carregar feedbacks
    LaunchedEffect(Unit) {
        viewModel.carregarTodosFeedbacks()
    }

    // Marcar como lido quando visualizar
    LaunchedEffect(feedbacks) {
        feedbacks.forEach { feedback ->
            if (feedback.status == FeedbackStatus.NOVO) {
                viewModel.marcarComoLido(feedback.id)
            }
        }
    }

    // Filtrar feedbacks
    val feedbacksFiltrados = remember(feedbacks, filtroSelecionado) {
        if (filtroSelecionado == null) {
            feedbacks
        } else {
            feedbacks.filter { it.status == filtroSelecionado }
        }
    }

    // Contadores
    val contadores = remember(feedbacks) {
        val novos = feedbacks.count { it.status == FeedbackStatus.NOVO }
        val lidos = feedbacks.count { it.status == FeedbackStatus.LIDO }
        val curtidos = feedbacks.count { it.status == FeedbackStatus.CURTIDO }
        Triple(novos, lidos, curtidos)
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
                title = { Text("Feedbacks", color = TextWhite) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Filtros
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filtroSelecionado == null,
                    onClick = { filtroSelecionado = null },
                    label = { 
                        Text(
                            "Todos (${feedbacks.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = filtroSelecionado == FeedbackStatus.NOVO,
                    onClick = { filtroSelecionado = FeedbackStatus.NOVO },
                    label = { 
                        Text(
                            "Novos (${contadores.first})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonBlue,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filtroSelecionado == FeedbackStatus.LIDO,
                    onClick = { filtroSelecionado = FeedbackStatus.LIDO },
                    label = { 
                        Text(
                            "Lidos (${contadores.second})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonOrange,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filtroSelecionado == FeedbackStatus.CURTIDO,
                    onClick = { filtroSelecionado = FeedbackStatus.CURTIDO },
                    label = { 
                        Text(
                            "Curtidos (${contadores.third})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            // Lista de feedbacks
            if (isLoading && feedbacks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            } else if (feedbacksFiltrados.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard {
                        Column(
                            modifier = Modifier.padding(24.dp),
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
                                text = if (filtroSelecionado == null) {
                                    "Nenhum feedback"
                                } else {
                                    "Nenhum feedback ${filtroSelecionado?.name?.lowercase()}"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextGray,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feedbacksFiltrados) { feedback ->
                        FeedbackCard(
                            feedback = feedback,
                            isSuperAdmin = true,
                            isOwner = true,
                            onEditar = {
                                feedbackParaEditar = feedback
                                textoEditado = feedback.mensagem
                            },
                            onExcluir = {
                                feedbackParaExcluir = feedback
                            },
                            onCurtir = {
                                if (feedback.status != FeedbackStatus.CURTIDO) {
                                    viewModel.curtirFeedback(feedback.id, superAdminId)
                                }
                            }
                        )
                    }
                }
            }
            }

            // Mensagens (fora do Column, dentro do Box)
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
            }

            error?.let { err ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White
                ) {
                    Text(err)
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
                                    superAdminId,
                                    true
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
                                viewModel.excluirFeedback(feedback.id, superAdminId, true)
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
}

