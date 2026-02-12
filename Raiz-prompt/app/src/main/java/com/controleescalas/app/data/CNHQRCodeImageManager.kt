package com.controleescalas.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private val Context.cnhQRCodeImageDataStore: DataStore<Preferences> by preferencesDataStore(name = "cnh_qr_code_image")

/**
 * Gerenciador de imagem do QR Code da habilitação (CNH)
 * Salva e carrega a imagem do QR Code no storage interno do app
 */
class CNHQRCodeImageManager(private val context: Context) {
    
    companion object {
        private val HAS_QR_CODE_IMAGE_KEY = booleanPreferencesKey("has_cnh_qr_code_image")
        private const val QR_CODE_IMAGE_FILENAME = "cnh_qr_code.jpg"
    }
    
    private val qrCodeImageFile: File
        get() = File(context.filesDir, QR_CODE_IMAGE_FILENAME)
    
    /**
     * Flow que indica se existe uma imagem do QR Code salva
     */
    val hasQRCodeImageFlow: Flow<Boolean> = context.cnhQRCodeImageDataStore.data.map { preferences ->
        preferences[HAS_QR_CODE_IMAGE_KEY] ?: false
    }
    
    /**
     * Verifica se existe uma imagem do QR Code salva
     */
    suspend fun hasQRCodeImage(): Boolean {
        return hasQRCodeImageFlow.first()
    }
    
    /**
     * Salva a imagem do QR Code a partir de uma URI
     * @param imageUri URI da imagem a ser salva
     * @return true se salvou com sucesso, false caso contrário
     */
    suspend fun saveQRCodeImageFromUri(imageUri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                println("❌ CNHQRCodeImageManager: Erro ao abrir input stream da URI")
                return false
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                println("❌ CNHQRCodeImageManager: Erro ao decodificar bitmap")
                return false
            }
            
            saveQRCodeImage(bitmap)
        } catch (e: Exception) {
            println("❌ CNHQRCodeImageManager: Erro ao salvar imagem do QR Code: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Salva a imagem do QR Code a partir de um Bitmap
     * @param bitmap Bitmap da imagem a ser salva
     * @return true se salvou com sucesso, false caso contrário
     */
    suspend fun saveQRCodeImage(bitmap: Bitmap): Boolean {
        return try {
            // Salvar bitmap no arquivo
            val outputStream = FileOutputStream(qrCodeImageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Atualizar flag no DataStore
            context.cnhQRCodeImageDataStore.edit { preferences ->
                preferences[HAS_QR_CODE_IMAGE_KEY] = true
            }
            
            println("✅ CNHQRCodeImageManager: Imagem do QR Code da habilitação salva com sucesso")
            true
        } catch (e: Exception) {
            println("❌ CNHQRCodeImageManager: Erro ao salvar imagem do QR Code: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Carrega a imagem do QR Code salva
     * @return Bitmap da imagem ou null se não existir
     */
    fun loadQRCodeImage(): Bitmap? {
        return try {
            if (!qrCodeImageFile.exists()) {
                println("⚠️ CNHQRCodeImageManager: Arquivo do QR Code não existe")
                return null
            }
            
            val bitmap = BitmapFactory.decodeFile(qrCodeImageFile.absolutePath)
            if (bitmap == null) {
                println("❌ CNHQRCodeImageManager: Erro ao decodificar arquivo do QR Code")
                return null
            }
            
            println("✅ CNHQRCodeImageManager: Imagem do QR Code carregada com sucesso")
            bitmap
        } catch (e: Exception) {
            println("❌ CNHQRCodeImageManager: Erro ao carregar imagem do QR Code: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Obtém a URI do arquivo do QR Code
     * @return URI do arquivo ou null se não existir
     */
    fun getQRCodeImageUri(): Uri? {
        return try {
            if (!qrCodeImageFile.exists()) {
                return null
            }
            Uri.fromFile(qrCodeImageFile)
        } catch (e: Exception) {
            println("❌ CNHQRCodeImageManager: Erro ao obter URI do QR Code: ${e.message}")
            null
        }
    }
    
    /**
     * Remove a imagem do QR Code salva
     */
    suspend fun clearQRCodeImage() {
        try {
            if (qrCodeImageFile.exists()) {
                qrCodeImageFile.delete()
            }
            
            context.cnhQRCodeImageDataStore.edit { preferences ->
                preferences.remove(HAS_QR_CODE_IMAGE_KEY)
            }
            
            println("✅ CNHQRCodeImageManager: Imagem do QR Code removida")
        } catch (e: Exception) {
            println("❌ CNHQRCodeImageManager: Erro ao remover imagem do QR Code: ${e.message}")
        }
    }
}

