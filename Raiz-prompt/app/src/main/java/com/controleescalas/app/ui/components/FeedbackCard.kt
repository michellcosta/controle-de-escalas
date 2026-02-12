package com.controleescalas.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.Feedback
import com.controleescalas.app.data.models.FeedbackStatus
import com.controleescalas.app.ui.components.ColoredAvatar
import com.controleescalas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card de feedback para exibição
 */
@Composable
fun FeedbackCard(
    feedback: Feedback,
    isSuperAdmin: Boolean = false,
    isOwner: Boolean = false,
    onCurtir: (() -> Unit)? = null,
    onEditar: (() -> Unit)? = null,
    onExcluir: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusIcon = when (feedback.status) {
        FeedbackStatus.NOVO -> Icons.Default.FiberNew
        FeedbackStatus.LIDO -> Icons.Default.Visibility
        FeedbackStatus.CURTIDO -> Icons.Default.ThumbUp
    }

    val statusColor = when (feedback.status) {
        FeedbackStatus.NOVO -> NeonBlue
        FeedbackStatus.LIDO -> NeonOrange
        FeedbackStatus.CURTIDO -> NeonGreen
    }

    val statusText = when (feedback.status) {
        FeedbackStatus.NOVO -> "🆕 Novo"
        FeedbackStatus.LIDO -> "👁️ Lido"
        FeedbackStatus.CURTIDO -> "👍 Curtido"
    }

    val dataFormatada = remember(feedback.data) {
        val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        sdf.format(Date(feedback.data))
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho com avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSuperAdmin) {
                        ColoredAvatar(
                            name = feedback.adminNome,
                            size = 40
                        )
                        Column {
                            Text(
                                text = feedback.adminNome,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = feedback.baseNome,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                        }
                    } else {
                        ColoredAvatar(
                            name = "Admin",
                            size = 40
                        )
                        Column {
                            Text(
                                text = "Feedback",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Status
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Mensagem
            Text(
                text = feedback.mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite,
                modifier = Modifier.fillMaxWidth()
            )

            // Rodapé
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dataFormatada,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botões de editar/excluir (se for dono ou super admin)
                    if ((isOwner || isSuperAdmin) && (onEditar != null || onExcluir != null)) {
                        if (onEditar != null) {
                            IconButton(
                                onClick = onEditar,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = NeonBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (onExcluir != null) {
                            IconButton(
                                onClick = onExcluir,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Excluir",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Botão curtir (apenas para super admin e se não estiver curtido)
                    if (isSuperAdmin && feedback.status != FeedbackStatus.CURTIDO && onCurtir != null) {
                        TextButton(onClick = onCurtir) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "Curtir",
                                tint = NeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Curtir",
                                color = NeonGreen,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Mostrar quem curtiu (se foi curtido)
                    if (feedback.status == FeedbackStatus.CURTIDO && feedback.curtidoEm != null) {
                        val curtidoFormatado = remember(feedback.curtidoEm) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            sdf.format(Date(feedback.curtidoEm))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Curtido em $curtidoFormatado",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonGreen.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

