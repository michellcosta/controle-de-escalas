
package com.controleescalas.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import androidx.work.*
import com.controleescalas.app.data.DailyResetWorker
import com.controleescalas.app.data.repositories.SuperAdminRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * MainApp
 *
 * Inicializa Firebase e mantém instância global para acesso ao contexto
 */
class MainApp : Application() {
    
    companion object {
        lateinit var instance: MainApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        
        // Criar canal de notificação logo no início (necessário para FCM com app fechado)
        com.controleescalas.app.data.NotificationService(this).createNotificationChannelAtStartup()
        
        // Agendar reset diário
        agendarResetDiario()
        
        // Inicializar Super Admin único
        inicializarSuperAdmin()
        
        // ✅ NOVO: Garantir que FCM token seja salvo para sessões ativas
        inicializarFcmToken()
    }
    
    /**
     * Inicializar o Super Admin único do sistema
     */
    private fun inicializarSuperAdmin() {
        // Executar em background para não bloquear o onCreate
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val superAdminRepository = SuperAdminRepository()
                superAdminRepository.ensureSuperAdminExists()
            } catch (e: Exception) {
                Log.e("MainApp", "❌ Erro ao inicializar super admin: ${e.message}", e)
                println("❌ MainApp: Erro ao inicializar super admin: ${e.message}")
            }
        }
    }
    
    /**
     * Agenda o reset diário para executar à meia-noite
     */
    private fun agendarResetDiario() {
        try {
            val workManager = WorkManager.getInstance(this)
            
            // Calcular tempo até a próxima meia-noite
            val calendar = Calendar.getInstance()
            val agora = Calendar.getInstance()
            
            // Definir para meia-noite de hoje
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // Se já passou da meia-noite hoje, agendar para meia-noite de amanhã
            if (calendar.timeInMillis <= agora.timeInMillis) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            val delayMillis = calendar.timeInMillis - agora.timeInMillis
            val delayHours = TimeUnit.MILLISECONDS.toHours(delayMillis)
            
            Log.d("MainApp", "🕛 Agendando reset diário para meia-noite (em $delayHours horas)")
            println("🕛 MainApp: Agendando reset diário para meia-noite (em $delayHours horas)")
            
            // Criar constraints (executar mesmo sem internet, mas precisa estar carregando ou conectado)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            // Criar work request inicial (executa uma vez após o delay)
            // O Worker se re-agendará automaticamente após cada execução
            val initialWork = OneTimeWorkRequestBuilder<DailyResetWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(DailyResetWorker.WORK_NAME)
                .build()
            
            // Enfileirar o trabalho inicial (substitui se já existir)
            workManager.enqueueUniqueWork(
                DailyResetWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                initialWork
            )
            
            Log.d("MainApp", "✅ Reset diário agendado com sucesso")
            println("✅ MainApp: Reset diário agendado com sucesso")
        } catch (e: Exception) {
            Log.e("MainApp", "❌ Erro ao agendar reset diário: ${e.message}", e)
            println("❌ MainApp: Erro ao agendar reset diário - ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Garantir que FCM token seja salvo para todas as sessões ativas
     * Executa quando o app inicia para manter tokens atualizados
     */
    private fun inicializarFcmToken() {
        // Executar em background para não bloquear o onCreate
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Aguardar um pouco para garantir que Firebase esteja inicializado
                kotlinx.coroutines.delay(1000)
                
                val sessionManager = com.controleescalas.app.data.SessionManager(instance)
                val sessions = sessionManager.getUserSessions()
                
                if (sessions.sessions.isNotEmpty()) {
                    Log.d("MainApp", "📱 Inicializando FCM tokens para ${sessions.sessions.size} sessão(ões) ativa(s)")
                    val notificationService = com.controleescalas.app.data.NotificationService(instance)
                    
                    sessions.sessions.forEach { session ->
                        try {
                            // Salvar FCM token e inscrever em tópico da base
                            notificationService.saveFcmTokenToFirestore(session.userId, session.baseId)
                            notificationService.subscribeToBaseTopic(session.baseId)
                            Log.d("MainApp", "✅ FCM token atualizado para ${session.userName} (${session.baseName})")
                        } catch (e: Exception) {
                            Log.e("MainApp", "⚠️ Erro ao atualizar FCM token para ${session.userName}: ${e.message}")
                        }
                    }
                } else {
                    Log.d("MainApp", "ℹ️ Nenhuma sessão ativa encontrada para atualizar FCM token")
                }
            } catch (e: Exception) {
                Log.e("MainApp", "❌ Erro ao inicializar FCM token: ${e.message}", e)
            }
        }
    }
}
