
package com.controleescalas.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import androidx.work.*
import com.controleescalas.app.data.DailyResetWorker
import com.controleescalas.app.data.repositories.SuperAdminRepository
import com.controleescalas.app.utils.BrasilDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

        /**
         * B12 da auditoria: registro de versão iniciado no onCreate. O FCM
         * token aguarda este gate antes de gravar no Firestore.
         */
        lateinit var versaoRegistrada: kotlinx.coroutines.Deferred<Boolean>
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

        // GATE DE VERSÃO: registrar versionCode antes de qualquer escrita no Firestore
        // (as rules negam escrita para apps sem a claim de versão)
        registrarVersaoNoStartup()

        // ✅ NOVO: Garantir que FCM token seja salvo para sessões ativas
        inicializarFcmToken()
    }

    /**
     * Gate de versão: registra o versionCode no backend e força refresh do token.
     * Precisa acontecer antes das escritas do startup (ex.: token FCM).
     */
    private fun registrarVersaoNoStartup() {
        // B12 da auditoria: o resultado vira um Deferred compartilhado — o FCM
        // token AGUARDA este gate antes de escrever (escrita sem a claim "v"
        // é negada pelas rules e o push ficava perdido para a sessão).
        versaoRegistrada = CoroutineScope(Dispatchers.IO).async {
            com.controleescalas.app.data.VersaoGate.registrar()
        }
    }
    
    /**
     * Inicializar o Super Admin único do sistema
     */
    private fun inicializarSuperAdmin() {
        // Executar em background para não bloquear o onCreate
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A verificação lê todas as bases e é custosa em cota do Firestore.
                // Basta rodar uma vez por instalação; nas demais aberturas o app pula direto.
                val prefs = getSharedPreferences("cota_firestore", android.content.Context.MODE_PRIVATE)
                if (prefs.getBoolean("superadmin_verificado", false)) {
                    println("⏭️ MainApp: Super Admin já verificado nesta instalação — pulando verificação")
                    return@launch
                }

                val superAdminRepository = SuperAdminRepository()
                superAdminRepository.ensureSuperAdminExists()

                prefs.edit().putBoolean("superadmin_verificado", true).apply()
                println("✅ MainApp: Verificação do Super Admin concluída (não roda de novo nesta instalação)")
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
            
            val delayMillis = BrasilDate.millisAteProximaMeiaNoite()
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
                // B12: espera o registro de versão (claim "v") em vez de um
                // chute de 1s — com o Render frio o registro demora muito mais,
                // e a escrita do token era negada (push perdida para a sessão).
                // Em falha do registro segue em frente (a escrita pode ser
                // negada; a splash/login tentam registrar novamente).
                runCatching { versaoRegistrada.await() }

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
