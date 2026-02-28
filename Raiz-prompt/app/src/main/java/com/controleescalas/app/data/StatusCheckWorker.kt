package com.controleescalas.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.controleescalas.app.data.repositories.ConfigRepository
import com.controleescalas.app.data.repositories.MotoristaRepository
import com.controleescalas.app.ui.screens.DriverStatusInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Worker para verificar mudanças de status periodicamente
 * Funciona mesmo quando o app está completamente fechado
 */
class StatusCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "StatusCheckWorker"
        const val KEY_MOTORISTA_ID = "motorista_id"
        const val KEY_BASE_ID = "base_id"
        const val KEY_LAST_STATUS = "last_status"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val motoristaId = inputData.getString(KEY_MOTORISTA_ID)
            val baseId = inputData.getString(KEY_BASE_ID)
            val lastStatus = inputData.getString(KEY_LAST_STATUS)
            
            Log.d(TAG, "🔍 StatusCheckWorker executando - MotoristaId: $motoristaId, BaseId: $baseId, Último Status: $lastStatus")
            println("🔍 StatusCheckWorker: Verificando status - MotoristaId=$motoristaId, Último Status=$lastStatus")
            
            if (motoristaId.isNullOrBlank() || baseId.isNullOrBlank()) {
                Log.e(TAG, "❌ MotoristaId ou BaseId não fornecidos")
                return@withContext Result.failure()
            }
            
            val motoristaRepository = MotoristaRepository()
            val statusAtual = motoristaRepository.getStatusMotorista(motoristaId, baseId)
            val novoStatus = statusAtual?.estado
            
            Log.d(TAG, "📊 Status atual obtido: $novoStatus")
            println("📊 StatusCheckWorker: Status atual = $novoStatus")
            
            // ✅ Verificar se status é CONCLUIDO - se for, não fazer nada e retornar sucesso
            // O WorkManager será cancelado pelo DriverViewModel, mas esta verificação garante
            // que não processamos nada desnecessariamente
            if (novoStatus == "CONCLUIDO") {
                Log.d(TAG, "✅ Status é CONCLUIDO - WorkManager deve ser cancelado. Retornando sucesso sem processar.")
                println("✅ StatusCheckWorker: Status CONCLUIDO detectado, não processando")
                return@withContext Result.success()
            }
            
            // Verificar localização e atualizar status se necessário (para motoristas que já estão dentro do raio)
            if (novoStatus == "A_CAMINHO" || novoStatus == "IR_ESTACIONAMENTO") {
                checkLocationAndUpdateStatus(motoristaId, baseId, novoStatus)
            }
            
            // Verificar se houve mudança de status
            // Nota: Notificações de escalação quando admin aperta "Notificar Todos" são enviadas
            // pelo DriverViewModel via listener do Firestore. Este worker apenas notifica mudanças reais de status.
            if (lastStatus != novoStatus && !novoStatus.isNullOrEmpty()) {
                Log.d(TAG, "🔄 Mudança de status detectada: '$lastStatus' -> '$novoStatus'")
                println("🔄 StatusCheckWorker: Mudança detectada: '$lastStatus' -> '$novoStatus'")
                
                val notificationManager = NotificationManager.getInstance(applicationContext)
                
                // Notificar apenas para estados importantes usando NotificationManager centralizado
                when (novoStatus) {
                    "IR_ESTACIONAMENTO" -> {
                        Log.d(TAG, "🅿️ Enviando notificação de estacionamento...")
                        println("🅿️ StatusCheckWorker: Enviando notificação de estacionamento")
                        notificationManager.sendMotoristaEstacionamentoNotification(
                            motoristaNome = "Motorista"
                        )
                    }
                    "CARREGANDO" -> {
                        val vaga = statusAtual?.vagaAtual ?: ""
                        val rota = statusAtual?.rotaAtual ?: ""
                        Log.d(TAG, "🚚 Enviando notificação de carregamento - Vaga: $vaga, Rota: $rota")
                        println("🚚 StatusCheckWorker: Enviando notificação de carregamento")
                        notificationManager.sendMotoristaChamadaNotification(
                            motoristaNome = "Motorista",
                            vaga = vaga,
                            rota = rota
                        )
                    }
                    "CONCLUIDO" -> {
                        // Este caso não deve ser alcançado devido à verificação acima, mas mantido por segurança
                        Log.d(TAG, "✅ Enviando notificação de conclusão...")
                        println("✅ StatusCheckWorker: Enviando notificação de conclusão")
                        notificationManager.sendConclusaoNotification(
                            mensagem = statusAtual?.mensagem?.ifEmpty { "Carregamento finalizado!" } ?: "Carregamento finalizado!"
                        )
                    }
                }
                
                Log.d(TAG, "✅ Notificação enviada com sucesso")
                println("✅ StatusCheckWorker: Notificação enviada")
            } else {
                Log.d(TAG, "ℹ️ Sem mudança de status - Último: '$lastStatus', Novo: '$novoStatus'")
                println("ℹ️ StatusCheckWorker: Sem mudança - Último='$lastStatus', Novo='$novoStatus'")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar status: ${e.message}", e)
            println("❌ StatusCheckWorker: Erro - ${e.message}")
            e.printStackTrace()
            Result.retry() // Tentar novamente em caso de erro
        }
    }
    
    /**
     * Verificar localização atual e atualizar status se motorista estiver dentro do raio do galpão
     */
    private suspend fun checkLocationAndUpdateStatus(motoristaId: String, baseId: String, currentStatus: String) {
        try {
            // Verificar permissões
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "⚠️ Permissões de localização não concedidas, pulando verificação")
                return
            }
            
            // Obter coordenadas do galpão
            val configRepository = ConfigRepository()
            val config = configRepository.getConfiguracaoBase(baseId)
            
            if (config == null || config.galpao.lat == 0.0 || config.galpao.lng == 0.0) {
                Log.d(TAG, "⚠️ Coordenadas do galpão não configuradas, pulando verificação")
                return
            }
            
            // Obter localização atual (lastLocation primeiro, getCurrentLocation como fallback)
            val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            var location = fusedLocationClient.lastLocation.await()
            if (location == null) {
                Log.d(TAG, "ℹ️ lastLocation null, tentando getCurrentLocation...")
                try {
                    location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ getCurrentLocation falhou: ${e.message}")
                }
            }
            if (location == null) {
                Log.d(TAG, "ℹ️ Nenhuma localização disponível, pulando verificação")
                return
            }
            
            // Calcular distância até o galpão
            val resultsGalpao = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                config.galpao.lat, config.galpao.lng,
                resultsGalpao
            )
            val distanciaGalpao = resultsGalpao[0].toDouble()
            val raioGalpao = config.galpao.raio.toDouble()
            
            val motoristaRepository = MotoristaRepository()
            var statusAtualizado = false
            
            // Verificar galpão primeiro (tem prioridade)
            if (config.galpao.lat != 0.0 && config.galpao.lng != 0.0) {
                val dentroDoGalpao = distanciaGalpao <= raioGalpao
                Log.d(TAG, "📍 Verificação de localização - Galpão: distância=${distanciaGalpao.toInt()}m, raio=${raioGalpao.toInt()}m, dentro=${dentroDoGalpao}")
                
                // Se está dentro do raio do galpão e status é A_CAMINHO, atualizar para CHEGUEI
                if (dentroDoGalpao && currentStatus == "A_CAMINHO") {
                    Log.d(TAG, "✅ Motorista está dentro do raio do galpão (${distanciaGalpao.toInt()}m), atualizando status para CHEGUEI")
                    val success = motoristaRepository.updateStatusMotorista(
                        motoristaId = motoristaId,
                        baseId = baseId,
                        estado = "CHEGUEI",
                        mensagem = "Chegou ao galpão (automático - background)"
                    )
                    
                    if (success) {
                        Log.d(TAG, "✅ Status atualizado para CHEGUEI em background")
                        statusAtualizado = true
                        NotifyStatusChangeWorker.enqueue(applicationContext, baseId, motoristaId, "CHEGUEI")
                    } else {
                        Log.e(TAG, "❌ Erro ao atualizar status para CHEGUEI em background")
                    }
                }
            }
            
            // Verificar estacionamento apenas se não estiver dentro do galpão
            // (o galpão tem prioridade sobre o estacionamento)
            if (!statusAtualizado && config.estacionamento.lat != 0.0 && config.estacionamento.lng != 0.0) {
                val resultsEstacionamento = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude,
                    config.estacionamento.lat, config.estacionamento.lng,
                    resultsEstacionamento
                )
                val distanciaEstacionamento = resultsEstacionamento[0].toDouble()
                val raioEstacionamento = config.estacionamento.raio.toDouble()
                
                val dentroDoEstacionamento = distanciaEstacionamento <= raioEstacionamento
                Log.d(TAG, "📍 Verificação de localização - Estacionamento: distância=${distanciaEstacionamento.toInt()}m, raio=${raioEstacionamento.toInt()}m, dentro=${dentroDoEstacionamento}")
                
                // Se está dentro do raio do estacionamento e status é IR_ESTACIONAMENTO ou A_CAMINHO, atualizar para ESTACIONAMENTO
                if (dentroDoEstacionamento && (currentStatus == "IR_ESTACIONAMENTO" || currentStatus == "A_CAMINHO")) {
                    Log.d(TAG, "✅ Motorista está dentro do raio do estacionamento (${distanciaEstacionamento.toInt()}m), atualizando status para ESTACIONAMENTO")
                    val success = motoristaRepository.updateStatusMotorista(
                        motoristaId = motoristaId,
                        baseId = baseId,
                        estado = "ESTACIONAMENTO",
                        mensagem = "Chegou ao estacionamento (automático - background)"
                    )
                    
                    if (success) {
                        Log.d(TAG, "✅ Status atualizado para ESTACIONAMENTO em background")
                        NotifyStatusChangeWorker.enqueue(applicationContext, baseId, motoristaId, "ESTACIONAMENTO")
                    } else {
                        Log.e(TAG, "❌ Erro ao atualizar status para ESTACIONAMENTO em background")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar localização: ${e.message}", e)
        }
    }
}
