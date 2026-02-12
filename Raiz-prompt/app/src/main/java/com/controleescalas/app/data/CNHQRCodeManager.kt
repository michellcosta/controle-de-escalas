package com.controleescalas.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cnhQRCodeDataStore: DataStore<Preferences> by preferencesDataStore(name = "cnh_qr_code")

/**
 * Gerenciador de QR Code da habilitação (CNH)
 * Persiste o texto do QR Code localmente usando DataStore
 */
class CNHQRCodeManager(private val context: Context) {
    
    companion object {
        private val QR_CODE_TEXT_KEY = stringPreferencesKey("cnh_qr_code_text")
        private val HAS_QR_CODE_KEY = booleanPreferencesKey("has_cnh_qr_code")
    }
    
    /**
     * Flow que indica se existe um QR Code salvo
     */
    val hasQRCodeFlow: Flow<Boolean> = context.cnhQRCodeDataStore.data.map { preferences ->
        preferences[HAS_QR_CODE_KEY] ?: false
    }
    
    /**
     * Flow do texto do QR Code
     */
    val qrCodeTextFlow: Flow<String?> = context.cnhQRCodeDataStore.data.map { preferences ->
        preferences[QR_CODE_TEXT_KEY]
    }
    
    /**
     * Verifica se existe um QR Code salvo
     */
    suspend fun hasQRCode(): Boolean {
        return hasQRCodeFlow.first()
    }
    
    /**
     * Obtém o texto do QR Code salvo
     */
    suspend fun getQRCodeText(): String? {
        return qrCodeTextFlow.first()
    }
    
    /**
     * Salva o texto do QR Code
     * @param qrCodeText Texto do QR Code a ser salvo
     * @return true se salvou com sucesso, false caso contrário
     */
    suspend fun saveQRCode(qrCodeText: String): Boolean {
        return try {
            if (qrCodeText.isBlank()) {
                println("⚠️ CNHQRCodeManager: Tentativa de salvar QR Code vazio")
                return false
            }
            
            context.cnhQRCodeDataStore.edit { preferences ->
                preferences[QR_CODE_TEXT_KEY] = qrCodeText
                preferences[HAS_QR_CODE_KEY] = true
            }
            
            println("✅ CNHQRCodeManager: QR Code da habilitação salvo com sucesso")
            true
        } catch (e: Exception) {
            println("❌ CNHQRCodeManager: Erro ao salvar QR Code: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Remove o QR Code salvo
     */
    suspend fun clearQRCode() {
        try {
            context.cnhQRCodeDataStore.edit { preferences ->
                preferences.remove(QR_CODE_TEXT_KEY)
                preferences.remove(HAS_QR_CODE_KEY)
            }
            
            println("✅ CNHQRCodeManager: QR Code da habilitação removido")
        } catch (e: Exception) {
            println("❌ CNHQRCodeManager: Erro ao remover QR Code: ${e.message}")
        }
    }
}

