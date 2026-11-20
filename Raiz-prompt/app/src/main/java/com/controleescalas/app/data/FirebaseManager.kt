package com.controleescalas.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

/**
 * FirebaseManager - Singleton para gerenciar instâncias do Firebase
 * 
 * Centraliza o acesso aos serviços Firebase:
 * - Firestore (banco de dados) com persistência offline habilitada
 * - Storage (arquivos/PDFs)
 * - Auth (autenticação)
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            try {
                // Habilitar persistência offline.
                // Achado 14 da auditoria: CACHE_SIZE_UNLIMITED fazia o cache do
                // aparelho do motorista crescer sem teto (armazenamento). 100MB
                // é o teto padrão recomendado pelo Firebase.
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(100L * 1024 * 1024)
                    .build()

                firestoreSettings = settings
                Log.d(TAG, "✅ Firestore offline persistence habilitado (cache 100MB)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao configurar Firestore offline persistence", e)
            }
        }
    }
    
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
}



