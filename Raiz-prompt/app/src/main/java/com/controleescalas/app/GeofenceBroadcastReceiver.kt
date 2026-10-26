package com.controleescalas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.controleescalas.app.data.NotifyStatusChangeWorker
import com.controleescalas.app.data.repositories.MotoristaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BroadcastReceiver para receber eventos de geofencing
 * ✅ CORREÇÃO: Agora atualiza status diretamente no Firestore quando detecta geofence
 * Funciona mesmo quando o app está em segundo plano
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent?.hasError() == true) {
            Log.e(TAG, "Erro no geofencing: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent?.geofenceTransition ?: return
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        Log.d(TAG, "Transição de geofence: $geofenceTransition")
        Log.d(TAG, "Geofences acionados: ${triggeringGeofences.size}")

        // goAsync() garante que o trabalho assíncrono complete mesmo com app fechado
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                when (geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        handleGeofenceEnter(context, triggeringGeofences)
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        handleGeofenceExit(context, triggeringGeofences)
                    }
                    Geofence.GEOFENCE_TRANSITION_DWELL -> {
                        handleGeofenceDwell(context, triggeringGeofences)
                    }
                    else -> {
                        Log.w(TAG, "Transição de geofence não reconhecida: $geofenceTransition")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Lidar com entrada em geofence
     */
    private suspend fun handleGeofenceEnter(context: Context, triggeringGeofences: List<Geofence>) {
        triggeringGeofences.forEach { geofence ->
            when (geofence.requestId) {
                com.controleescalas.app.data.GeofencingService.GEOFENCE_GALPAO_ID -> {
                    Log.d(TAG, "✅ Motorista entrou no galpão (via geofence)")
                    updateStatusForGeofence(
                        context,
                        "CHEGUEI",
                        "Chegou ao galpão (automático)",
                        listOf("A_CAMINHO", "ESTACIONAMENTO")
                    )
                }
                com.controleescalas.app.data.GeofencingService.GEOFENCE_ESTACIONAMENTO_ID -> {
                    Log.d(TAG, "✅ Motorista entrou no estacionamento (via geofence)")
                    updateStatusForGeofence(
                        context,
                        "ESTACIONAMENTO",
                        "Chegou ao estacionamento (automático)",
                        listOf("IR_ESTACIONAMENTO")
                    )
                }
            }
        }
    }

    /**
     * Lidar com saída de geofence
     */
    private fun handleGeofenceExit(context: Context, triggeringGeofences: List<Geofence>) {
        triggeringGeofences.forEach { geofence ->
            when (geofence.requestId) {
                com.controleescalas.app.data.GeofencingService.GEOFENCE_GALPAO_ID -> {
                    Log.d(TAG, "📍 Motorista saiu do galpão")
                }
                com.controleescalas.app.data.GeofencingService.GEOFENCE_ESTACIONAMENTO_ID -> {
                    Log.d(TAG, "📍 Motorista saiu do estacionamento")
                }
            }
        }
    }

    /**
     * Lidar com permanência em geofence (dwell)
     */
    private suspend fun handleGeofenceDwell(context: Context, triggeringGeofences: List<Geofence>) {
        triggeringGeofences.forEach { geofence ->
            when (geofence.requestId) {
                com.controleescalas.app.data.GeofencingService.GEOFENCE_GALPAO_ID -> {
                    Log.d(TAG, "✅ Motorista permanece no galpão (dwell)")
                    updateStatusForGeofence(
                        context,
                        "CHEGUEI",
                        "Chegou ao galpão (automático)",
                        listOf("A_CAMINHO")
                    )
                }
                com.controleescalas.app.data.GeofencingService.GEOFENCE_ESTACIONAMENTO_ID -> {
                    Log.d(TAG, "✅ Motorista permanece no estacionamento (dwell)")
                    updateStatusForGeofence(
                        context,
                        "ESTACIONAMENTO",
                        "Chegou ao estacionamento (automático)",
                        listOf("IR_ESTACIONAMENTO")
                    )
                }
            }
        }
    }
    
    /**
     * Atualizar status do motorista no Firestore quando detectar geofence
     * Suspend para garantir conclusão antes de finish() quando app está fechado
     */
    private suspend fun updateStatusForGeofence(
        context: Context,
        estado: String,
        mensagem: String,
        estadosDeOrigem: List<String>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val sessionManager = com.controleescalas.app.data.SessionManager(context)
                val sessions = sessionManager.getUserSessions()
                
                sessions.sessions.forEach { session ->
                    try {
                        val motoristaRepository = MotoristaRepository()
                        val statusAtual = motoristaRepository.getStatusMotorista(session.userId, session.baseId)
                        
                        // Regra: sem status = não monitorar - não atualizar geofence
                        if (statusAtual == null) {
                            Log.d(TAG, "⏭️ Motorista ${session.userName} sem status (não escalado), não atualizando geofence")
                            return@forEach
                        }
                        
                        val statusAtualEstado = statusAtual.estado

                        if (statusAtualEstado == "CARREGANDO" || statusAtualEstado == "CONCLUIDO") {
                            Log.d(TAG, "⏭️ Status atual ($statusAtualEstado) não muda sozinho para $estado")
                            return@forEach
                        }
                        if (statusAtualEstado !in estadosDeOrigem || statusAtualEstado == estado) {
                            Log.d(TAG, "⏭️ Status $statusAtualEstado não gera gravação de $estado")
                            return@forEach
                        }
                        
                        val success = motoristaRepository.updateStatusMotorista(
                            motoristaId = session.userId,
                            baseId = session.baseId,
                            estado = estado,
                            mensagem = mensagem
                        )
                        
                        if (success) {
                            Log.d(TAG, "✅ Status atualizado para $estado para motorista ${session.userName} (${session.baseName})")
                            NotifyStatusChangeWorker.enqueue(
                                context,
                                session.baseId,
                                session.userId,
                                estado,
                                session.userName
                            )
                        } else {
                            Log.e(TAG, "❌ Falha ao atualizar status para $estado")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao atualizar status para ${session.userName}: ${e.message}", e)
                    }
                }
                
                if (sessions.sessions.isEmpty()) {
                    Log.w(TAG, "⚠️ Nenhuma sessão ativa encontrada para atualizar status")
                } else {
                    // Sessões processadas
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao buscar sessões: ${e.message}", e)
            }
        }
    }
}
