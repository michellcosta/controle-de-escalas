package com.controleescalas.app.data

import com.controleescalas.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Gate de versão: registra o versionCode do app no backend, que grava a
 * custom claim "v" no usuário autenticado. As security rules do Firestore
 * negam ESCRITA para quem não tem claim >= 12 — aparelhos com app antigo
 * ficam somente leitura (veem o aviso de atualização) até atualizarem.
 *
 * Deve ser chamado no startup do app e após cada login, ANTES de qualquer
 * escrita no Firestore.
 */
object VersaoGate {

    /**
     * Registra a versão e força refresh do ID token para embutir a claim.
     * @return true se registrado e token atualizado com sucesso.
     */
    suspend fun registrar(): Boolean {
        return try {
            val auth = FirebaseAuth.getInstance()
            var user = auth.currentUser
            if (user == null) {
                // Garantir autenticação (mesmo fluxo dos repositories)
                auth.signInAnonymously().await()
                user = auth.currentUser
            }
            val uid = user?.uid ?: return false

            // Fast path: claim já registrada e suficiente (persiste no servidor)
            val tokenResult = user.getIdToken(false).await()
            val vAtual = (tokenResult?.claims?.get("v") as? Number)?.toInt() ?: 0
            if (vAtual >= 12) return true

            val api = NotificationApiService()
            val idToken = tokenResult?.token ?: return false
            val resultado = api.registrarVersao(idToken, BuildConfig.VERSION_CODE)

            if (resultado.ok) {
                // A claim só entra no token após refresh — obrigatório antes de escrever
                user.getIdToken(true).await()
                println("✅ VersaoGate: versão ${BuildConfig.VERSION_CODE} registrada para uid $uid")
                true
            } else {
                println("⚠️ VersaoGate: backend não confirmou o registro da versão ${BuildConfig.VERSION_CODE}")
                false
            }
        } catch (e: Exception) {
            println("⚠️ VersaoGate: falha ao registrar versão (writes podem ser negados até conseguir): ${e.message}")
            false
        }
    }
}
