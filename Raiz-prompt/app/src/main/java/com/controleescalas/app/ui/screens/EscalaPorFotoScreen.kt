package com.controleescalas.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.controleescalas.app.navigation.Routes
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.EscalaPorFotoViewModel
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import com.controleescalas.app.ui.viewmodels.ParsedEscalaEntry
import com.controleescalas.app.ui.viewmodels.ScaleViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscalaPorFotoScreen(
    baseId: String,
    turno: String,
    onBack: () -> Unit,
    navController: NavController,
    viewModel: EscalaPorFotoViewModel = viewModel(),
    operationalViewModel: OperationalViewModel? = null,
    scaleViewModel: ScaleViewModel? = null
) {
    val context = LocalContext.current
    val parsedEntries by viewModel.parsedEntries.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val error by viewModel.error.collectAsState()

    val lastPhotoPath = remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastPhotoPath.value?.let { path ->
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        viewModel.processImage(bitmap)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun launchCamera() {
        val photoFile = File(context.cacheDir, "escala_foto_${System.currentTimeMillis()}.jpg")
        lastPhotoPath.value = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(uri)
    }

    val useOperational = operationalViewModel != null

    LaunchedEffect(baseId) {
        viewModel.loadMotoristas(baseId)
    }

    LaunchedEffect(turno, useOperational) {
        if (useOperational) {
            operationalViewModel?.changeTurno(turno)
        } else {
            scaleViewModel?.loadData(baseId, turno)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar por Foto", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Formato: Nome / Vaga / Rota\nEx: Brendon / 02 / F7",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (parsedEntries.isEmpty() && !isProcessing) {
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tirar foto da escala")
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonGreen)
                        Spacer(Modifier.height(16.dp))
                        Text("Processando imagem...", color = TextGray)
                    }
                }
            }

            error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1515)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(err, color = Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
                }
            }

            if (parsedEntries.isNotEmpty()) {
                Text(
                    "Encontrados: ${parsedEntries.size} motoristas. Confira e aplique.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(parsedEntries) { _, entry ->
                        ParsedEntryCard(entry = entry)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearParsed()
                            launchCamera()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nova foto")
                    }
                    Button(
                        onClick = {
                            val ondaCount = (parsedEntries.maxOfOrNull { it.ondaIndex } ?: 0) + 1
                            if (useOperational) {
                                operationalViewModel!!.ensureOndasCount(turno, ondaCount)
                                parsedEntries.filter { it.motoristaId != null }.forEach { entry ->
                                    operationalViewModel.addMotoristaToOndaWithDetails(
                                        entry.ondaIndex,
                                        entry.motoristaId!!,
                                        entry.motoristaNome ?: entry.nome,
                                        entry.vaga,
                                        entry.rota
                                    )
                                }
                            } else {
                                scaleViewModel!!.ensureOndasCount(turno, ondaCount)
                                parsedEntries.filter { it.motoristaId != null }.forEach { entry ->
                                    scaleViewModel.addMotoristaToOndaWithDetails(
                                        entry.ondaIndex,
                                        entry.motoristaId!!,
                                        entry.motoristaNome ?: entry.nome,
                                        entry.vaga,
                                        entry.rota
                                    )
                                }
                            }
                            viewModel.clearParsed()
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        val validCount = parsedEntries.count { it.motoristaId != null }
                        Text("Aplicar ($validCount)")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParsedEntryCard(entry: ParsedEscalaEntry) {
    val isMatched = entry.motoristaId != null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMatched) DarkSurface else DarkSurface.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.nome,
                    color = TextWhite,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Vaga ${entry.vaga} | Rota ${entry.rota} | ${entry.ondaIndex + 1}ª onda",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                if (!isMatched) {
                    Text("Não encontrado", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6B6B))
                } else {
                    Text(entry.motoristaNome ?: "", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                }
            }
            Icon(
                if (isMatched) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isMatched) NeonGreen else NeonOrange,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
