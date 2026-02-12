package com.controleescalas.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.controleescalas.app.data.models.MotoristaQRCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Extensão para criar DataStore
private val Context.qrCodeDataStore: DataStore<Preferences> by preferencesDataStore(name = "motorista_qr_code")

/**
 * Gerenciador de QR Code do motorista
 * Persiste o QR Code localmente usando DataStore
 */
class MotoristaQRCodeManager(private val context: Context) {
    
    companion object {
        private val QR_CODE_KEY = stringPreferencesKey("motorista_qr_code")
        private val json = Json { 
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    
    /**
     * Fluxo do QR Code salvo (objeto deserializado)
     */
    val qrCodeFlow: Flow<MotoristaQRCode?> = context.qrCodeDataStore.data.map { preferences ->
        val qrCodeJson = preferences[QR_CODE_KEY] ?: ""
        if (qrCodeJson.isBlank()) {
            null
        } else {
            try {
                json.decodeFromString<MotoristaQRCode>(qrCodeJson)
            } catch (e: Exception) {
                println("❌ MotoristaQRCodeManager: Erro ao decodificar QR Code: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Fluxo do QR Code salvo (JSON serializado - mais rápido para gerar QR Code)
     */
    val qrCodeJsonFlow: Flow<String?> = context.qrCodeDataStore.data.map { preferences ->
        val qrCodeJson = preferences[QR_CODE_KEY] ?: ""
        if (qrCodeJson.isBlank()) {
            null
        } else {
            qrCodeJson
        }
    }
    
    /**
     * Salvar QR Code
     */
    suspend fun saveQRCode(qrCode: MotoristaQRCode): Boolean {
        return try {
            val qrCodeJson = json.encodeToString(qrCode)
            context.qrCodeDataStore.edit { preferences ->
                preferences[QR_CODE_KEY] = qrCodeJson
            }
            println("✅ MotoristaQRCodeManager: QR Code salvo com sucesso")
            true
        } catch (e: Exception) {
            println("❌ MotoristaQRCodeManager: Erro ao salvar QR Code: ${e.message}")
            false
        }
    }
    
    /**
     * Obter QR Code salvo
     */
    suspend fun getQRCode(): MotoristaQRCode? {
        return try {
            val preferences = context.qrCodeDataStore.data.first()
            val qrCodeJson = preferences[QR_CODE_KEY] ?: ""
            if (qrCodeJson.isBlank()) {
                null
            } else {
                json.decodeFromString<MotoristaQRCode>(qrCodeJson)
            }
        } catch (e: Exception) {
            println("❌ MotoristaQRCodeManager: Erro ao obter QR Code: ${e.message}")
            null
        }
    }
    
    /**
     * Remover QR Code salvo
     */
    suspend fun clearQRCode(): Boolean {
        return try {
            context.qrCodeDataStore.edit { preferences ->
                preferences.remove(QR_CODE_KEY)
            }
            println("✅ MotoristaQRCodeManager: QR Code removido com sucesso")
            true
        } catch (e: Exception) {
            println("❌ MotoristaQRCodeManager: Erro ao remover QR Code: ${e.message}")
            false
        }
    }
}

