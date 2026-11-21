package com.controleescalas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.DisponibilidadeMotorista
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.utils.BrasilDate

/**
 * Card de disponibilidade POR TURNO: o motorista marca, para cada turno
 * cadastrado da base, se está disponível ou não para o dia seguinte.
 *
 * Compatibilidade: docs antigos sem respostas por turno mostram o estado
 * legado (sim/não único) como resposta para todos os turnos.
 */
@Composable
fun DisponibilidadeCard(
    data: String, // YYYY-MM-DD
    turnos: List<String>,
    minhaResposta: DisponibilidadeMotorista?,
    onMarcarTurno: (turno: String, disponivel: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val dataFormatada = remember(data) { BrasilDate.yyyyMmDdParaDdMmYyyy(data) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Disponibilidade por turno",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dataFormatada,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGrayLightMode
                    )
                }
            }

            Text(
                text = "Em quais turnos você pode trabalhar amanhã?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            if (turnos.isEmpty()) {
                Text(
                    text = "Nenhum turno cadastrado pela transportadora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrayLightMode
                )
            } else {
                // Uma linha por turno: nome + estado + botões
                turnos.forEach { turno ->
                    val estado = minhaResposta?.estadoNoTurno(turno)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Indicador de estado
                            val (icone, cor) = when (estado) {
                                true -> Icons.Default.CheckCircle to NeonGreen
                                false -> Icons.Default.Close to StatusError
                                else -> Icons.Default.HelpOutline to TextDisabled
                            }
                            Icon(
                                imageVector = icone,
                                contentDescription = null,
                                tint = cor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = turno,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EscolhaSimNaoBotao(
                                texto = "SIM",
                                selecionado = estado == true,
                                corSelecionado = NeonGreen,
                                onClick = { onMarcarTurno(turno, true) }
                            )
                            EscolhaSimNaoBotao(
                                texto = "NÃO",
                                selecionado = estado == false,
                                corSelecionado = StatusError,
                                onClick = { onMarcarTurno(turno, false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EscolhaSimNaoBotao(
    texto: String,
    selecionado: Boolean,
    corSelecionado: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selecionado) corSelecionado else Color.White,
            contentColor = if (selecionado) Color.White else TextGrayLightMode
        ),
        border = if (selecionado) null else BorderStroke(1.dp, CardBorderLight),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
