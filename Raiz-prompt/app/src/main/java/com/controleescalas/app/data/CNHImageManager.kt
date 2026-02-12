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

private val Context.cnhDataStore: DataStore<Preferences> by preferencesDataStore(name = "cnh_data")

/**
 * Gerenciador de imagem da CNH
 * Salva e carrega a imagem da CNH no storage interno do app
 */
class CNHImageManager(private val context: Context) {
    
    companion object {
        private val HAS_CNH_IMAGE_KEY = booleanPreferencesKey("has_cnh_image")
        private const val CNH_IMAGE_FILENAME = "cnh.jpg"
    }
    
    private val cnhImageFile: File
        get() = File(context.filesDir, CNH_IMAGE_FILENAME)
    
    /**
     * Flow que indica se existe uma imagem da CNH salva
     */
    val hasCnhImageFlow: Flow<Boolean> = context.cnhDataStore.data.map { preferences ->
        preferences[HAS_CNH_IMAGE_KEY] ?: false
    }
    
    /**
     * Verifica se existe uma imagem da CNH salva
     */
    suspend fun hasCnhImage(): Boolean {
        return hasCnhImageFlow.first()
    }
    
    /**
     * Salva a imagem da CNH a partir de uma URI
     * @param imageUri URI da imagem a ser salva
     * @return true se salvou com sucesso, false caso contrário
     */
    suspend fun saveCnhImageFromUri(imageUri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                println("❌ CNHImageManager: Erro ao abrir input stream da URI")
                return false
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                println("❌ CNHImageManager: Erro ao decodificar bitmap")
                return false
            }
            
            saveCnhImage(bitmap)
        } catch (e: Exception) {
            println("❌ CNHImageManager: Erro ao salvar imagem da CNH: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Salva a imagem da CNH a partir de um Bitmap
     * @param bitmap Bitmap da imagem a ser salva
     * @return true se salvou com sucesso, false caso contrário
     */
    suspend fun saveCnhImage(bitmap: Bitmap): Boolean {
        return try {
            // Salvar bitmap no arquivo
            val outputStream = FileOutputStream(cnhImageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Atualizar flag no DataStore
            context.cnhDataStore.edit { preferences ->
                preferences[HAS_CNH_IMAGE_KEY] = true
            }
            
            println("✅ CNHImageManager: Imagem da CNH salva com sucesso")
            true
        } catch (e: Exception) {
            println("❌ CNHImageManager: Erro ao salvar imagem da CNH: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Carrega a imagem da CNH salva
     * @return Bitmap da imagem ou null se não existir
     */
    fun loadCnhImage(): Bitmap? {
        return try {
            if (!cnhImageFile.exists()) {
                println("⚠️ CNHImageManager: Arquivo da CNH não existe")
                return null
            }
            
            val bitmap = BitmapFactory.decodeFile(cnhImageFile.absolutePath)
            if (bitmap == null) {
                println("❌ CNHImageManager: Erro ao decodificar arquivo da CNH")
                return null
            }
            
            println("✅ CNHImageManager: Imagem da CNH carregada com sucesso")
            bitmap
        } catch (e: Exception) {
            println("❌ CNHImageManager: Erro ao carregar imagem da CNH: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Obtém a URI do arquivo da CNH
     * @return URI do arquivo ou null se não existir
     */
    fun getCnhImageUri(): Uri? {
        return try {
            if (!cnhImageFile.exists()) {
                return null
            }
            Uri.fromFile(cnhImageFile)
        } catch (e: Exception) {
            println("❌ CNHImageManager: Erro ao obter URI da CNH: ${e.message}")
            null
        }
    }
    
    /**
     * Remove a imagem da CNH salva
     */
    suspend fun clearCnhImage() {
        try {
            if (cnhImageFile.exists()) {
                cnhImageFile.delete()
            }
            
            context.cnhDataStore.edit { preferences ->
                preferences.remove(HAS_CNH_IMAGE_KEY)
            }
            
            println("✅ CNHImageManager: Imagem da CNH removida")
        } catch (e: Exception) {
            println("❌ CNHImageManager: Erro ao remover imagem da CNH: ${e.message}")
        }
    }
}

